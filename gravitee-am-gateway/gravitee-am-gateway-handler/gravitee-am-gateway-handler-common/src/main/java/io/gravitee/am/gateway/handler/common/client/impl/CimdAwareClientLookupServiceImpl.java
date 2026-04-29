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
package io.gravitee.am.gateway.handler.common.client.impl;

import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataService;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Maybe;

public class CimdAwareClientLookupServiceImpl implements ClientLookupService {

    private final DefaultClientLookupServiceImpl defaultClientLookupService;
    private final ClientSyncService clientSyncService;
    private final CimdMetadataService cimdMetadataService;
    private final String templateId;

    public CimdAwareClientLookupServiceImpl(
            ClientSyncService clientSyncService,
            ProtectedResourceSyncService protectedResourceSyncService,
            CimdMetadataService cimdMetadataService,
            String templateId
    ) {
        this.defaultClientLookupService = new DefaultClientLookupServiceImpl(clientSyncService, protectedResourceSyncService);
        this.cimdMetadataService = cimdMetadataService;
        this.clientSyncService = clientSyncService;
        this.templateId = templateId;
    }

    public CimdAwareClientLookupServiceImpl(
            ClientSyncService clientSyncService,
            CimdMetadataService cimdMetadataService,
            String templateId
    ) {
        this.defaultClientLookupService = new DefaultClientLookupServiceImpl(clientSyncService);
        this.cimdMetadataService = cimdMetadataService;
        this.clientSyncService = clientSyncService;
        this.templateId = templateId;
    }

    @Override
    public Maybe<Client> findById(String id) {
        // id can only be an application id, so we can just use the default lookup service
        return defaultClientLookupService.findById(id);
    }

    @Override
    public Maybe<Client> findByClientId(String clientId) {
        return defaultClientLookupService.findByClientId(clientId)
                .switchIfEmpty(resolveFromCimd(clientId));
    }

    @Override
    public Maybe<Client> findByDomainAndClientId(String domain, String clientId) {
        return defaultClientLookupService.findByDomainAndClientId(domain, clientId)
                .switchIfEmpty(resolveFromCimd(clientId));
    }

    private Maybe<Client> resolveFromCimd(String clientId) {
        return Maybe.defer(() -> clientSyncService.findById(templateId)
                .switchIfEmpty(Maybe.error(new InvalidClientMetadataException("No template found for templateId " + templateId)))
                .flatMap(templateClient -> cimdMetadataService.resolveClient(clientId, templateClient)));
    }

}
