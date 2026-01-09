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
package io.gravitee.am.resource.smtp.spring;

import io.gravitee.am.service.http.WebClientBuilder;
import io.gravitee.am.service.spring.email.OAuth2TokenService;
import io.vertx.rxjava3.core.Vertx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class SmtpResourceProviderConfiguration {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Environment environment;

    @Bean
    public WebClientBuilder webClientBuilder() {
        return new WebClientBuilder(environment);
    }

    @Bean
    public OAuth2TokenService oAuth2TokenService() {
        return new OAuth2TokenService(webClientBuilder(), vertx);
    }
}
