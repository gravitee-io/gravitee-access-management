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

    /**
     * Look up a binding by its agent-side identity — the natural key for the PS token endpoint,
     * which knows {@code agent_server_url} (from the verified agent_token's {@code iss} claim)
     * and {@code agent_identifier} (from the resource_token's {@code agent} claim) but does not
     * yet know which user the agent represents. The binding row provides that user id, which
     * the token endpoint then uses to short-circuit to 200 + auth_token when prior consent
     * exists.
     */
    Maybe<AAuthBootstrapBinding> findByDomainAndAgentServerUrlAndAgentIdentifier(String domain, String agentServerUrl, String agentIdentifier);

    Single<AAuthBootstrapBinding> create(AAuthBootstrapBinding binding);

    Single<AAuthBootstrapBinding> update(AAuthBootstrapBinding binding);

    Completable delete(String id);
}
