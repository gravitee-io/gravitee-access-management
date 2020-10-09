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

import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderConfiguration;
import io.gravitee.am.service.http.WebClientBuilder;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class OAuth2GenericAuthenticationProviderConfiguration {

    private static final String DEFAULT_USER_AGENT = "Gravitee.io-AM/3";
    private static final String HTTPS = "https://";

    @Autowired
    private Vertx vertx;

    @Autowired
    private OAuth2GenericIdentityProviderConfiguration configuration;

    @Bean
    public WebClientBuilder webClientBuilder() {
        return new WebClientBuilder();
    }

    @Bean
    @Qualifier("oauthWebClient")
    public WebClient httpClient(WebClientBuilder webClientBuilder) {
        WebClientOptions httpClientOptions = new WebClientOptions();
        httpClientOptions
                .setUserAgent(DEFAULT_USER_AGENT)
                .setConnectTimeout(configuration.getConnectTimeout())
                .setMaxPoolSize(configuration.getMaxPoolSize())
                .setSsl(isTLS());

        return webClientBuilder.createWebClient(vertx, httpClientOptions);
    }

    /**
     * Check if all defined oauth2 urls are secured or not.
     * This method is mainly used to determine if ssl should be enabled on the webClient used to communicate with the oauth2 server.
     *
     * @return <code>true</code> if all urls are secured, <code>false</code> else.
     */
    private boolean isTLS() {
        return configuration.getAccessTokenUri() != null && configuration.getAccessTokenUri().startsWith(HTTPS)
                && configuration.getUserAuthorizationUri() != null && configuration.getUserAuthorizationUri().startsWith(HTTPS)
                && configuration.getUserProfileUri() != null && configuration.getUserProfileUri().startsWith(HTTPS)
                && configuration.getWellKnownUri() != null && configuration.getWellKnownUri().startsWith(HTTPS);
    }
}
