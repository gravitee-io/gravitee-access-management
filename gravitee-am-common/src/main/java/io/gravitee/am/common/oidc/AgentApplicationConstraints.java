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
package io.gravitee.am.common.oidc;

import io.gravitee.am.common.oauth2.GrantType;

import java.util.Set;

import static io.gravitee.am.common.oauth2.ResponseType.TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN;

/**
 * Shared constraints for agent application type.
 * Used by both the Management API (ApplicationAgentTemplate) and the DCR flow (DynamicClientRegistrationServiceImpl).
 *
 * @author GraviteeSource Team
 */
public final class AgentApplicationConstraints {

    private AgentApplicationConstraints() {}

    /**
     * Grant types that are forbidden for agent applications.
     * Agents must not use end-user-facing flows (implicit, password) or long-lived tokens (refresh_token).
     */
    public static final Set<String> FORBIDDEN_GRANT_TYPES = Set.of(
            GrantType.IMPLICIT, GrantType.PASSWORD, GrantType.REFRESH_TOKEN
    );

    /**
     * Implicit response types that are forbidden for agent applications.
     */
    public static final Set<String> FORBIDDEN_RESPONSE_TYPES = Set.of(
            TOKEN, ID_TOKEN, ID_TOKEN_TOKEN
    );
}
