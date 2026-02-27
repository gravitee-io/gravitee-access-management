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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.AuthorizationSchema;
import io.gravitee.am.model.AuthorizationSchemaVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.model.NewAuthorizationSchema;
import io.gravitee.am.service.model.UpdateAuthorizationSchema;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author GraviteeSource Team
 */
public interface AuthorizationSchemaService {

    Flowable<AuthorizationSchema> findByDomain(String domainId);

    Maybe<AuthorizationSchema> findById(String id);

    Maybe<AuthorizationSchema> findByDomainAndId(String domainId, String id);

    Single<AuthorizationSchema> create(Domain domain, NewAuthorizationSchema request, User principal);

    Single<AuthorizationSchema> update(Domain domain, String id, UpdateAuthorizationSchema request, User principal);

    Completable delete(Domain domain, String id, User principal);

    Completable deleteByDomain(String domainId);

    Flowable<AuthorizationSchemaVersion> getVersions(String schemaId);

    Maybe<AuthorizationSchemaVersion> getVersion(String schemaId, int version);

    Single<AuthorizationSchema> restoreVersion(Domain domain, String id, int version, User principal);
}
