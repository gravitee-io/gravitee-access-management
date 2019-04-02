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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.model.*;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface ClientService {

    Single<Set<Client>> findAll();

    Single<Page<Client>> findAll(int page, int size);

    Single<Set<Client>> search(String domain, String query);

    Single<Set<Client>> findByDomain(String domain);

    Single<Page<Client>> findByDomain(String domain, int page, int size);

    Single<Set<Client>> findByDomainAndExtensionGrant(String domain, String tokenGranter);

    Single<Set<Client>> findByCertificate(String certificate);

    Single<Set<Client>> findByIdentityProvider(String identityProvider);

    Single<Set<TopClient>> findTopClients();

    Single<Set<TopClient>> findTopClientsByDomain(String domain);

    Single<TotalClient> findTotalClients();

    Single<TotalClient> findTotalClientsByDomain(String domain);

    Maybe<Client> findByDomainAndClientId(String domain, String clientId);

    Maybe<Client> findById(String id);

    Single<Client> create(String domain, NewClient newClient, User principal);

    Single<Client> create(Client client);

    Single<Client> patch(String domain, String id, PatchClient patchClient, boolean forceNull, User principal);

    Single<Client> renewClientSecret(String domain, String id, User principal);

    Completable delete(String clientId, User principal);

    Single<Client> update(Client client);

    default Single<Client> create(String domain, NewClient newClient) {
        return create(domain, newClient, null);
    }

    default Single<Client> patch(String domain, String id, PatchClient patchClient) {
        return patch(domain, id, patchClient, false, null);
    }

    default Single<Client> patch(String domain, String id, PatchClient patchClient, boolean forceNull) {
        return patch(domain, id, patchClient, forceNull, null);
    }

    default Single<Client> renewClientSecret(String domain, String id) {
        return renewClientSecret(domain, id, null);
    }

    default Completable delete(String clientId) {
        return delete(clientId, null);
    }

}
