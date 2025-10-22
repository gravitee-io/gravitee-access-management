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

package io.gravitee.am.gateway.handler.common.protectedresource.impl;

import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.beans.factory.annotation.Autowired;

import static io.reactivex.rxjava3.core.Observable.fromIterable;

public class ProtectedResourceSyncServiceImpl implements ProtectedResourceSyncService {

    @Autowired
    private Domain domain;

    @Autowired
    private ProtectedResourceManager protectedResourceManager;

    @Override
    public Maybe<Client> findByClientId(String clientId) {
        return findByDomainAndClientId(domain.getId(), clientId);
    }

    @Override
    public Maybe<Client> findByDomainAndClientId(String domainId, String clientId) {
        return fromIterable(protectedResourceManager.entities())
                .filter(protectedResource -> protectedResource.getClientId().equals(clientId) && protectedResource.getDomainId().equals(domainId))
                .map(protectedResource -> protectedResource.toClient())
                .firstElement();
    }
}
