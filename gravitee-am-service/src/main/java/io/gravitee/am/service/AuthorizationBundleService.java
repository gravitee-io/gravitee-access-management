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
import io.gravitee.am.model.AuthorizationBundle;
import io.gravitee.am.model.AuthorizationBundleVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.model.NewAuthorizationBundle;
import io.gravitee.am.service.model.UpdateAuthorizationBundle;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author GraviteeSource Team
 */
public interface AuthorizationBundleService {

    Flowable<AuthorizationBundle> findByDomain(String domainId);

    Maybe<AuthorizationBundle> findById(String id);

    Maybe<AuthorizationBundle> findByDomainAndId(String domainId, String id);

    Single<AuthorizationBundle> create(Domain domain, NewAuthorizationBundle request, User principal);

    Single<AuthorizationBundle> update(Domain domain, String id, UpdateAuthorizationBundle request, User principal);

    Completable delete(Domain domain, String id, User principal);

    Completable deleteByDomain(String domainId);

    // Versioning

    Flowable<AuthorizationBundleVersion> getVersionHistory(String bundleId);

    Maybe<AuthorizationBundleVersion> getVersion(String bundleId, int version);

    Single<AuthorizationBundle> rollback(Domain domain, String bundleId, int targetVersion, User principal);
}
