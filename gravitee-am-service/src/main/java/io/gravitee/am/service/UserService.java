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

import io.gravitee.am.model.User;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserService {

    Flowable<User> findByDomain(String domain);

    Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size);

    Single<Page<User>> findByDomain(String domain, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria, int page, int size);

    Flowable<User> findByIdIn(List<String> ids);

    Maybe<User> findByDomainAndUsername(String domain, String username);

    Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source);

    Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source);

    Single<User> findById(ReferenceType referenceType, String referenceId, String id);

    Maybe<User> findById(String id);

    Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source);

    Single<User> create(String domain, NewUser newUser);

    Single<User> create(ReferenceType referenceType, String referenceId, NewUser newUser);

    Single<User> create(User user);

    Single<User> update(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser);

    Single<User> update(String domain, String id, UpdateUser updateUser);

    Single<User> update(User user);

    Single<User> enhance(User user);

    Completable delete(String userId);

    Single<Long> countByDomain(String domain);

    Single<Long> countByApplication(String domain, String application);

    Single<Map<Object, Object>> statistics(AnalyticsQuery query);

}
