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
package com.hivemq.extensions.tdengine.configuration;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Reads a property file containing taosdata properties
 * and provides some utility methods for working with {@link Properties}.
 *
 * @author Christoph Schäbel
 * @author Michael Walter
 * @author Kemp
 */
public class TDengineConfiguration extends PropertiesReader {

    private static final Logger log = LoggerFactory.getLogger(TDengineConfiguration.class);

    private static Properties jdbcProperties;
    
    private static final String MODE = "mode";
    private static final String MODE_DEFAULT = "jdbc";

    private static final String MQTT_TOPIC = "mqtt_topic";
    private static final String MQTT_TOPIC_DEFAULT = "application/sensor_data";
    private static final String MQTT_CODER = "msg_coder";
    private static final String MQTT_CODER_DEFAULT = "json";

    private static final String SQL_CREATE_DATABASE = "sql.create_database";
    private static final String SQL_CREATE_DATABASE_DEFAULT = "create database if not exists hivemqdb;";
    private static final String SQL_CREATE_TABLE = "sql.create_table";
    private static final String SQL_CREATE_TABLE_DEFAULT = "create table if not exists testdb.sensor_data (ts timestamp, temperature float, voltage int) TAGS (name binary(32), groupid int);";
    private static final String SQL_INSERT_TABLE = "sql.insert_table";
    private static final String SQL_INSERT_TABLE_DEFAULT = "insert into hivemqdb.sensor_data_${payload.groupid} using hivemqdb.sensor_data (now, ${payload.temperature}, ${payload.voltage}) tags('${payload.name}', ${payload.groupid});";

    private static final String HTTP_URL = "http.url";
    private static final String HTTP_URL_DEFAULT = "127.0.0.1:6020/rest/sql";
    
    private static final String HTTP_TOKEN = "http.token";
    private static final String HTTP_TOKEN_DEFAULT = "root:taosdata";

    
    private static final String JDBC_DRIVER_CLASS = "jdbc.driverClass";
    private static final String JDBC_DRIVER_CLASS_DEFAULT = "com.taosdata.jdbc.TSDBDriver";
    private static final String JDBC_URL = "jdbc.url";
    private static final String JDBC_URL_DEFAULT = "jdbc:TAOS://127.0.0.1:6030/log";
    private static final String JDBC_USERNAME = "jdbc.username";
    private static final String JDBC_USERNAME_DEFAULT = "root";
    private static final String JDBC_PASSWORD = "jdbc.password";
    private static final String JDBC_PASSWORD_DEFAULT = "taosdata";
    private static final String JDBC_POOL_INIT = "jdbc.pool.init";
    private static final int    JDBC_POOL_INIT_DEFAULT = 1;
    private static final String JDBC_POOL_MIN_IDLE = "jdbc.pool.minIdle";
    private static final int    JDBC_POOL_MIN_IDLE_DEFAULT = 3;
    private static final String JDBC_POOL_MAX_ACTIVE = "jdbc.pool.maxActive";
    private static final int    JDBC_POOL_MAX_ACTIVE_DEFAULT = 3;
    private static final String JDBC_TEST_SQL = "jdbc.testSql";
    private static final String JDBC_TEST_SQL_DEFAULT = "select server_status();";
    

    
    public TDengineConfiguration(@NotNull final File configFilePath) {
        super(configFilePath);
    }

    
    @NotNull
    public Properties getJDBCProperties() {
        return jdbcProperties;
    }
    
    /**
     * Check if mandatory properties exist and are valid. Mandatory properties are port and host.
     *
     * @return <b>true</b> if all mandatory properties exist, else <b>false</b>.
     */
    public boolean validateConfiguration() {
        int countError = 0;

        countError += checkMandatoryProperty(MODE);
        countError += checkMandatoryProperty(MQTT_TOPIC);
        countError += checkMandatoryProperty(SQL_CREATE_DATABASE);
        countError += checkMandatoryProperty(SQL_CREATE_TABLE);
        countError += checkMandatoryProperty(SQL_INSERT_TABLE);
        countError += checkMandatoryProperty(MQTT_CODER);
        
        if (countError != 0){
            return false;
        }

        final String mode = getProperty(MODE);
        if (!StringUtils.equalsAnyIgnoreCase(mode, "jdbc", "http")) {
        	log.error("invalid mode property {}!", mode);
        	return false;
        }
        
        if (StringUtils.equalsIgnoreCase(mode, "http")) {
        	
            countError += checkMandatoryProperty(HTTP_TOKEN);
            countError += checkMandatoryProperty(HTTP_URL);
        
        
        } else if (StringUtils.equalsIgnoreCase(mode, "jdbc")) {
        	
            countError += checkMandatoryProperty(JDBC_DRIVER_CLASS);
            countError += checkMandatoryProperty(JDBC_URL);
       	
            // check for valid poolInit value
            final String poolInit = getProperty(JDBC_POOL_INIT);
            try {
                final int intPoolInit = Integer.parseInt(poolInit);

                if (intPoolInit < 0 || intPoolInit > 100) {
                    log.error("Value for mandatory taosdata property {} is not in valid range.", JDBC_POOL_INIT);
                    countError++;
                }

            } catch (NumberFormatException e) {
                log.error("Value for mandatory taosdata property {} is not a number.", JDBC_POOL_INIT);
                countError++;
            }

            // check for valid poolMinIdle value
            final String poolminIdle = getProperty(JDBC_POOL_MIN_IDLE);
            try {
                final int intPoolminIdle = Integer.parseInt(poolminIdle);

                if (intPoolminIdle < 0 || intPoolminIdle > 100) {
                    log.error("Value for mandatory taosdata property {} is not in valid range.", JDBC_POOL_MIN_IDLE);
                    countError++;
                }

            } catch (NumberFormatException e) {
                log.error("Value for mandatory taosdata property {} is not a number.", JDBC_POOL_MIN_IDLE);
                countError++;
            }
            
            // check for valid poolMinIdle value
            final String poolmaxActive = getProperty(JDBC_POOL_MAX_ACTIVE);
            try {
                final int intPoolmaxActive = Integer.parseInt(poolmaxActive);

                if (intPoolmaxActive < 0 || intPoolmaxActive > 100) {
                    log.error("Value for mandatory taosdata property {} is not in valid range.", JDBC_POOL_MAX_ACTIVE);
                    countError++;
                }

            } catch (NumberFormatException e) {
                log.error("Value for mandatory taosdata property {} is not a number.", JDBC_POOL_MAX_ACTIVE);
                countError++;
            }            
            
            setupJDBCProperties();
        }
        
        if (countError != 0){
            return false;
        }


        
        return countError == 0;
    }

    private void setupJDBCProperties() {
        jdbcProperties = new Properties();
        jdbcProperties.put(DruidDataSourceFactory.PROP_DRIVERCLASSNAME, getDriverClass());
        jdbcProperties.put(DruidDataSourceFactory.PROP_URL, getUrl());
        jdbcProperties.put(DruidDataSourceFactory.PROP_USERNAME, getUsername());
        jdbcProperties.put(DruidDataSourceFactory.PROP_PASSWORD, getPassword());

        jdbcProperties.put(DruidDataSourceFactory.PROP_MAXACTIVE, getPoolMaxActive()+""); //maximum number of connection in the pool
        jdbcProperties.put(DruidDataSourceFactory.PROP_INITIALSIZE, getPoolInit()+"");//initial number of connection
        jdbcProperties.put(DruidDataSourceFactory.PROP_MAXWAIT, "10000");//maximum wait milliseconds for get connection from pool
        jdbcProperties.put(DruidDataSourceFactory.PROP_MINIDLE, getPoolMinIdle()+"");//minimum number of connection in the pool

        jdbcProperties.put(DruidDataSourceFactory.PROP_TIMEBETWEENEVICTIONRUNSMILLIS, "3000");// the interval milliseconds to test connection

        jdbcProperties.put(DruidDataSourceFactory.PROP_MINEVICTABLEIDLETIMEMILLIS, "60000");//the minimum milliseconds to keep idle
        jdbcProperties.put("maxEvictableIdleTimeMillis", "90000");//the maximum milliseconds to keep idle

        jdbcProperties.put(DruidDataSourceFactory.PROP_VALIDATIONQUERY, getTestSQL()); //validation query
        jdbcProperties.put(DruidDataSourceFactory.PROP_TESTWHILEIDLE, "true"); // test connection while idle
        jdbcProperties.put(DruidDataSourceFactory.PROP_TESTONBORROW, "false"); // don't need while testWhileIdle is true
        jdbcProperties.put(DruidDataSourceFactory.PROP_TESTONRETURN, "false"); // don't need while testWhileIdle is true
	}


	/**
     * Check if mandatory property exists.
     *
     * @param property Property to check.
     * @return 0 if property exists, else 1.
     */
    private int checkMandatoryProperty(@NotNull final String property) {
        checkNotNull(property, "Mandatory property must not be null");

        final String value = getProperty(property);

        if (value == null) {
            log.error("Mandatory property {} is not set.", property);
            return 1;
        }
        return 0;
    }

    /**
     * Fetch property with given <b>key</b>. If the fetched {@link String} is <b>null</b> the <b>defaultValue</b> will be returned.
     *
     * @param key          Key of the property.
     * @param defaultValue Default value as fallback, if property has no value.
     * @return the actual value of the property if it is set, else the <b>defaultValue</b>.
     */
    private String validateStringProperty(@NotNull final String key, @NotNull final String defaultValue) {
        checkNotNull(key, "Key to fetch property must not be null");
        checkNotNull(defaultValue, "Default value for property must not be null");

        final String value = getProperty(key);

        if (value == null) {

            if (!defaultValue.isEmpty()) {
                log.warn("No '{}' configured for InfluxDb, using default: {}", key, defaultValue);
            }
            return defaultValue;
        }

        return value;
    }

    /**
     * Fetch property with given <b>key</b>.
     * If the fetched {@link String} value is not <b>null</b> convert the value to an int and check validation constraints if given flags are <b>false</b> before returning the value.
     *
     * @param key             Key of the property
     * @param defaultValue    Default value as fallback, if property has no value
     * @param zeroAllowed     use <b>true</b> if property can be zero
     * @param negativeAllowed use <b>true</b> is property can be negative int
     * @return the actual value of the property if it is set and valid, else the <b>defaultValue</b>
     */
    private int validateIntProperty(@NotNull final String key, final int defaultValue, final boolean zeroAllowed, final boolean negativeAllowed) {
        checkNotNull(key, "Key to fetch property must not be null");

        final String value = properties.getProperty(key);

        if (value == null) {
            log.warn("No '{}' configured for InfluxDb, using default: {}", key, defaultValue);
            return defaultValue;
        }

        int valueAsInt;

        try {
            valueAsInt = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Value for taosdata property '{}' is not a number, original value {}. Using default: {}", key, value, defaultValue);
            return defaultValue;
        }

        if (!zeroAllowed && valueAsInt == 0) {
            log.warn("Value for taosdata property '{}' can't be zero. Using default: {}", key, defaultValue);
            return defaultValue;
        }

        if (!negativeAllowed && valueAsInt < 0) {
            log.warn("Value for taosdata property '{}' can't be negative. Using default: {}", key, defaultValue);
            return defaultValue;
        }

        return valueAsInt;
    }
    
       
    



    @NotNull
    public String getMode() {
        return validateStringProperty(MODE, MODE_DEFAULT);
    }

    @NotNull
    public String getMqtttopic() {
        return validateStringProperty(MQTT_TOPIC, MQTT_TOPIC_DEFAULT);
    }

    @NotNull
    public String getMqttCoder() {
        return validateStringProperty(MQTT_CODER, MQTT_CODER_DEFAULT);
    }
    
    @Override
    public String getFilename() {
        return "taosdata.properties";
    }
    
    @NotNull
    public String getHttpUrl() {
        return validateStringProperty(HTTP_URL, HTTP_URL_DEFAULT);
    }
    
    @NotNull
    public String getHttpToken() {
        return validateStringProperty(HTTP_TOKEN, HTTP_TOKEN_DEFAULT);
    }
    
    @NotNull
    public String getDriverClass() {
        return validateStringProperty(JDBC_DRIVER_CLASS, JDBC_DRIVER_CLASS_DEFAULT);
    }
    
    @NotNull
    public String getUrl() {
        return validateStringProperty(JDBC_URL, JDBC_URL_DEFAULT);
    }
       
    @NotNull
    public String getUsername() {
        return validateStringProperty(JDBC_USERNAME, JDBC_USERNAME_DEFAULT);
    }
    
    @NotNull
    public String getPassword() {
        return validateStringProperty(JDBC_PASSWORD, JDBC_PASSWORD_DEFAULT);
    }
    
    @NotNull
    public int getPoolInit() {
        return validateIntProperty(JDBC_POOL_INIT, JDBC_POOL_INIT_DEFAULT, false, false);
    }
    
    @NotNull
    public int getPoolMinIdle() {
        return validateIntProperty(JDBC_POOL_MIN_IDLE, JDBC_POOL_MIN_IDLE_DEFAULT, false, false);
    }

    @NotNull
    public int getPoolMaxActive() {
        return validateIntProperty(JDBC_POOL_MAX_ACTIVE, JDBC_POOL_MAX_ACTIVE_DEFAULT, false, false);
    }
    
    @NotNull
    public String getTestSQL() {
        return validateStringProperty(JDBC_TEST_SQL, JDBC_TEST_SQL_DEFAULT);
    }

    @NotNull
    public String getCreateDatabaseSQL() {
        return validateStringProperty(SQL_CREATE_DATABASE, SQL_CREATE_DATABASE_DEFAULT);
    }
    
    @NotNull
    public String getCreateTableSQL() {
        return validateStringProperty(SQL_CREATE_TABLE, SQL_CREATE_TABLE_DEFAULT);
    }
    
    @NotNull
    public String getInsertTableSQL() {
        return validateStringProperty(SQL_INSERT_TABLE, SQL_INSERT_TABLE_DEFAULT);
    }
}