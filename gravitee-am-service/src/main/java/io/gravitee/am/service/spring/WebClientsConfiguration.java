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

import io.gravitee.am.service.http.WebClientBuilder;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import java.net.MalformedURLException;
import java.net.URI;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Lazy
@Configuration
public class WebClientsConfiguration {

    @Autowired
    private Environment environment;

    @Bean
    protected WebClientBuilder webClientBuilder() {
        return new WebClientBuilder(environment);
    }

    @Bean("recaptchaWebClient")
    protected WebClient recaptchaWebClient(Vertx vertx, WebClientBuilder webClientBuilder, io.gravitee.node.api.configuration.Configuration configuration) throws MalformedURLException {
        final String recaptchaServiceUrl = configuration.getProperty("reCaptcha.serviceUrl", "https://www.google.com/recaptcha/api/siteverify");
        return webClientBuilder.createWebClient(vertx, URI.create(recaptchaServiceUrl).toURL());
    }

    @Bean("newsletterWebClient")
    protected WebClient newsletterWebClient(Vertx vertx, WebClientBuilder webClientBuilder, io.gravitee.node.api.configuration.Configuration configuration) throws MalformedURLException {
        final String newsletterURL =  configuration.getProperty("newsletter.url", "https://newsletter.gravitee.io");
        return webClientBuilder.createWebClient(vertx, URI.create(newsletterURL).toURL());
    }

    @Bean("agentCardWebClient")
    protected WebClient agentCardWebClient(Vertx vertx, WebClientBuilder webClientBuilder) {
        return webClientBuilder.createWebClient(vertx);
    }
}
