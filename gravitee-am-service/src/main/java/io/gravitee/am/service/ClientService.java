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
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.model.NewClient;
import io.gravitee.am.service.model.PatchClient;
import io.gravitee.am.service.model.TopClient;
import io.gravitee.am.service.model.TotalClient;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.Set;

/**
 * NOTE : this service must only be used in an OpenID Connect context
 * Use the {@link io.gravitee.am.service.ApplicationService} for management purpose
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface ClientService {

    Single<Set<Client>> findAll();

    Single<Page<Client>> findAll(int page, int size);

    @Deprecated
    Single<Set<Client>> search(String domain, String query);

    @Deprecated
    Single<Page<Client>> search(String domain, String query, int page, int size);

    Single<Set<Client>> findByDomain(String domain);

    Single<Page<Client>> findByDomain(String domain, int page, int size);

    @Deprecated
    Single<Set<TopClient>> findTopClients();

    @Deprecated
    Single<Set<TopClient>> findTopClientsByDomain(String domain);

    @Deprecated
    Single<TotalClient> findTotalClients();

    @Deprecated
    Single<TotalClient> findTotalClientsByDomain(String domain);

    Maybe<Client> findByDomainAndClientId(String domain, String clientId);

    Maybe<Client> findById(String id);

    @Deprecated
    Single<Client> create(String domain, NewClient newClient, User principal);

    Single<Client> create(Client client);

    @Deprecated
    Single<Client> patch(String domain, String id, PatchClient patchClient, boolean forceNull, User principal);

    Single<Client> renewClientSecret(String domain, String id, User principal);

    Completable delete(String clientId, User principal);

    Single<Client> update(Client client);

    @Deprecated
    default Single<Client> create(String domain, NewClient newClient) {
        return create(domain, newClient, null);
    }

    @Deprecated
    default Single<Client> patch(String domain, String id, PatchClient patchClient) {
        return patch(domain, id, patchClient, false, null);
    }
    @Deprecated
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
