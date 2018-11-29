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
package io.gravitee.am.management.repository.proxy;

import io.gravitee.am.model.Client;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ClientRepositoryProxy extends AbstractProxy<ClientRepository> implements ClientRepository {

    @Override
    public Single<Set<Client>> findByDomain(String domain) {
        return target.findByDomain(domain);
    }

    @Override
    public Single<Page<Client>> findByDomain(String domain, int page, int size) {
        return target.findByDomain(domain, page, size);
    }

    @Override
    public Maybe<Client> findByClientIdAndDomain(String clientId, String domain) {
        return target.findByClientIdAndDomain(clientId, domain);
    }

    @Override
    public Single<Set<Client>> findByIdentityProvider(String identityProvider) {
        return target.findByIdentityProvider(identityProvider);
    }

    @Override
    public Single<Set<Client>> findByCertificate(String certificate) {
        return target.findByCertificate(certificate);
    }

    @Override
    public Single<Set<Client>> findByDomainAndExtensionGrant(String domain, String tokenGranter) {
        return target.findByDomainAndExtensionGrant(domain, tokenGranter);
    }

    @Override
    public Single<Set<Client>> findAll() {
        return target.findAll();
    }

    @Override
    public Single<Page<Client>> findAll(int page, int size) {
        return target.findAll(page, size);
    }

    @Override
    public Maybe<Client> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Single<Client> create(Client client) {
        return target.create(client);
    }

    @Override
    public Single<Client> update(Client client) {
        return target.update(client);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }

    @Override
    public Single<Long> countByDomain(String domain) {
        return target.countByDomain(domain);
    }

    @Override
    public Single<Long> count() {
        return target.count();
    }
}
