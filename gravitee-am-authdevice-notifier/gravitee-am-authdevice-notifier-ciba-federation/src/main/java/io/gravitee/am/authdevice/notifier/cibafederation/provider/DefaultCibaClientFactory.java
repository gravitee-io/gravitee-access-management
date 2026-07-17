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
 * Production {@link CibaClientFactory}: a stateless bean holding the shared web client, building a
 * per-request {@link CibaClient} from the federated connection's credentials, the notifier's configured
 * resource audience, and the already-resolved {@link ProviderMetadata} (discovery is resolved once, up
 * front, by the provider and handed in here).
 */
public class DefaultCibaClientFactory implements CibaClientFactory {

    private final WebClient webClient;

    public DefaultCibaClientFactory(WebClient webClient) {
        this.webClient = Objects.requireNonNull(webClient, "webClient");
    }

    @Override
    public CibaClient create(FederatedConnection connection, String resourceAudience, ProviderMetadata metadata) {
        return new CibaClient(webClient, metadata,
                connection.clientId(), connection.clientSecret(), resourceAudience, connection.clientAuthMethod());
    }
}
