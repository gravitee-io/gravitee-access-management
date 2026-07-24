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
package io.gravitee.am.reporter.elasticsearch.spring;

import io.gravitee.am.reporter.elasticsearch.ElasticsearchReporterConfiguration;
import io.gravitee.elasticsearch.client.Client;
import io.gravitee.elasticsearch.client.http.ClientSslConfiguration;
import io.gravitee.elasticsearch.client.http.HttpClient;
import io.gravitee.elasticsearch.client.http.HttpClientConfiguration;
import io.gravitee.elasticsearch.client.http.HttpClientJksSslConfiguration;
import io.gravitee.elasticsearch.client.http.HttpClientPfxSslConfiguration;
import io.gravitee.elasticsearch.config.Endpoint;
import io.vertx.rxjava3.core.Vertx;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Builds the reactive Vert.x instance and the Elasticsearch HTTP client used by the reporter, from
 * the reporter's own {@link ElasticsearchReporterConfiguration} (per-domain / per-organization).
 *
 * @author GraviteeSource Team
 */
@Configuration
public class ElasticsearchClientConfiguration {

    @Bean
    public Vertx vertxRx(io.vertx.core.Vertx vertx) {
        return Vertx.newInstance(vertx);
    }

    @Bean
    public Client httpClient(ElasticsearchReporterConfiguration configuration) {
        HttpClientConfiguration clientConfiguration = new HttpClientConfiguration();

        List<Endpoint> endpoints = configuration.getEndpoints().stream().map(Endpoint::new).collect(toList());
        clientConfiguration.setEndpoints(endpoints);
        clientConfiguration.setUsername(configuration.getUsername());
        clientConfiguration.setPassword(configuration.getPassword());
        clientConfiguration.setRequestTimeout(configuration.getRequestTimeout());

        final String keystoreType = configuration.getSslKeystoreType();
        if (keystoreType != null) {
            if (keystoreType.equalsIgnoreCase(ClientSslConfiguration.JKS_KEYSTORE_TYPE)) {
                clientConfiguration.setSslConfig(
                        new HttpClientJksSslConfiguration(configuration.getSslKeystorePath(), configuration.getSslKeystorePassword()));
            } else if (keystoreType.equalsIgnoreCase(ClientSslConfiguration.PFX_KEYSTORE_TYPE) || keystoreType.equalsIgnoreCase("pkcs12")) {
                clientConfiguration.setSslConfig(
                        new HttpClientPfxSslConfiguration(configuration.getSslKeystorePath(), configuration.getSslKeystorePassword()));
            }
        }

        return new HttpClient(clientConfiguration);
    }
}
