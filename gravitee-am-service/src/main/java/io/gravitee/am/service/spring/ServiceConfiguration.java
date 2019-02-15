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

import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.gravitee.am.service.authentication.crypto.password.RegexPasswordValidator;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan("io.gravitee.am.service")
public class ServiceConfiguration {
    private static final String DEFAULT_MAX_TOTAL_CONNECTION = "200";
    private static final String DEFAULT_CONNECTION_TIMEOUT = "10";

    @Autowired
    @Qualifier("graviteeProperties")
    private Properties properties;

    @Autowired
    private Vertx vertx;

    @Bean
    public WebClient webClient() {
        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout(Integer.valueOf(properties.getProperty("oidc.http.connectionTimeout", DEFAULT_CONNECTION_TIMEOUT)) * 1000)
                .setMaxPoolSize(Integer.valueOf(properties.getProperty("oidc.http.pool.maxTotalConnection", DEFAULT_MAX_TOTAL_CONNECTION)))
                .setTrustAll(Boolean.valueOf(properties.getProperty("oidc.http.client.trustAll", "true")));

        return WebClient.create(vertx,options);
    }

    @Bean
    public PasswordValidator passwordValidator() {
        return new RegexPasswordValidator();
    }
}
