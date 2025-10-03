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
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.model.NewApplication;
import io.gravitee.am.service.model.PatchApplication;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ApplicationService {

    Flowable<Application> findAll();

    Single<Page<Application>> findAll(int page, int size);

    Single<Page<Application>> findByDomain(String domain, int page, int size);

    Single<Page<Application>> findByDomain(String domain, List<String> applicationIds, int page, int size);

    Single<Page<Application>> search(String domain, String query, int page, int size);

    Single<Page<Application>> search(String domain, List<String> applicationIds, String query, int page, int size);

    Flowable<Application> findByCertificate(String certificate);

    Flowable<Application> findByIdentityProvider(String identityProvider);

    Flowable<Application> findByFactor(String factor);

    Single<Set<Application>> findByDomainAndExtensionGrant(String domain, String extensionGrant);

    Flowable<Application> findByIdIn(List<String> ids);

    Maybe<Application> findById(String id);

    Maybe<Application> findByDomainAndClientId(String domain, String clientId);

    Single<Application> create(Domain domain, NewApplication newApplication, User principal);

    Single<Application> create(Domain domain, Application application);

    Single<Application> update(Application application);

    Single<Application> updateType(String domain, String id, ApplicationType type, User principal);

    Single<Application> patch(Domain domain, String id, PatchApplication patchApplication, User principal, BiFunction<Domain, Application, Completable> revokeTokenProcessor);

    Completable delete(String id, User principal, Domain domain);

    Single<Long> count();

    Single<Long> countByDomain(String domainId);

    default Single<Set<Application>> fetchAll() {
        return findAll(0, Integer.MAX_VALUE)
                .map(pagedApplications -> (pagedApplications.getData() == null) ? Collections.emptySet() : new HashSet<>(pagedApplications.getData()));
    }

    default Single<Set<Application>> findByDomain(String domain) {
        return findByDomain(domain, 0, Integer.MAX_VALUE)
                .map(pagedApplications -> (pagedApplications.getData() == null) ? Collections.emptySet() : new HashSet<>(pagedApplications.getData()));
    }

    default Single<Application> create(Domain domain, NewApplication newApplication) {
        return create(domain, newApplication, null);
    }

    default Completable delete(String id, Domain domain) {
        return delete(id, null, domain);
    }

    /**
     * Fetch agent card information from the specified URL
     * @param agentCardUrl The URL to fetch agent card information from
     * @return Single containing the agent card information
     */
    Single<Object> fetchAgentCard(String agentCardUrl);
}
