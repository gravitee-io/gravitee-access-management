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

import io.gravitee.am.model.Certificate;
import io.gravitee.common.util.EnvironmentUtils;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final Pattern WILDCARD_PATTERN = Pattern.compile("\\*\\.");

    @Autowired
    private Environment environment;

    public WebClientBuilder() {}

    public WebClientBuilder(Environment environment) {
        this.environment = environment;
    }

    public WebClient createWebClient(Vertx vertx, URL url) {
        final boolean isSsl = url.getProtocol().equals(HTTPS_SCHEME);
        final int port = url.getPort() != -1 ? url.getPort() : (isSsl ? 443 : 80);

        WebClientOptions options = new WebClientOptions()
                .setDefaultPort(port)
                .setDefaultHost(url.getHost())
                .setKeepAlive(true)
                .setMaxPoolSize(httpClientMaxPoolSize())
                .setTcpKeepAlive(true)
                .setConnectTimeout(httpClientTimeout())
                .setSsl(isSsl);

        configureHttp2Settings(options);

        return createWebClient(vertx, options);
    }

    public WebClient createWebClient(Vertx vertx, WebClientOptions options) {
        return createWebClient(vertx, options, null);
    }

    public WebClient createWebClient(Vertx vertx, WebClientOptions options, String url, Boolean withSystemProxy){
        var configurer = new WebClientOptionsConfigurer(environment);
        if (!isExcludedHost(url) && withSystemProxy) {
            configurer.setProxySettings(options);
        }
        configurer.setSSLSettings(options);
        configureHttp2Settings(options);
        
        return WebClient.create(vertx, options);
    }

    public WebClient createWebClient(Vertx vertx, WebClientOptions options, String url) {
        var configurer = new WebClientOptionsConfigurer(environment);
        if (!isExcludedHost(url)) {
            configurer.setProxySettings(options);
        }
        configurer.setSSLSettings(options);
        configureHttp2Settings(options);
        
        return WebClient.create(vertx, options);
    }

    public WebClient createMTLSWebClient(Vertx vertx, WebClientOptions options, String url, Certificate clientCertificate) {
        var configurer = new WebClientOptionsConfigurer(environment);
        if (!isExcludedHost(url)) {
            configurer.setProxySettings(options);
        }
        configurer.setMTLSSettings(options, clientCertificate);
        configureHttp2Settings(options);
        
        return WebClient.create(vertx, options);
    }

    private void configureHttp2Settings(WebClientOptions options) {
        if (isHttp2Enabled()) {
            options.setUseAlpn(true);
            options.setHttp2MaxPoolSize(http2MaxPoolSize());
            options.setHttp2ConnectionWindowSize(http2ConnectionWindowSize());
            options.setHttp2KeepAliveTimeout(http2KeepAliveTimeout());
            LOGGER.debug("HTTP/2 enabled with ALPN protocol negotiation");
        } else {
            options.setUseAlpn(false);
            options.setHttp2ClearTextUpgrade(false);
            LOGGER.debug("HTTP/1.1 mode with optimized connection pooling");
        }
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

    private Integer httpClientMaxPoolSize() {
        return environment.getProperty("httpClient.maxPoolSize", Integer.class, 10);
    }

    private boolean isHttp2Enabled() {
        return environment.getProperty("httpClient.http2.enabled", Boolean.class, true);
    }

    private Integer http2MaxPoolSize() {
        return environment.getProperty("httpClient.http2.maxPoolSize", Integer.class, 10);
    }

    private Integer http2ConnectionWindowSize() {
        return environment.getProperty("httpClient.http2.connectionWindowSize", Integer.class, Http2Settings.DEFAULT_INITIAL_WINDOW_SIZE);
    }

    private Integer http2KeepAliveTimeout() {
        return environment.getProperty("httpClient.http2.keepAliveTimeout", Integer.class, HttpClientOptions.DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT);
    }

}
