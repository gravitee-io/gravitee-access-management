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
import io.gravitee.am.reporter.elasticsearch.LoggableEndpoints;
import io.gravitee.elasticsearch.client.Client;
import io.gravitee.elasticsearch.client.http.ClientSslConfiguration;
import io.gravitee.elasticsearch.client.http.HttpClient;
import io.gravitee.elasticsearch.client.http.HttpClientConfiguration;
import io.gravitee.elasticsearch.client.http.HttpClientJksSslConfiguration;
import io.gravitee.elasticsearch.client.http.HttpClientPemSslConfiguration;
import io.gravitee.elasticsearch.client.http.HttpClientPfxSslConfiguration;
import io.gravitee.elasticsearch.config.Endpoint;
import io.vertx.core.net.ProxyType;
import io.vertx.rxjava3.core.Vertx;
import lombok.CustomLog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Builds the reactive Vert.x instance and the Elasticsearch HTTP client used by the reporter, from
 * the reporter's own {@link ElasticsearchReporterConfiguration} (per-domain / per-organization).
 *
 * @author GraviteeSource Team
 */
@CustomLog
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

        warnAboutUnverifiedServerCertificates(configuration);
        applyClientCertificate(configuration, clientConfiguration);
        applyProxy(configuration, clientConfiguration);

        return new HttpClient(clientConfiguration);
    }

    /**
     * Routes the cluster traffic through an egress proxy when one is configured. The client picks
     * between its http and https proxy settings by endpoint scheme, so the one proxy configured here
     * is applied to both — otherwise switching an endpoint from http to https would silently stop
     * using the proxy.
     */
    private void applyProxy(ElasticsearchReporterConfiguration configuration, HttpClientConfiguration clientConfiguration) {
        if (!configuration.isProxyConfigured()) {
            return;
        }
        Integer port = configuration.getProxyPort();
        if (port == null || port <= 0 || port > 65535) {
            throw new IllegalStateException(("The Elasticsearch reporter is configured to use the proxy at '%s' but its " +
                    "port is %s. Set a proxy port between 1 and 65535, or clear the proxy host.")
                    .formatted(configuration.getProxyHost(), port));
        }

        clientConfiguration.setProxyType(proxyType(configuration.getProxyType()));
        clientConfiguration.setProxyHttpHost(configuration.getProxyHost());
        clientConfiguration.setProxyHttpPort(port);
        clientConfiguration.setProxyHttpUsername(configuration.getProxyUsername());
        clientConfiguration.setProxyHttpPassword(configuration.getProxyPassword());
        clientConfiguration.setProxyHttpsHost(configuration.getProxyHost());
        clientConfiguration.setProxyHttpsPort(port);
        clientConfiguration.setProxyHttpsUsername(configuration.getProxyUsername());
        clientConfiguration.setProxyHttpsPassword(configuration.getProxyPassword());
        clientConfiguration.setProxyConfigured(true);

        log.info("Audits will be sent to Elasticsearch through the {} proxy at {}:{}",
                clientConfiguration.getProxyType(), configuration.getProxyHost(), port);
    }

    /**
     * The client passes this straight to {@link ProxyType#valueOf}, which would otherwise fail bean
     * creation with a bare enum-constant error naming neither the reporter nor the accepted values.
     */
    private String proxyType(String configured) {
        String type = configured == null || configured.isBlank()
                ? ProxyType.HTTP.name()
                : configured.trim().toUpperCase(Locale.ROOT);
        try {
            return ProxyType.valueOf(type).name();
        } catch (IllegalArgumentException unsupported) {
            throw new IllegalStateException(("Unsupported Elasticsearch proxy type '%s'. Supported types are %s.")
                    .formatted(configured, Arrays.stream(ProxyType.values()).map(ProxyType::name).collect(joining(", "))));
        }
    }

    /**
     * The shared Elasticsearch client enables TLS and trusts every certificate on an https endpoint,
     * with no truststore, CA or hostname verification option anywhere in it. That is inherited
     * platform behaviour rather than something this reporter introduces, but audit records are
     * sensitive enough that it should be visible rather than latent.
     */
    private void warnAboutUnverifiedServerCertificates(ElasticsearchReporterConfiguration configuration) {
        List<String> secureEndpoints = configuration.getEndpoints().stream()
                .filter(endpoint -> endpoint != null && endpoint.toLowerCase(Locale.ROOT).startsWith("https://"))
                .toList();
        if (!secureEndpoints.isEmpty()) {
            log.warn("Audits will be sent to {} over TLS, but the Elasticsearch server certificate is NOT verified: " +
                            "the shared Gravitee Elasticsearch client trusts any certificate presented. Treat the link as " +
                            "encrypted but unauthenticated, and keep it on a trusted network.",
                    LoggableEndpoints.redact(secureEndpoints));
        }
    }

    private void applyClientCertificate(ElasticsearchReporterConfiguration configuration, HttpClientConfiguration clientConfiguration) {
        final String keystoreType = configuration.getSslKeystoreType();
        if (keystoreType == null || keystoreType.isBlank()) {
            return;
        }
        if (keystoreType.equalsIgnoreCase(ClientSslConfiguration.JKS_KEYSTORE_TYPE)) {
            clientConfiguration.setSslConfig(
                    new HttpClientJksSslConfiguration(configuration.getSslKeystorePath(), configuration.getSslKeystorePassword()));
        } else if (keystoreType.equalsIgnoreCase(ClientSslConfiguration.PFX_KEYSTORE_TYPE) || keystoreType.equalsIgnoreCase("pkcs12")) {
            clientConfiguration.setSslConfig(
                    new HttpClientPfxSslConfiguration(configuration.getSslKeystorePath(), configuration.getSslKeystorePassword()));
        } else if (keystoreType.equalsIgnoreCase(ClientSslConfiguration.PEM_KEYSTORE_TYPE)) {
            if (configuration.getSslPemCerts().isEmpty() || configuration.getSslPemKeys().isEmpty()) {
                throw new IllegalStateException("The Elasticsearch reporter is configured with a pem client certificate " +
                        "but no certificate or key path was given. Set both sslPemCerts and sslPemKeys, or clear the client certificate type.");
            }
            clientConfiguration.setSslConfig(
                    new HttpClientPemSslConfiguration(configuration.getSslPemCerts(), configuration.getSslPemKeys()));
        } else {
            throw new IllegalStateException("Unsupported Elasticsearch client certificate type '%s'. Supported types are jks, pkcs12 and pem."
                    .formatted(keystoreType));
        }
    }
}
