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
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private static final Pattern WILCARD_PATTERN = Pattern.compile("\\*\\.");

    @Value("${httpClient.timeout:10000}")
    private int httpClientTimeout;

    @Value("${httpClient.proxy.type:HTTP}")
    private String httpClientProxyType;

    @Value("${httpClient.proxy.http.host:#{systemProperties['http.proxyHost'] ?: 'localhost'}}")
    private String httpClientProxyHttpHost;

    @Value("${httpClient.proxy.http.port:#{systemProperties['http.proxyPort'] ?: 3128}}")
    private int httpClientProxyHttpPort;

    @Value("${httpClient.proxy.http.username:#{null}}")
    private String httpClientProxyHttpUsername;

    @Value("${httpClient.proxy.http.password:#{null}}")
    private String httpClientProxyHttpPassword;

    @Value("${httpClient.proxy.https.host:#{systemProperties['https.proxyHost'] ?: 'localhost'}}")
    private String httpClientProxyHttpsHost;

    @Value("${httpClient.proxy.https.port:#{systemProperties['https.proxyPort'] ?: 3128}}")
    private int httpClientProxyHttpsPort;

    @Value("${httpClient.proxy.https.username:#{null}}")
    private String httpClientProxyHttpsUsername;

    @Value("${httpClient.proxy.https.password:#{null}}")
    private String httpClientProxyHttpsPassword;

    @Value("${httpClient.proxy.enabled:false}")
    private boolean isProxyConfigured;

    @Autowired
    private Environment environment;

    public WebClient createWebClient(Vertx vertx, URL url) {

        final int port = url.getPort() != -1 ? url.getPort() : (HTTPS_SCHEME.equals(url.getProtocol()) ? 443 : 80);

        WebClientOptions options = new WebClientOptions()
                .setDefaultPort(port)
                .setDefaultHost(url.getHost())
                .setKeepAlive(true)
                .setMaxPoolSize(10)
                .setTcpKeepAlive(true)
                .setConnectTimeout(httpClientTimeout)
                .setSsl(url.getProtocol().equals(HTTPS_SCHEME));

        return createWebClient(vertx, options);
    }

    public WebClient createWebClient(Vertx vertx, WebClientOptions options) {
        return createWebClient(vertx, options, null);
    }

    public WebClient createWebClient(Vertx vertx, WebClientOptions options, String url) {
        setProxySettings(options, url);
        return WebClient.create(vertx, options);
    }

    private void setProxySettings(WebClientOptions options, String url) {

        if (this.isProxyConfigured && !isExcludedHost(url)) {
            ProxyOptions proxyOptions = new ProxyOptions();
            proxyOptions.setType(ProxyType.valueOf(httpClientProxyType));
            if (options.isSsl()) {
                proxyOptions.setHost(httpClientProxyHttpsHost);
                proxyOptions.setPort(httpClientProxyHttpsPort);
                proxyOptions.setUsername(httpClientProxyHttpsUsername);
                proxyOptions.setPassword(httpClientProxyHttpsPassword);
            } else {
                proxyOptions.setHost(httpClientProxyHttpHost);
                proxyOptions.setPort(httpClientProxyHttpPort);
                proxyOptions.setUsername(httpClientProxyHttpUsername);
                proxyOptions.setPassword(httpClientProxyHttpPassword);
            }
            options.setProxyOptions(proxyOptions);
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

            if(url.contains("?")) {
                // Remove the query part as it could contains invalid characters such as those used in El expression.
                url = url.substring(0, url.indexOf('?'));
            }

            URL uri = URI.create(url).toURL();
            String host = uri.getHost();
            return proxyExcludeHosts.stream().anyMatch(excludedHost -> {
                if (excludedHost.startsWith("*.")) {
                    return host.endsWith(WILCARD_PATTERN.matcher(excludedHost).replaceFirst(""));
                } else {
                    return host.equals(excludedHost);
                }
            });
        } catch (Exception ex) {
            LOGGER.error("An error has occurred when calculating proxy excluded hosts", ex);
            return false;
        }
    }
}
