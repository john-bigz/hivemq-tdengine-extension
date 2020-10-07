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
 
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
 
import java.io.IOException;
import java.util.Map;
 
public class HttpClientUtil {
    private static PoolingHttpClientConnectionManager cm = null;
    private static final String EMPTY_STR = "";
    private static final int POOL_MAX_TOTAL = 50;
    private static final int POOL_MAX_PER_ROUTE = 5;

    
    private HttpClientUtil() {
    }

    private static void init() {
        if (cm == null) {
            cm = new PoolingHttpClientConnectionManager();
            cm.setMaxTotal(POOL_MAX_TOTAL);
            cm.setDefaultMaxPerRoute(POOL_MAX_PER_ROUTE);
        }
    }
 
    /**
     * Factory for retrieving HttpConnections.
     * @return The returned connection
     */
    private static CloseableHttpClient getHttpClient() {
        init();
        return HttpClients.custom().setConnectionManager(cm).build();
        
    }
 
    /**
     * Close connection manager
     */
    public static void closeConnectionManager() {
        if (cm != null) {
            cm.close();
        }
    }

    /**
     * Do HTTP Post request.
     * @param url
     * @param headers
     * @param json
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     */
    public static String httpPostRequest(String url, Map<String, Object> headers, String json) throws ClientProtocolException, IOException {
        HttpPost httpPost = new HttpPost(url);
        if (headers != null) {
            for (Map.Entry<String, Object> param : headers.entrySet()) {
                httpPost.addHeader(param.getKey(), String.valueOf(param.getValue()));
            }
        }
        StringEntity entity = new StringEntity(json,"utf-8");
        entity.setContentEncoding("UTF-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        return getResult(httpPost);
    }
 
 
 
 
    /**
     * processing HTTP Request.
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    private static String getResult(HttpRequestBase request) throws ClientProtocolException, IOException {
        CloseableHttpClient httpClient = getHttpClient();
        CloseableHttpResponse response = httpClient.execute(request);
        try {
	        HttpEntity entity = response.getEntity();
	        if (entity != null) {
	            String result = EntityUtils.toString(entity);
	            return result;
	        }
	        return EMPTY_STR;
        } finally {
        	if (response != null) {
       			response.close();
        	}
        }
    }

 
}