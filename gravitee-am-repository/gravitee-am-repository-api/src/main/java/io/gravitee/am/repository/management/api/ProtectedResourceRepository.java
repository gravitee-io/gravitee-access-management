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

    Flowable<ProtectedResource> findAll();

    Flowable<ProtectedResource> findByDomain(String domain);

    Single<Page<ProtectedResourcePrimaryData>> findByDomainAndType(String domain, Type type, PageSortRequest pageSortRequest);

    Single<Page<ProtectedResourcePrimaryData>> findByDomainAndTypeAndIds(String domain, Type type, List<String> ids, PageSortRequest pageSortRequest);

    Single<Boolean> existsByResourceIdentifiers(String domainId, List<String> resourceIdentifiers);
}
