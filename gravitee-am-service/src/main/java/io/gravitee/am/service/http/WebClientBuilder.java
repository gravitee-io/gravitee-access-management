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
package io.gravitee.am.service.http;

import io.gravitee.common.util.EnvironmentUtils;
import io.vertx.core.net.*;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebClientBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebClientBuilder.class);
    private static final String HTTPS_SCHEME = "https";
    private static final String JKS_KEYSTORE_TYPE = "jks";
    private static final String PKCS12_KEYSTORE_TYPE = "pkcs12";
    private static final String PEM_KEYSTORE_TYPE = "pem";
    private static final String SSL_TRUST_STORE_PATH = "httpClient.ssl.truststore.path";
    private static final String SSL_TRUST_STORE_PASSWORD = "httpClient.ssl.truststore.password";
    private static final String SSL_KEYSTORE_STORE_PATH = "httpClient.ssl.keystore.path";
    private static final String SSL_KEYSTORE_STORE_KEY_PATH = "httpClient.ssl.keystore.keyPath";
    private static final String SSL_KEYSTORE_STORE_PASSWORD = "httpClient.ssl.keystore.password";
    private static final Pattern WILDCARD_PATTERN = Pattern.compile("\\*\\.");

    private final Environment environment;

    public WebClientBuilder(Environment environment) {
        this.environment = environment;
    }
    public WebClient createWebClient(Vertx vertx, URL url) {

        final int port = url.getPort() != -1 ? url.getPort() : (HTTPS_SCHEME.equals(url.getProtocol()) ? 443 : 80);

        WebClientOptions options = new WebClientOptions()
                .setDefaultPort(port)
                .setDefaultHost(url.getHost())
                .setKeepAlive(true)
                .setMaxPoolSize(10)
                .setTcpKeepAlive(true)
                .setConnectTimeout(httpClientTimeout())
                .setSsl(url.getProtocol().equals(HTTPS_SCHEME));

        return createWebClient(vertx, options);
    }

    public WebClient createWebClient(Vertx vertx, WebClientOptions options) {
        return createWebClient(vertx, options, null);
    }

    public WebClient createWebClient(Vertx vertx, WebClientOptions options, String url) {
        setProxySettings(options, url);
        setSSLSettings(options);
        return WebClient.create(vertx, options);
    }

    private void setProxySettings(WebClientOptions options, String url) {

        if (this.isProxyConfigured() && !isExcludedHost(url)) {
            ProxyOptions proxyOptions = new ProxyOptions();
            proxyOptions.setType(ProxyType.valueOf(httpClientProxyType()));
            if (options.isSsl()) {
                proxyOptions.setHost(httpClientProxyHttpsHost());
                proxyOptions.setPort(httpClientProxyHttpsPort());
                proxyOptions.setUsername(httpClientProxyHttpsUsername());
                proxyOptions.setPassword(httpClientProxyHttpsPassword());
            } else {
                proxyOptions.setHost(httpClientProxyHttpHost());
                proxyOptions.setPort(httpClientProxyHttpPort());
                proxyOptions.setUsername(httpClientProxyHttpUsername());
                proxyOptions.setPassword(httpClientProxyHttpPassword());
            }
            options.setProxyOptions(proxyOptions);
        }
    }

    private void setSSLSettings(WebClientOptions options) {
        if (isSSLEnabled()) {
            options.setTrustAll(isSSLTrustAllEnabled());
            options.setVerifyHost(isSSLVerifyHostEnabled());
            if (sslTrustStoreType() != null) {
                switch (sslTrustStoreType()) {
                    case JKS_KEYSTORE_TYPE -> setJksTrustOptions(options);
                    case PKCS12_KEYSTORE_TYPE -> setPfxTrustOptions(options);
                    case PEM_KEYSTORE_TYPE -> setPemTrustOptions(options);
                    default -> LOGGER.error("No suitable httpClient SSL TrustStore type found for : " + sslTrustStoreType());
                }
            }
            if (sslKeyStoreType() != null) {
                switch (sslKeyStoreType()) {
                    case JKS_KEYSTORE_TYPE -> setJksKeyOptions(options);
                    case PKCS12_KEYSTORE_TYPE -> setPfxKeyOptions(options);
                    case PEM_KEYSTORE_TYPE -> setPemKeyOptions(options);
                    default -> LOGGER.error("No suitable httpClient SSL KeyStore type found for : " + sslKeyStoreType());
                }
            }
        }
    }

    private void setJksTrustOptions(WebClientOptions options) {
        JksOptions jksOptions = new JksOptions();
        jksOptions.setPath(environment.getProperty(SSL_TRUST_STORE_PATH));
        jksOptions.setPassword(environment.getProperty(SSL_TRUST_STORE_PASSWORD));
        options.setTrustStoreOptions(jksOptions);
    }

    private void setPemTrustOptions(WebClientOptions options) {
        PemTrustOptions pemOptions = new PemTrustOptions();
        pemOptions.addCertPath(environment.getProperty(SSL_TRUST_STORE_PATH));
        options.setPemTrustOptions(pemOptions);
    }

    private void setPfxTrustOptions(WebClientOptions options) {
        PfxOptions pfxOptions = new PfxOptions();
        pfxOptions.setPath(environment.getProperty(SSL_TRUST_STORE_PATH));
        pfxOptions.setPassword(environment.getProperty(SSL_TRUST_STORE_PASSWORD));
        options.setPfxTrustOptions(pfxOptions);
    }

    private void setJksKeyOptions(WebClientOptions options) {
        JksOptions jksOptions = new JksOptions();
        jksOptions.setPath(environment.getProperty(SSL_KEYSTORE_STORE_PATH));
        jksOptions.setPassword(environment.getProperty(SSL_KEYSTORE_STORE_PASSWORD));
        options.setKeyStoreOptions(jksOptions);
    }

    private void setPemKeyOptions(WebClientOptions options) {
        PemKeyCertOptions pemOptions = new PemKeyCertOptions();
        pemOptions.setCertPath(environment.getProperty(SSL_KEYSTORE_STORE_PATH));
        pemOptions.setKeyPath(environment.getProperty(SSL_KEYSTORE_STORE_KEY_PATH));
        options.setPemKeyCertOptions(pemOptions);
    }

    private void setPfxKeyOptions(WebClientOptions options) {
        PfxOptions pfxOptions = new PfxOptions();
        pfxOptions.setPath(environment.getProperty(SSL_KEYSTORE_STORE_PATH));
        pfxOptions.setPassword(environment.getProperty(SSL_KEYSTORE_STORE_PASSWORD));
        options.setPfxKeyCertOptions(pfxOptions);
    }

    private boolean isExcludedHost(String url) {
        if (url == null) {
            return false;
        }

        try {
            final List<String> proxyExcludeHosts = EnvironmentUtils
                    .getPropertiesStartingWith((ConfigurableEnvironment) environment, "httpClient.proxy.exclude-hosts")
                    .values()
                    .stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());

            if (url.contains("?")) {
                // Remove the query part as it could contain invalid characters such as those used in El expression.
                url = url.substring(0, url.indexOf('?'));
            }

            URL uri = URI.create(url).toURL();
            String host = uri.getHost();
            return proxyExcludeHosts.stream().anyMatch(excludedHost -> {
                if (excludedHost.startsWith("*.")) {
                    return host.endsWith(WILDCARD_PATTERN.matcher(excludedHost).replaceFirst(""));
                } else {
                    return host.equals(excludedHost);
                }
            });
        } catch (Exception ex) {
            LOGGER.error("An error has occurred when calculating proxy excluded hosts", ex);
            return false;
        }
    }

    private Integer httpClientTimeout() {
        return environment.getProperty("httpClient.timeout", Integer.class, 10000);
    }

    private String httpClientProxyType() {
        return environment.getProperty("httpClient.proxy.type", "HTTP");
    }

    private String httpClientProxyHttpHost() {
        return environment.getProperty("httpClient.proxy.http.host", System.getProperty("http.proxyHost", "localhost"));
    }

    private Integer httpClientProxyHttpPort() {
        return environment.getProperty(
                "httpClient.proxy.http.port",
                Integer.class,
                Integer.valueOf(System.getProperty("http.proxyPort", "3128"))
        );
    }

    private String httpClientProxyHttpUsername() {
        return environment.getProperty("httpClient.proxy.http.username");
    }

    private String httpClientProxyHttpPassword() {
        return environment.getProperty("httpClient.proxy.http.password");
    }

    private String httpClientProxyHttpsHost() {
        return environment.getProperty("httpClient.proxy.https.host", System.getProperty("https.proxyHost", "localhost"));
    }

    private Integer httpClientProxyHttpsPort() {
        return environment.getProperty(
                "httpClient.proxy.https.port",
                Integer.class,
                Integer.valueOf(System.getProperty("https.proxyPort", "3128"))
        );
    }

    private String httpClientProxyHttpsUsername() {
        return environment.getProperty("httpClient.proxy.https.username");
    }

    private String httpClientProxyHttpsPassword() {
        return environment.getProperty("httpClient.proxy.https.password");
    }

    private boolean isProxyConfigured() {
        return environment.getProperty(
                "httpClient.proxy.enabled",
                Boolean.class,
                false);
    }

    private boolean isSSLEnabled() {
        return environment.getProperty(
                "httpClient.ssl.enabled",
                Boolean.class,
                false);
    }

    private boolean isSSLTrustAllEnabled() {
        return environment.getProperty(
                "httpClient.ssl.trustAll",
                Boolean.class,
                false);
    }

    private boolean isSSLVerifyHostEnabled() {
        return environment.getProperty(
                "httpClient.ssl.verifyHost",
                Boolean.class,
                true);
    }


    private String sslTrustStoreType() {
        return environment.getProperty("httpClient.ssl.truststore.type");
    }

    private String sslKeyStoreType() {
        return environment.getProperty("httpClient.ssl.keystore.type");
    }
}
