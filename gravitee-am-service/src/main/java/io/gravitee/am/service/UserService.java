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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.CommonUserRepository.UpdateActions;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserService extends CommonUserService {

    Flowable<User> findByDomain(String domain);

    Single<Page<User>> findByDomain(String domain, int page, int size);

    Maybe<User> findByDomainAndUsername(String domain, String username);

    Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source);

    Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source, boolean includeLinkedIdentities);

    Maybe<User> findById(String id);

    Single<User> create(String domain, NewUser newUser);

    Single<User> update(String domain, String id, UpdateUser updateUser);

    Single<Long> countByDomain(String domain);

    Single<Long> countByApplication(String domain, String application);

    Single<Map<Object, Object>> statistics(AnalyticsQuery query);

    Completable deleteByDomain(String domainId);

    Completable removeFactor(String userId, String factorId, io.gravitee.am.identityprovider.api.User principal);

    Single<User> update(User user, UpdateActions updateActions);
}
