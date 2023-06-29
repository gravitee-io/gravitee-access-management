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

import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static io.reactivex.rxjava3.core.Observable.fromIterable;
import static java.util.Optional.ofNullable;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class ClientSyncServiceImpl implements ClientSyncService {

    @Autowired
    private Domain domain;

    @Autowired
    private ClientManager clientManager;

    @Override
    public Maybe<Client> findById(String id) {
        return ofNullable(clientManager.get(id)).map(Maybe::just).orElseGet(Maybe::empty);
    }


    @Override
    public Maybe<Client> findByClientId(String clientId) {
        return findByDomainAndClientId(domain.getId(), clientId);
    }

    @Override
    public Maybe<Client> findByDomainAndClientId(String domain, String clientId) {
        return fromIterable(clientManager.entities())
                .filter(client -> !client.isTemplate())
                .filter(client -> client.getClientId().equals(clientId) && client.getDomain().equals(domain))
                .firstElement();
    }

    @Override
    public Maybe<Client> findByEntityId(String entityId) {
        return findByDomainAndEntityId(domain.getId(), entityId);
    }

    @Override
    public Maybe<Client> findByDomainAndEntityId(String domain, String entityId) {
        return fromIterable(clientManager.entities())
                .filter(client -> !client.isTemplate())
                .filter(client -> domain.equals(client.getDomain()) && entityId.equals(client.getEntityId()))
                .firstElement();
    }

    @Override
    public Single<List<Client>> findTemplates() {
        return fromIterable(clientManager.entities())
                .filter(client -> client.isTemplate() && client.getDomain().equals(domain.getId()))
                .toList();
    }

    @Override
    public Client addDynamicClientRegistred(Client client) {
        clientManager.deploy(client);
        return client;
    }

    @Override
    public Client removeDynamicClientRegistred(Client client) {
        clientManager.undeploy(client.getId());
        return client;
    }
}
