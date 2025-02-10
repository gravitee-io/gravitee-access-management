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

package io.gravitee.am.management.service.dataplane;


import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UMAResourceManagementService {
    Flowable<AccessPolicy> findAccessPoliciesByResources(Domain domain, List<String> resourceIds);
    Maybe<AccessPolicy> findAccessPolicy(Domain domain, String accessPolicy);
    Maybe<Resource> findByDomainAndClientResource(Domain domain, String client, String resourceId);
    Single<Page<Resource>> findByDomainAndClient(Domain domain, String client, int page, int size);
    Single<Long> countAccessPolicyByResource(Domain domain,String resourceId);
    Single<Map<String, Map<String, Object>>> getMetadata(Domain domain, List<Resource> resources);
    Flowable<Resource> findByDomain(Domain domain);
    Completable delete(Domain domain, Resource resource);
}
