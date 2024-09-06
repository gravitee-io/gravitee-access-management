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

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderConfiguration;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.http.WebClientBuilder;
import io.gravitee.am.service.http.WebClientInitializer;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.concurrent.TimeUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class OAuth2GenericAuthenticationProviderConfiguration {

    private static final String DEFAULT_USER_AGENT = "Gravitee.io-AM/3";
    private static final String HTTPS = "https://";
    private static final TimeUnit DEFAULT_IDLE_TIMEOUT_UNIT = TimeUnit.MILLISECONDS;

    @Autowired
    private Vertx vertx;

    @Autowired
    private OAuth2GenericIdentityProviderConfiguration configuration;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private Environment environment;

    @Bean
    public WebClientBuilder webClientBuilder() {
        return new WebClientBuilder(environment);
    }

    @Bean
    @Qualifier("oauthWebClient")
    public WebClient httpClient(WebClientBuilder webClientBuilder) {
        WebClientOptions httpClientOptions = new WebClientOptions();
        httpClientOptions
                .setUserAgent(DEFAULT_USER_AGENT)
                .setConnectTimeout(configuration.getConnectTimeout())
                .setIdleTimeout(configuration.getIdleTimeout())
                .setIdleTimeoutUnit(DEFAULT_IDLE_TIMEOUT_UNIT)
                .setMaxPoolSize(configuration.getMaxPoolSize())
                .setSsl(isTLS());
        if (configuration.getClientAuthenticationMethod().equals(ClientAuthenticationMethod.TLS_CLIENT_AUTH)) {
            return initializeMTlsWebClient(webClientBuilder, httpClientOptions);
        } else {
            return createWebClient(webClientBuilder, httpClientOptions);
        }
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

    private WebClient initializeMTlsWebClient(WebClientBuilder webClientBuilder, WebClientOptions options) {
        Maybe<WebClient> webClient = certificateService
                .findById(configuration.getClientAuthenticationCertificate())
                .map(cert -> webClientBuilder.createMTLSWebClient(vertx, options, configuration.getUserAuthorizationUri(), cert));
        var delegate = WebClientInitializer.asyncInitialize(webClient).getDelegate();
        return new WebClient(delegate);
    }

    private WebClient createWebClient(WebClientBuilder webClientBuilder, WebClientOptions options) {
        return webClientBuilder.createWebClient(vertx, options, configuration.getUserAuthorizationUri());
    }

}
