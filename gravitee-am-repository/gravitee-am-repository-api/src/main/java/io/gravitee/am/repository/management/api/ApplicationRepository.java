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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ApplicationRepository extends CrudRepository<Application, String> {

    Single<List<Application>> findAll();

    Single<Page<Application>> findAll(int page, int size);

    Single<List<Application>> findByDomain(String domain);

    Single<Page<Application>> findByDomain(String domain, int page, int size);

    Single<Page<Application>> search(String domain, String query, int page, int size);

    Single<Set<Application>> findByCertificate(String certificate);

    Single<Set<Application>> findByIdentityProvider(String identityProvider);

    Single<Set<Application>> findByFactor(String factor);

    Single<Set<Application>> findByDomainAndExtensionGrant(String domain, String extensionGrant);

    Single<Set<Application>> findByIdIn(List<String> ids);

    Single<Long> count();

    Single<Long> countByDomain(String domain);

    Maybe<Application> findByDomainAndClientId(String domain, String clientId);

}
