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
package io.gravitee.am.repository.oidc.api;

import io.gravitee.am.repository.oidc.model.AAuthBootstrapBinding;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Repository for AAUTH bootstrap bindings (permanent user-agent relationships).
 *
 * @author GraviteeSource Team
 */
public interface AAuthBootstrapBindingRepository {

    Maybe<AAuthBootstrapBinding> findById(String id);

    Flowable<AAuthBootstrapBinding> findByDomainAndUserId(String domain, String userId);

    Maybe<AAuthBootstrapBinding> findByDomainAndAgentServerUrlAndUserId(String domain, String agentServerUrl, String userId);

    Single<AAuthBootstrapBinding> create(AAuthBootstrapBinding binding);

    Single<AAuthBootstrapBinding> update(AAuthBootstrapBinding binding);

    Completable delete(String id);
}
