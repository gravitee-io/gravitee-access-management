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
package io.gravitee.am.gateway.handler.oidc.service.cimd;

import io.gravitee.am.model.oidc.CIMDSettings;
import io.reactivex.rxjava3.core.Single;

/**
 * Fetches and validates CIMD metadata documents from client_id URIs.
 *
 * @author GraviteeSource Team
 */
public interface CIMDMetadataFetcher {

    /**
     * Fetch a CIMD metadata document from the given client_id URI.
     * Applies SSRF protection, size limits, and caching per the domain's CIMD settings.
     *
     * @param clientIdUri the client_id (which is a URI pointing to the metadata document)
     * @param domainId    the domain ID (used for cache scoping)
     * @param settings    the domain's CIMD settings
     * @return the parsed metadata document
     */
    Single<CIMDMetadataDocument> fetch(String clientIdUri, String domainId, CIMDSettings settings);
}
