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
package io.gravitee.am.management.handlers.management.api.adapter;

import io.gravitee.am.common.oauth2.ClientIds;
import io.gravitee.am.management.handlers.management.api.model.ApplicationEntity;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

/**
 * Builds {@link ApplicationEntity} for user consent payloads, including URL-shaped (CIMD) clients
 * that are not persisted as domain applications.
 */
@RequiredArgsConstructor
public class ConsentApplicationEntityFactory {

    public static final String CIMD_CLIENT = "cimd-client";

    private final ApplicationService applicationService;
    private final CimdMetadataDocumentService cimdMetadataDocumentService;

    public Single<ApplicationEntity> resolve(String domainId, String clientId) {
        return applicationService.findByDomainAndClientId(domainId, clientId)
                .map(ApplicationEntity::new)
                .switchIfEmpty(Single.defer(() -> {
                    if (ClientIds.isUrlShaped(clientId)) {
                        return resolveCimdDocument(domainId, clientId);
                    }
                    return Single.just(new ApplicationEntity(ScopeApprovalAdapterImpl.UNKNOWN_ID, clientId, "unknown-client-name"));
                }));
    }

    private Single<ApplicationEntity> resolveCimdDocument(String domainId, String clientId) {
        String canonical = ClientIds.canonicalize(clientId);
        return cimdMetadataDocumentService.findByDomainAndClientId(domainId, canonical)
                .map(doc -> {
                    String displayName = doc.getClientName();
                    if (displayName == null) {
                        displayName = canonical;
                    }
                    return new ApplicationEntity(CIMD_CLIENT, clientId, displayName);
                })
                .switchIfEmpty(Maybe.just(new ApplicationEntity(CIMD_CLIENT, clientId, canonical)))
                .toSingle();
    }
}
