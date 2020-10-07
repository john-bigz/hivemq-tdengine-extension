
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

import com.google.common.collect.Maps;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extensions.tdengine.HttpClientUtil;
import com.hivemq.extensions.tdengine.TDenginePublishInterceptor;
import com.hivemq.testcontainer.core.MavenHiveMQExtensionSupplier;
import com.hivemq.testcontainer.junit5.HiveMQTestContainerExtension;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.ClientProtocolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This tests the functionality of the {@link TDenginePublishInterceptor}.
 * It uses the HiveMQ Testcontainer to automatically package and deploy this extension inside a HiveMQ docker container.
 * It intercepts the published payload and saved it into TDengine, 
 * then it gets the record back from TDengine via RESTful interface and checks it with the original timestamp. 
 * 
 * @author Kemp
 * @since 1.0.0
 */
class TDengineInterceptorIT {

    private static final String HTTP_TOKEN = "root:taosdata";

	private static final String HTTP_URL = "http://127.0.0.1:%d/rest/sql/";

	private static final String PAYLOAD_TEMPLATE = "{\"ts\": %d, \"temperature\": 32.1, \"voltage\": 321, \"name\": \"d02\", \"devid\": 2}";

	private static final String SQL_TEMPLATE = "select * from hivemqdb.sensor_data where ts=%d";
    
	@RegisterExtension
    public final @NotNull HiveMQTestContainerExtension extension =
            new HiveMQTestContainerExtension("taosdata-hivemq-extension", "latest")
                    .withExtension(MavenHiveMQExtensionSupplier.direct().get()).withExposedPorts(6041);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void test_payload_modified() throws InterruptedException, ClientProtocolException, IOException {
        final Mqtt5BlockingClient client = Mqtt5Client.builder()
                .identifier("taosdata-extension")
                .serverPort(extension.getMqttPort())
                .buildBlocking();
        client.connect();
        long timestamp = System.currentTimeMillis();
        final String payload = String.format(PAYLOAD_TEMPLATE, timestamp);
        		
        final Mqtt5BlockingClient.Mqtt5Publishes publishes = client.publishes(MqttGlobalPublishFilter.ALL);
        client.subscribeWith().topicFilter("application/sensor_data").send();

        client.publishWith().topic("application/sensor_data").payload(payload.getBytes(StandardCharsets.UTF_8)).send();

        Integer mappedPort = extension.getMappedPort(6041);
        
        final Mqtt5Publish receive = publishes.receive();
        assertTrue(receive.getPayload().isPresent());

        // By sending a RESTful request, we got back the record which we just saved to TDengine.
        final String httpURL = String.format(HTTP_URL, mappedPort);
        final String sql = String.format(SQL_TEMPLATE, timestamp);
        final Map<String, Object> headers = Maps.newHashMap();
		final String auth = "Basic " + Base64.getEncoder().encodeToString(HTTP_TOKEN.getBytes());
		headers.put("Authorization", auth);
		
        final String result = HttpClientUtil.httpPostRequest(httpURL, headers, sql);
        
        // Checking if the record contains the timestamp.
		String timestampString = (new java.text.SimpleDateFormat("mm:ss.SSS")).format(new Date(timestamp));
		assertTrue(StringUtils.contains(result, timestampString));
    }
}