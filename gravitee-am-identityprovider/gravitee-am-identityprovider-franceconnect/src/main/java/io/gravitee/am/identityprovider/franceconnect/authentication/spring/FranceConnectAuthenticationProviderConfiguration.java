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
package io.gravitee.am.identityprovider.franceconnect.authentication.spring;

import io.gravitee.am.identityprovider.common.oauth2.utils.WebClientBuilder;
import io.gravitee.am.identityprovider.franceconnect.FranceConnectIdentityProviderConfiguration;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class FranceConnectAuthenticationProviderConfiguration {

    @Autowired
    private Vertx vertx;

    @Autowired
    private FranceConnectIdentityProviderConfiguration configuration;

    @Bean
    @Qualifier("franceConnectWebClient")
    public WebClient httpClient() {
        return WebClientBuilder.build(vertx, configuration);
    }
}
