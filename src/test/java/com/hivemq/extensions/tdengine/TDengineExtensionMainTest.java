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

import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extensions.tdengine.TDengineExtensionMain;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TDengineExtensionMainTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Mock
    ExtensionStartInput extensionStartInput;

    @Mock
    ExtensionStartOutput extensionStartOutput;

    @Mock
    ExtensionInformation extensionInformation;

    private File root;

    private File file;

    @Before
    public void set_up() throws IOException {
        MockitoAnnotations.initMocks(this);

        root = folder.getRoot();
        String fileName = "taosdata.properties";
        file = folder.newFile(fileName);
    }


    @Test
    public void extensionStart_failed_no_configuration_file() {
        file.delete();

        final TDengineExtensionMain main = new TDengineExtensionMain();
        when(extensionStartInput.getExtensionInformation()).thenReturn(extensionInformation);
        when(extensionStartInput.getExtensionInformation().getExtensionHomeFolder()).thenReturn(root);


        main.extensionStart(extensionStartInput, extensionStartOutput);

        verify(extensionStartOutput).preventExtensionStartup(anyString());
    }

    @Test
    public void extensionStart_failed_configuration_file_not_valid() throws IOException {

        final List<String> lines = Arrays.asList("mode=wrong", "mqtt_topic=");
        Files.write(file.toPath(), lines, Charset.forName("UTF-8"));

        final TDengineExtensionMain main = new TDengineExtensionMain();
        when(extensionStartInput.getExtensionInformation()).thenReturn(extensionInformation);
        when(extensionStartInput.getExtensionInformation().getExtensionHomeFolder()).thenReturn(root);


        main.extensionStart(extensionStartInput, extensionStartOutput);

        verify(extensionStartOutput).preventExtensionStartup(anyString());
    }

    @Ignore
    @Test
    public void extensionStart_failed_configuration_file_valid() throws IOException {

        final List<String> lines = Arrays.asList("mode=http", "mqtt_topic=application/sensor_data", 
        		"http.url=http://127.0.0.1:6041/rest/sql/", "http.token=root:taosdata",
        		"sql.create_database=create database if not exists hivemqdb;",
        		"sql.create_table=create table if not exists hivemqdb.sensor_data (ts timestamp, temperature float, voltage int) TAGS (name binary(32), groupid int);", 
        		"sql.insert_table=insert into hivemqdb.sensor_data_${payload.groupid} using hivemqdb.sensor_data TAGS ('${payload.name}', ${payload.groupid}) VALUES (now, ${payload.temperature}, ${payload.voltage});");
        Files.write(file.toPath(), lines, Charset.forName("UTF-8"));

        final TDengineExtensionMain main = new TDengineExtensionMain();
        when(extensionStartInput.getExtensionInformation()).thenReturn(extensionInformation);
        when(extensionStartInput.getExtensionInformation().getExtensionHomeFolder()).thenReturn(root);


        main.extensionStart(extensionStartInput, extensionStartOutput);

        verify(extensionStartOutput, times(0));
    }

    @Test
    public void extensionStop() {
    }
}