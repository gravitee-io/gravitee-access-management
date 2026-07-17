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

/**
 * Builds a {@link CibaClient} bound to a specific federated connection's OP endpoint and credentials.
 *
 * <p>The client is intrinsically per-request: its endpoints and client credentials come from the
 * inbound {@link FederatedConnection} and the already-resolved {@link ProviderMetadata}, so there is no
 * single long-lived client. Discovery is resolved once, up front, by the provider (Y flow) and handed in
 * here; the factory itself is a stateless bean holding only the shared web client. Tests supply a stub
 * factory, removing the need for a test-only client field or a test-vs-production branch in the provider.
 */
@FunctionalInterface
public interface CibaClientFactory {

    CibaClient create(FederatedConnection connection, String resourceAudience, ProviderMetadata metadata);
}
