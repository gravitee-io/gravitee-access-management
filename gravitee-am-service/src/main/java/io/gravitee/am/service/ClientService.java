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
package io.gravitee.am.service;

import io.gravitee.am.model.Client;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.model.NewClient;
import io.gravitee.am.service.model.TopClient;
import io.gravitee.am.service.model.TotalClient;
import io.gravitee.am.service.model.UpdateClient;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientService {

    Maybe<Client> findById(String id);

    Maybe<Client> findByDomainAndClientId(String domain, String clientId);

    Single<Page<Client>> findByDomain(String domain, int page, int size);

    Single<Set<Client>> findByDomain(String domain);

    Single<Client> create(String domain, NewClient newClient);

    Single<Client> update(String domain, String id, UpdateClient updateClient);

    Single<Set<Client>> findByIdentityProvider(String identityProvider);

    Single<Set<Client>> findByCertificate(String certificate);

    Single<Set<Client>> findByExtensionGrant(String tokenGranter);

    Single<Set<Client>> findAll();

    Single<Page<Client>> findAll(int page, int size);

    Single<Set<TopClient>> findTopClients();

    Single<Set<TopClient>> findTopClientsByDomain(String domain);

    Single<TotalClient> findTotalClientsByDomain(String domain);

    Single<TotalClient> findTotalClients();

    Completable delete(String clientId);
}
