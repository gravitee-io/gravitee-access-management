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
import io.gravitee.am.model.AuthorizationPolicy;
import io.gravitee.am.model.AuthorizationPolicyVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.model.NewAuthorizationPolicy;
import io.gravitee.am.service.model.UpdateAuthorizationPolicy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author GraviteeSource Team
 */
public interface AuthorizationPolicyService {

    Flowable<AuthorizationPolicy> findByDomain(String domainId);

    Maybe<AuthorizationPolicy> findById(String id);

    Maybe<AuthorizationPolicy> findByDomainAndId(String domainId, String id);

    Flowable<AuthorizationPolicy> findByDomainAndEngineType(String domainId, String engineType);

    Single<AuthorizationPolicy> create(Domain domain, NewAuthorizationPolicy request, User principal);

    Single<AuthorizationPolicy> update(Domain domain, String id, UpdateAuthorizationPolicy request, User principal);

    Completable delete(Domain domain, String id, User principal);

    Completable deleteByDomain(String domainId);

    // Versioning

    Flowable<AuthorizationPolicyVersion> getVersionHistory(String policyId);

    Maybe<AuthorizationPolicyVersion> getVersion(String policyId, int version);

    Single<AuthorizationPolicy> rollback(Domain domain, String policyId, int targetVersion, User principal);
}
