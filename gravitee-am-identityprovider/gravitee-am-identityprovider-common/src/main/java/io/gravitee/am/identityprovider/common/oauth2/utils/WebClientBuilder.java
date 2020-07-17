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
package io.gravitee.am.identityprovider.common.oauth2.utils;

import io.gravitee.am.identityprovider.api.social.SocialIdentityProviderConfiguration;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebClientBuilder {

    private static final String DEFAULT_USER_AGENT = "Gravitee.io-AM/3";

    public static WebClient build(Vertx vertx, SocialIdentityProviderConfiguration configuration) {
        WebClientOptions httpClientOptions = new WebClientOptions();
        httpClientOptions
                .setUserAgent(DEFAULT_USER_AGENT)
                .setConnectTimeout(configuration.getConnectTimeout())
                .setMaxPoolSize(configuration.getMaxPoolSize());

        return WebClient.create(vertx, httpClientOptions);
    }
}
