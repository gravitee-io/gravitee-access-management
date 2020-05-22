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
package io.gravitee.am.identityprovider.http.authentication.spring;

import io.gravitee.am.identityprovider.http.configuration.HttpIdentityProviderConfiguration;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class HttpAuthenticationProviderConfiguration {

    private static final String DEFAULT_USER_AGENT = "Gravitee.io-AM";

    @Autowired
    private Vertx vertx;

    @Autowired
    private HttpIdentityProviderConfiguration configuration;

    @Bean
    public WebClient httpClient() {
        WebClientOptions httpClientOptions = new WebClientOptions();
        httpClientOptions
                .setUserAgent(DEFAULT_USER_AGENT)
                .setConnectTimeout(configuration.getConnectTimeout())
                .setMaxPoolSize(configuration.getMaxPoolSize());

        if (configuration.getAuthenticationResource().getBaseURL() != null
                && configuration.getAuthenticationResource().getBaseURL().startsWith("https://")) {
            httpClientOptions.setSsl(true);
            httpClientOptions.setTrustAll(true);
        }

        return WebClient.create(vertx, httpClientOptions);
    }
}
