
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

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.ClientProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.google.common.collect.Maps;
import com.hivemq.extension.sdk.api.ExtensionMain;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.intializer.InitializerRegistry;
import com.hivemq.extensions.tdengine.configuration.TDengineConfiguration;

/**
 * This is the main class of the extension,
 * which is instantiated either during the HiveMQ start up process (if extension is enabled)
 * or when HiveMQ is already started by enabling the extension.
 *
 * @author Kemp
 * @since 1.0.0
 */
public class TDengineExtensionMain implements ExtensionMain {

    private static final @NotNull Logger log = LoggerFactory.getLogger(TDengineExtensionMain.class);
    private DruidDataSource datasource = null;
   
    @Override
    public void extensionStart(final @NotNull ExtensionStartInput extensionStartInput, final @NotNull ExtensionStartOutput extensionStartOutput) {

        try {
            final File extensionHomeFolder = extensionStartInput.getExtensionInformation().getExtensionHomeFolder();
            final TDengineConfiguration configuration = new TDengineConfiguration(extensionHomeFolder);

            if (!configuration.readPropertiesFromFile()) {
                extensionStartOutput.preventExtensionStartup("Could not read taosdata properties");
                return;
            }

            if (!configuration.validateConfiguration()) {
                extensionStartOutput.preventExtensionStartup("At least one mandatory property not set");
                return;
            }

            if (StringUtils.equalsIgnoreCase(configuration.getMode(), "jdbc")) { 
            	datasource = setupTaosDataSource(configuration);
            } else {
            	setupTaosDataRESTful(configuration);
            }
            
            addPublishModifier(configuration, datasource);
            
            
            final ExtensionInformation extensionInformation = extensionStartInput.getExtensionInformation();
            log.info("Started " + extensionInformation.getName() + ":" + extensionInformation.getVersion());

        } catch (Exception e) {
            log.error("Exception thrown at extension start: ", e);
        }

    }

    /**
     * Initializing RESTFul connector by creating database and table if they are not exists.
     * @param configuration
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    private void setupTaosDataRESTful(TDengineConfiguration configuration) throws ClientProtocolException, IOException {
    	final String Auth = "Basic " + Base64.getEncoder().encodeToString(configuration.getHttpToken().getBytes());
     	Map<String, Object> headers = Maps.newHashMap();
     	headers.put("Authorization", Auth);
     	if (StringUtils.isNotBlank(configuration.getCreateDatabaseSQL())) {
     		HttpClientUtil.httpPostRequest(configuration.getHttpUrl(), headers, configuration.getCreateDatabaseSQL());
     	}
     	if (StringUtils.isNotBlank(configuration.getCreateTableSQL())) {
     		HttpClientUtil.httpPostRequest(configuration.getHttpUrl(), headers, configuration.getCreateTableSQL());
     	}
   	
	}

    /**
     * Initializing JDBC Datasource and creating database and table if they are not exists.
     * @param configuration
     * @return
     * @throws Exception
     */
	private DruidDataSource setupTaosDataSource(@NotNull final TDengineConfiguration configuration) throws Exception {
    	DruidDataSource ds = (DruidDataSource) DruidDataSourceFactory.createDataSource(configuration.getJDBCProperties());
        Connection  connection = ds.getConnection(); // get connection
        Statement statement = null;
        try {
        	statement = connection.createStatement(); // get statement
         	if (StringUtils.isNotBlank(configuration.getCreateDatabaseSQL())) {
         		statement.execute(configuration.getCreateDatabaseSQL());
         	}
         	
         	if (StringUtils.isNotBlank(configuration.getCreateTableSQL())) {
         		statement.execute(configuration.getCreateTableSQL());
         	}
        	
        } finally {
            if (statement != null) {
                statement.close();
            }
        	connection.close();// put back to conneciton pool
        }
        return ds;
	}

	@Override
    public void extensionStop(final @NotNull ExtensionStopInput extensionStopInput, final @NotNull ExtensionStopOutput extensionStopOutput) {

        final ExtensionInformation extensionInformation = extensionStopInput.getExtensionInformation();
        log.info("Stopped " + extensionInformation.getName() + ":" + extensionInformation.getVersion());
        if (datasource != null) {
            datasource.close();
        } else {
            HttpClientUtil.closeConnectionManager();
        }
    }

    private void addPublishModifier(@NotNull final TDengineConfiguration configuration, @NotNull final DataSource datasource) {
        final InitializerRegistry initializerRegistry = Services.initializerRegistry();

        final TDenginePublishInterceptor taosdataInterceptor = new TDenginePublishInterceptor(configuration, datasource);

        initializerRegistry.setClientInitializer((initializerInput, clientContext) -> clientContext.addPublishInboundInterceptor(taosdataInterceptor));
    }

}
