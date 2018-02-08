/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.identityprovider.oauth2.authentication.spring;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class OAuth2GenericAuthenticationProviderConfiguration {

    private static final String DEFAULT_MAX_TOTAL_CONNECTION = "200";
    private static final String DEFAULT_MAX_PER_ROUTE = "100";
    private static final String DEFAULT_CONNECTION_TIMEOUT = "10";
    private static final String DEFAULT_CONNECTION_REQUEST_TIMEOUT = "10";
    private static final String DEFAULT_SOCKET_TIMEOUT = "10";

    @Autowired
    @Qualifier("graviteeProperties")
    private Properties properties;

    @Bean
    public HttpClient httpClient() {
        // pooling connection manager
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
            cm.setMaxTotal(Integer.valueOf(properties.getProperty("identities.oauth2.http.pool.maxTotalConnection", DEFAULT_MAX_TOTAL_CONNECTION)));
            cm.setDefaultMaxPerRoute(Integer.valueOf(properties.getProperty("identities.oauth2.http.pool.maxPerRoute", DEFAULT_MAX_PER_ROUTE)));

        // connection configuration
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Integer.valueOf(properties.getProperty("identities.oauth2.http.connectionTimeout", DEFAULT_CONNECTION_TIMEOUT)) * 1000)
                .setConnectionRequestTimeout(Integer.valueOf(properties.getProperty("identities.oauth2.http.connectionRequestTimeout", DEFAULT_CONNECTION_REQUEST_TIMEOUT)) * 1000)
                .setSocketTimeout(Integer.valueOf(properties.getProperty("identities.oauth2.http.socketTimeout", DEFAULT_SOCKET_TIMEOUT)) * 1000).build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(config)
                .build();

        return httpClient;
    }
}
