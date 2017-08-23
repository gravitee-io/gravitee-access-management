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
package io.gravitee.am.gateway.service;

import io.gravitee.am.gateway.service.model.NewClient;
import io.gravitee.am.gateway.service.model.TopClient;
import io.gravitee.am.gateway.service.model.TotalClient;
import io.gravitee.am.gateway.service.model.UpdateClient;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.common.Page;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientService {

    Client findById(String id);

    Client findByDomainAndClientId(String domain, String clientId);

    Page<Client> findByDomain(String domain, int page, int size);

    Set<Client> findByDomain(String domain);

    Client create(String domain, NewClient newClient);

    Client update(String domain, String id, UpdateClient updateClient);

    Set<Client> findByIdentityProvider(String identityProvider);

    Set<Client> findByCertificate(String certificate);

    Set<Client> findAll();

    Page<Client> findAll(int page, int size);

    Set<TopClient> findTopClients();

    Set<TopClient> findTopClientsByDomain(String domain);

    TotalClient findTotalClientsByDomain(String domain);

    TotalClient findTotalClients();

    void delete(String clientId);
}
