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
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ApplicationRepository extends CrudRepository<Application, String> {

    Flowable<Application> findAll();

    Single<Page<Application>> findAll(int page, int size);

    Flowable<Application> findByDomain(String domain);

    Single<Page<Application>> findByDomain(String domain, int page, int size);

    Single<Page<Application>> findByDomain(String domain, List<String> applicationIds, int page, int size);

    Single<Page<Application>> search(String domain, String query, int page, int size);

    Single<Page<Application>> search(String domain, List<String> applicationIds, String query, int page, int size);

    Single<Page<Application>> findByDomain(String domain, int page, int size, List<ApplicationType> types);

    Single<Page<Application>> findByDomain(String domain, List<String> applicationIds, int page, int size, List<ApplicationType> types);

    Single<Page<Application>> search(String domain, String query, int page, int size, List<ApplicationType> types);

    Single<Page<Application>> search(String domain, List<String> applicationIds, String query, int page, int size, List<ApplicationType> types);

    Flowable<Application> findByCertificate(String certificate);

    Flowable<Application> findByIdentityProvider(String identityProvider);

    Flowable<Application> findByFactor(String factor);

    Flowable<Application> findByDomainAndExtensionGrant(String domain, String extensionGrant);

    Flowable<Application> findByIdIn(List<String> ids);

    Single<Long> count();

    Single<Long> countByDomain(String domain);

    Maybe<Application> findByDomainAndClientId(String domain, String clientId);

}
