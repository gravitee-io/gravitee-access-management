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

import io.gravitee.am.model.User;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.user.UserReferenceType;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserRepository extends CrudRepository<User, String> {

    Single<Set<User>> findByReference(String referenceId, UserReferenceType referenceType);

    Single<Page<User>> findByReference(String referenceId, UserReferenceType referenceType, int page, int size);

    Single<Page<User>> search(String referenceId, UserReferenceType referenceType, String query, int page, int size);

    Single<List<User>> findByReferenceAndEmail(String referenceId, UserReferenceType referenceType, String email, boolean strict);

    Maybe<User> findByUsernameAndReference(String referenceId, UserReferenceType referenceType, String username);

    Maybe<User> findByReferenceAndUsernameAndSource(String referenceId, UserReferenceType referenceType, String username, String source);

    Maybe<User> findByReferenceAndExternalIdAndSource(String referenceId, UserReferenceType referenceType, String externalId, String source);

    Single<List<User>> findByIdIn(List<String> ids);

    Single<Long> countByReference(String referenceId, UserReferenceType referenceType);

    Single<Map<Object, Object>> statistics(AnalyticsQuery query);
}
