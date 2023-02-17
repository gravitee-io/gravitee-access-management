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
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.PatchDomain;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DomainService {

    Flowable<Domain> findAllByEnvironment(String organizationId, String environment);

    Flowable<Domain> search(String organizationId, String environmentId, String query);

    Maybe<Domain> findById(String id);

    Single<Domain> findByHrid(String environmentId, String hrid);

    Single<List<Domain>> findAll();

    Flowable<Domain> findAllByCriteria(DomainCriteria criteria);

    Flowable<Domain> findByIdIn(Collection<String> ids);

    Single<Domain> create(String organizationId, String environmentId, NewDomain domain, User principal);

    Single<Domain> update(String domainId, Domain domain);

    Single<Domain> patch(String domainId, PatchDomain domain, User principal);

    Completable delete(String domain, User principal);

    default Single<Domain> create(String organizationId, String environmentId, NewDomain domain) {
        return create(organizationId, environmentId, domain, null);
    }

    default Single<Domain> patch(String domainId, PatchDomain domain) {
        return patch(domainId, domain, null);
    }

    default Completable delete(String domain) {
        return delete(domain, null);
    }

    String buildUrl(Domain domain, String path);
}
