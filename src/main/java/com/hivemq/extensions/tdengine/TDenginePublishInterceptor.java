
/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hivemq.extensions.tdengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.extension.sdk.api.async.Async;
import com.hivemq.extension.sdk.api.async.TimeoutFallback;
import com.hivemq.extension.sdk.api.interceptor.publish.PublishInboundInterceptor;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundInput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import com.hivemq.extension.sdk.api.packets.publish.ModifiablePublishPacket;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extensions.tdengine.configuration.TDengineConfiguration;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a {@link PublishInboundInterceptor},
 *
 * @author Kemp
 * @since 1.0.0
 */
public class TDenginePublishInterceptor implements PublishInboundInterceptor {
	
    private static final @NotNull Logger log = LoggerFactory.getLogger(TDenginePublishInterceptor.class);
    private final TDengineConfiguration configuration;
    private final DataSource datasource;
    
    private final Map<String, Object> headers = Maps.newHashMap();

    private final boolean jdbcMode;
    private final String sqlTemplate;
    private final boolean jsonEnabled;
    private final String httpURL;
    
    public TDenginePublishInterceptor(@NotNull final TDengineConfiguration configuration, final DataSource datasource) {
		this.configuration = configuration;
		this.datasource = datasource;
        this.sqlTemplate = configuration.getInsertTableSQL().toLowerCase();
        this.httpURL = configuration.getHttpUrl();
        
        if (StringUtils.equalsIgnoreCase(configuration.getMode(), "jdbc")) { 
        	this.jdbcMode = true;
        } else {
        	this.jdbcMode = false;
        }
        
        if (StringUtils.equalsIgnoreCase(configuration.getMqttCoder(), "base64")) { 
        	this.jsonEnabled = false;
        } else {
        	this.jsonEnabled = true;
        }
        
		if (StringUtils.equalsIgnoreCase(configuration.getMode(), "http")) { 
			final String Auth = "Basic " + Base64.getEncoder().encodeToString(configuration.getHttpToken().getBytes());
			headers.put("Authorization", Auth);
		}
	}

	@Override
    public void onInboundPublish(final @NotNull PublishInboundInput publishInboundInput, final @NotNull PublishInboundOutput publishInboundOutput) {
        final ModifiablePublishPacket publishPacket = publishInboundOutput.getPublishPacket();
        
        final String topic = publishPacket.getTopic();
        if (!StringUtils.equals(topic, configuration.getMqtttopic())) {
        	return;
        }
        final Optional<ByteBuffer> payload = publishPacket.getPayload();
        if (!payload.isPresent()) {
            return;
        }
        String sql;
        if (jsonEnabled) {
	        final String payloadAsString = getStringFromByteBuffer(payload.orElse(null));
	        if (payloadAsString == null || payloadAsString.length() == 0) {
	            return;
	        }
	
	        sql = getJsonSQL(topic, payloadAsString.replace("'", "\\'"));
	        if (StringUtils.isBlank(sql)) {
	            return;
	        }
        } else {
        	final byte[] buf = getBytesFromByteBuffer(payload.get());
	        if (buf == null) {
	            return;
	        }
	        
        	sql = getBase64SQL(topic, buf);
	        if (StringUtils.isBlank(sql)) {
	            return;
	        }
        }
        
        final Async<PublishInboundOutput> asyncOutput = publishInboundOutput.async(Duration.ofSeconds(10), TimeoutFallback.FAILURE);
        final CompletableFuture<?> taskFuture = Services.extensionExecutorService().submit(() -> {
            try {
                if (jdbcMode) { 
                    Connection  connection = datasource.getConnection(); 
                    Statement statement = null;
                    try {
                        statement = connection.createStatement(); 
                        statement.executeUpdate(sql);
                    } finally {
                        if (statement != null) {
                            try {
                                statement.close();
                            } catch (SQLException e) {
                                log.error("failed to close statement", e);
                            }
                        }
                        if (connection != null) {
                            try {
                                connection.close();
                            } catch (SQLException e) {
                                log.error("failed to close connection", e);
                            }
                        }
                    }
                } else {
                    if (headers != null && headers.size() > 0) {
                        HttpClientUtil.httpPostRequest(httpURL, headers, sql);
                    }
                }
            } catch (Exception e) {
                log.error("failed in onInboundPublish " + sql, e);
            }
        });

        taskFuture.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                log.error("failed in onInboundPublish " + sql, throwable);
            } 
            asyncOutput.resume();
        });		
    }
    
	/**
	 * replace the placeholder with the actual field.
	 * @param topic
	 * @param buf
	 * @return the sql string
	 */
    private String getBase64SQL(String topic, byte[] buf) {
		String payload = Base64.getEncoder().encodeToString(buf);
    	String sql = StringUtils.replace(sqlTemplate, "${topic}", topic);
		sql = StringUtils.replace(sql, "${payload}", payload);
		return sql;
    }
    
	/**
	 * replace the placeholder with the actual field.
	 * @param topic
	 * @param payload
	 * @return the sql string
	 */
    private String getJsonSQL(String topic, String payload) {
    	String sql = sqlTemplate;

        Map<String, String> map = getKVMap(payload);
        if (map ==null || map.size() ==0) {
        	return null;
        }
        
        StringBuilder sb = new StringBuilder();
        Iterator<String> iter = map.keySet().iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            sb.append("${payload.").append(key).append("}");
            sql = StringUtils.replace(sql, sb.toString(), map.get(key));
            sb.setLength(0);
        }
        
        if (log.isDebugEnabled()) {
        	log.debug("sql={}", sql);
        }
        
		return sql;
	}

    /**
     * convert json payload to a hash map.
     * @param json
     * @return the has map contains the fields.
     */
	private Map<String, String> getKVMap(String json) {
    	Map<String, String> map = new HashMap<String, String>();
		ObjectMapper mapper = new ObjectMapper();
		
		try{
			map = mapper.readValue(json, new TypeReference<HashMap<String,String>>(){});
		}catch(Exception e){
            log.error("failed in getKVMap " + json, e);
		}
		return map;
	}

	@Nullable
    private static String getStringFromByteBuffer(final @Nullable ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        final byte[] bytes = new byte[buffer.remaining()];
        for (int i = 0; i < buffer.remaining(); i++) {
            bytes[i] = buffer.get(i);
        }
        return new String(bytes, Charset.defaultCharset()).trim();
    }
	
	@Nullable
    private static byte[] getBytesFromByteBuffer(final @Nullable ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        final byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        return bytes;
    }
}