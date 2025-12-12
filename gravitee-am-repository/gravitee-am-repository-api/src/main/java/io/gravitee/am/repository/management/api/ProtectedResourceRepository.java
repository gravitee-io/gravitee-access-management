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

import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResource.Type;
import io.gravitee.am.model.ProtectedResourcePrimaryData;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.PageSortRequest;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

public interface ProtectedResourceRepository extends CrudRepository<ProtectedResource, String> {

    Maybe<ProtectedResource> findByDomainAndClient(String domainId, String clientId);

    Maybe<ProtectedResource> findByDomainAndId(String domainId, String id);

    Flowable<ProtectedResource> findAll();

    Flowable<ProtectedResource> findByDomain(String domain);

    Single<Page<ProtectedResourcePrimaryData>> findByDomainAndType(String domain, Type type, PageSortRequest pageSortRequest);

    Single<Page<ProtectedResourcePrimaryData>> findByDomainAndTypeAndIds(String domain, Type type, List<String> ids, PageSortRequest pageSortRequest);

    Single<Page<ProtectedResourcePrimaryData>> search(String domain, Type type, String query, PageSortRequest pageSortRequest);

    Single<Page<ProtectedResourcePrimaryData>> search(String domain, Type type, List<String> ids, String query, PageSortRequest pageSortRequest);

    Single<Boolean> existsByResourceIdentifiers(String domainId, List<String> resourceIdentifiers);

    /**
     * Checks if any resource (excluding the one with excludeId) has any of the given resource identifiers.
     * Used during updates to validate uniqueness while excluding the current resource.
     *
     * @param domainId the domain to search in
     * @param resourceIdentifiers the identifiers to check for
     * @param excludeId the resource ID to exclude from the check
     * @return true if another resource (not the excluded one) has any of these identifiers
     */
    Single<Boolean> existsByResourceIdentifiersExcludingId(String domainId, List<String> resourceIdentifiers, String excludeId);
}
