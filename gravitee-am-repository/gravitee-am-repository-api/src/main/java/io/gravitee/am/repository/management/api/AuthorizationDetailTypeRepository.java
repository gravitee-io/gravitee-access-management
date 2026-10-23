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

import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.exceptions.UniqueConstraintViolationException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author GraviteeSource Team
 */
public interface AuthorizationDetailTypeRepository extends CrudRepository<AuthorizationDetailType, String> {

    /**
     * Fails with {@link UniqueConstraintViolationException} when the domain already has the type, ignoring case.
     */
    @Override
    Single<AuthorizationDetailType> create(AuthorizationDetailType item);

    Flowable<AuthorizationDetailType> findByDomain(String domain);

    /**
     * Orders by {@code type}, ignoring case.
     */
    Single<Page<AuthorizationDetailType>> findByDomain(String domain, int page, int size);

    /**
     * Matches {@code type} ignoring case, where {@code *} stands for any characters and every other character is literal.
     * Orders by {@code type}, ignoring case.
     */
    Single<Page<AuthorizationDetailType>> search(String domain, String query, int page, int size);

    Maybe<AuthorizationDetailType> findByDomainAndId(String domain, String id);

    /**
     * Matches {@code type} ignoring case.
     */
    Maybe<AuthorizationDetailType> findByDomainAndType(String domain, String type);

    Completable deleteByDomain(String domain);
}
