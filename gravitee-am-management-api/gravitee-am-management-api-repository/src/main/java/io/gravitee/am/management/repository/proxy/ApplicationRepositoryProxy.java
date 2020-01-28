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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ApplicationRepositoryProxy extends AbstractProxy<ApplicationRepository> implements ApplicationRepository {

    @Override
    public Single<List<Application>> findAll() {
        return target.findAll();
    }

    @Override
    public Single<Page<Application>> findAll(int page, int size) {
        return target.findAll(page, size);
    }

    @Override
    public Single<Page<Application>> findByDomain(String domain, int page, int size) {
        return target.findByDomain(domain, page, size);
    }

    @Override
    public Single<Page<Application>> search(String domain, String query, int page, int size) {
        return target.search(domain, query, page, size);
    }

    @Override
    public Single<Set<Application>> findByCertificate(String certificate) {
        return target.findByCertificate(certificate);
    }

    @Override
    public Single<Set<Application>> findByIdentityProvider(String identityProvider) {
        return target.findByIdentityProvider(identityProvider);
    }

    @Override
    public Single<Set<Application>> findByFactor(String factor) {
        return target.findByFactor(factor);
    }

    @Override
    public Single<Set<Application>> findByDomainAndExtensionGrant(String domain, String extensionGrant) {
        return target.findByDomainAndExtensionGrant(domain, extensionGrant);
    }

    @Override
    public Maybe<Application> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Single<Application> create(Application item) {
        return target.create(item);
    }

    @Override
    public Single<Application> update(Application item) {
        return target.update(item);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }

    @Override
    public Single<Long> count() {
        return target.count();
    }

    @Override
    public Single<Long> countByDomain(String domain) {
        return target.countByDomain(domain);
    }

    @Override
    public Maybe<Application> findByDomainAndClientId(String domain, String clientId) {
        return target.findByDomainAndClientId(domain, clientId);
    }
}
