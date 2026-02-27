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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PolicySet;
import io.gravitee.am.model.PolicySetVersion;
import io.gravitee.am.service.model.NewPolicySet;
import io.gravitee.am.service.model.UpdatePolicySet;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author GraviteeSource Team
 */
public interface PolicySetService {

    Flowable<PolicySet> findByDomain(String domainId);

    Maybe<PolicySet> findById(String id);

    Maybe<PolicySet> findByDomainAndId(String domainId, String id);

    Single<PolicySet> create(Domain domain, NewPolicySet request, User principal);

    Single<PolicySet> update(Domain domain, String id, UpdatePolicySet request, User principal);

    Completable delete(Domain domain, String id, User principal);

    Completable deleteByDomain(String domainId);

    Flowable<PolicySetVersion> getVersions(String policySetId);

    Maybe<PolicySetVersion> getVersion(String policySetId, int version);

    Single<PolicySet> restoreVersion(Domain domain, String id, int version, User principal);
}
