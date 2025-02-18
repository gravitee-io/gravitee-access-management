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
package io.gravitee.am.dataplane.api.repository;

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.CommonUserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserRepository extends CommonUserRepository {

    Maybe<User> findById(UserId id);

    Flowable<User> findAll(ReferenceType referenceType, String referenceId);

    Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size);

    /**
     * Same implementation as findAll for SCIM request as for SCIM pagination the startingIndex value is used instead of the page number.
     *
     * @param referenceType
     * @param referenceId
     * @param startIndex index of the record from which the search as to start
     * @param count
     * @return
     */
    Single<Page<User>> findAllScim(ReferenceType referenceType, String referenceId, int startIndex, int count);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int page, int size);

    /**
     * Same implementation as search for SCIM request as for SCIM pagination the startingIndex value is used instead of the page number.
     *
     * @param referenceType
     * @param referenceId
     * @param criteria
     * @param startIndex index of the record from which the search as to start
     * @param count
     * @return
     */
    Single<Page<User>> searchScim(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int startIndex, int count);

    Flowable<User> findByDomainAndEmail(String domain, String email, boolean strict);

    Maybe<User> findByUsernameAndDomain(String domain, String username);

    Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source);

    Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source, boolean includeLinkedIdentities);

    Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source);

    Flowable<User> findByIdIn(List<String> ids);

    Maybe<User> findById(Reference reference, UserId userId);

    Single<Long> countByReference(ReferenceType referenceType, String referenceId);

    Single<Long> countByApplication(String domain, String application);

    Single<Map<Object, Object>> statistics(AnalyticsQuery query);
}
