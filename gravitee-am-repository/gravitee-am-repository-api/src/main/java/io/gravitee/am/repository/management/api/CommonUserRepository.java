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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CommonUserRepository extends CrudRepository<User, String> {

    Flowable<User> findAll(ReferenceType referenceType, String referenceId);

    Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int page, int size);
    Flowable<User> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria);

    Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source);

    Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source);

    Flowable<User> findByIdIn(List<String> ids);

    Maybe<User> findById(ReferenceType referenceType, String referenceId, String userId);

    Completable deleteByReference(ReferenceType referenceType, String referenceId);
}
