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
package io.gravitee.am.gateway.handler.common.client.cimd;

import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Service for resolving OAuth clients via Client ID Metadata Document (CIMD).
 *
 * <p>When a {@code client_id} is URL-shaped ({@code ^https?://}), this service fetches
 * the remote metadata document, validates it against domain CIMD settings, and synthesizes
 * a runtime {@link Client} from the validated metadata and a template application.</p>
 *
 * @author GraviteeSource Team
 */
public interface CimdMetadataService {

    /**
     * Attempt to resolve a client via CIMD metadata fetch and validation.
     *
     * @param clientId the client_id from the authorization request
     * @param templateClient the template application to clone and overlay with metadata fields
     * @return {@code Maybe.just(synthesizedClient)} on success,
     *         {@code Maybe.empty()} if clientId is not URL-shaped,
     *         {@code Maybe.error(InvalidClientMetadataException)} on validation/fetch failure
     */
    Maybe<Client> resolveClient(String clientId, Client templateClient);

    /**
     * Synthesize a runtime {@link Client} from an already-persisted metadata document,
     * without any network fetch. Used by fan-out flows (e.g. OpenID Provider Commands
     * dispatch) that enumerate the domain's stored CIMD documents instead of resolving
     * a single known client_id.
     *
     * @param document the persisted metadata document (clientId + raw metadata JSON)
     * @param templateClient the template application to clone and overlay with metadata fields
     * @return the synthesized client
     * @throws io.gravitee.am.service.exception.InvalidClientMetadataException if the stored
     *         metadata no longer passes synthesis validation
     */
    Client synthesizeFromDocument(CimdMetadataDocument document, Client templateClient);
}
