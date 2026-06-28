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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import io.gravitee.am.authdevice.notifier.api.model.FederatedConnection;
import io.vertx.rxjava3.ext.web.client.WebClient;

import java.util.Objects;

/**
 * Production {@link CibaClientFactory}: a stateless bean holding the shared web client and discovery
 * resolver, building a per-request {@link CibaClient} from the federated connection's endpoint and
 * credentials plus the notifier's configured resource audience.
 */
public class DefaultCibaClientFactory implements CibaClientFactory {

    private final WebClient webClient;
    private final OidcDiscoveryResolver discovery;

    public DefaultCibaClientFactory(WebClient webClient, OidcDiscoveryResolver discovery) {
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
    }

    @Override
    public CibaClient create(FederatedConnection connection, String resourceAudience) {
        return new CibaClient(webClient, discovery, connection.wellKnownUri(),
                connection.clientId(), connection.clientSecret(), resourceAudience, connection.clientAuthMethod());
    }
}
