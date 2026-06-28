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
package io.gravitee.am.authdevice.notifier.cibafederation.provider.spring;

import io.gravitee.am.authdevice.notifier.cibafederation.provider.CibaClientFactory;
import io.gravitee.am.authdevice.notifier.cibafederation.provider.ConsentRelayStrategyRegistry;
import io.gravitee.am.authdevice.notifier.cibafederation.provider.DefaultCibaClientFactory;
import io.gravitee.am.authdevice.notifier.cibafederation.provider.OidcDiscoveryResolver;
import io.gravitee.am.service.http.WebClientBuilder;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class CibaFederationProviderSpringConfiguration {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Environment environment;

    @Bean
    public WebClientBuilder webClientBuilder() {
        return new WebClientBuilder(environment);
    }

    @Bean
    public OidcDiscoveryResolver oidcDiscoveryResolver(@Qualifier("cibaFederationWebClient") io.vertx.rxjava3.ext.web.client.WebClient webClient) {
        long ttlSeconds = environment.getProperty("authDeviceNotifiers.cibaFederation.discovery.cacheTtlSeconds", Long.class, 3600L);
        return new OidcDiscoveryResolver(webClient, ttlSeconds);
    }

    @Bean
    public CibaClientFactory cibaClientFactory(@Qualifier("cibaFederationWebClient") WebClient webClient,
                                               OidcDiscoveryResolver oidcDiscoveryResolver) {
        return new DefaultCibaClientFactory(webClient, oidcDiscoveryResolver);
    }

    @Bean
    public ConsentRelayStrategyRegistry consentRelayStrategyRegistry() {
        // getClass().getClassLoader() is the plugin classloader → spans the plugin's lib/ jars.
        return ConsentRelayStrategyRegistry.discover(getClass().getClassLoader());
    }

    @Bean
    @Qualifier("cibaFederationWebClient")
    public WebClient cibaFederationWebClient(WebClientBuilder webClientBuilder) {
        // No fixed endpoint: the client uses absolute URLs (discovery doc + resolved OP endpoints + the
        // gateway callback), so scheme/TLS follow each absolute URL. The builder still applies node-level
        // TLS/HTTP options from gravitee.yml.
        WebClientOptions options = new WebClientOptions().setUserAgent("Gravitee.io-AM-CIBA-Federation/1");
        return webClientBuilder.createWebClient(vertx, options);
    }
}
