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

import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.service.model.NewResource;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ResourceService {

    Single<Page<Resource>> findByDomain(String domain, int page, int size);
    Single<Page<Resource>> findByDomainAndClient(String domain, String client, int page, int size);
    Flowable<Resource> findByResources(List<String> resourceIds);
    Flowable<Resource> listByDomainAndClientAndUser(String domain, String client, String userId);
    Flowable<Resource> findByDomainAndClientAndResources(String domain, String client, List<String> resourceIds);
    Maybe<Resource> findByDomainAndClientAndUserAndResource(String domain, String client, String userId, String resourceId);
    Maybe<Resource> findByDomainAndClientResource(String domain, String client, String resourceId);
    Single<Map<String, Map<String, Object>>> getMetadata(List<Resource> resources);
    Single<Resource> create(NewResource newResource, String domain, String client, String userId);
    Single<Resource> update(NewResource newResource, String domain, String client, String userId, String resourceId);
    Single<Resource> update(Resource resource);
    Completable delete(String domain, String client, String userId, String resourceId);
    Completable delete(Resource resource);
    Flowable<AccessPolicy> findAccessPolicies(String domain, String client, String user, String resource);
    Flowable<AccessPolicy> findAccessPoliciesByResources(List<String> resourceIds);
    Single<Long> countAccessPolicyByResource(String resourceId);
    Maybe<AccessPolicy> findAccessPolicy(String domain, String client, String user, String resource, String accessPolicy);
    Maybe<AccessPolicy> findAccessPolicy(String accessPolicy);
    Single<AccessPolicy> createAccessPolicy(AccessPolicy accessPolicy, String domain, String client, String user, String resource);
    Single<AccessPolicy> updateAccessPolicy(AccessPolicy accessPolicy, String domain, String client, String user, String resource, String accessPolicyId);
    Completable deleteAccessPolicy(String domain, String client, String user, String resource, String accessPolicy);

    default Single<Set<Resource>> findByDomain(String domain) {
        return findByDomain(domain, 0, Integer.MAX_VALUE)
                .map(pagedResources -> (pagedResources.getData() == null) ? Collections.emptySet() : new HashSet<>(pagedResources.getData()));
    }
}
