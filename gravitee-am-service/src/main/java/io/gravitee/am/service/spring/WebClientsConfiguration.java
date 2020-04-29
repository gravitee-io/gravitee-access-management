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
package io.gravitee.am.service.spring;

import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class WebClientsConfiguration {

    private static final String HTTPS_SCHEME = "https";

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

    @Value("#{systemProperties['httpClient.proxy'] == null ? false : true }")
    private boolean isProxyConfigured;

    @Value("${reCaptcha.serviceUrl:https://www.google.com/recaptcha/api/siteverify}")
    private String recaptchaServiceUrl;

    @Value("${newsletter.url:https://newsletter.gravitee.io}")
    private String newsletterURL;

    @Bean("recaptchaWebClient")
    protected WebClient recaptchaWebClient(Vertx vertx) throws MalformedURLException {
        return createWebClient(vertx, URI.create(recaptchaServiceUrl).toURL());
    }

    @Bean("newsletterWebClient")
    protected WebClient newsletterWebClient(Vertx vertx) throws MalformedURLException {
        return createWebClient(vertx, URI.create(newsletterURL).toURL());
    }

    private WebClient createWebClient(Vertx vertx, URL url) {

        final int port = url.getPort() != -1 ? url.getPort() : (HTTPS_SCHEME.equals(url.getProtocol()) ? 443 : 80);

        WebClientOptions options = new WebClientOptions()
                .setDefaultPort(port)
                .setDefaultHost(url.getHost())
                .setKeepAlive(true)
                .setMaxPoolSize(10)
                .setTcpKeepAlive(true)
                .setConnectTimeout(httpClientTimeout)
                .setSsl(url.getProtocol().equals(HTTPS_SCHEME));

        if (this.isProxyConfigured) {
            ProxyOptions proxyOptions = new ProxyOptions();
            proxyOptions.setType(ProxyType.valueOf(httpClientProxyType));
            if (HTTPS_SCHEME.equals(url.getProtocol())) {
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

        return WebClient.create(vertx, options);
    }
}
