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
package io.gravitee.am.model.application;

import java.util.Map;

/**
 * Configuration block for Blueprint (agent identity) applications.
 * Stored on {@link ApplicationSettings} when {@code agentIdentityMode} is true.
 * <p>
 * Only fields unique to the agent persona live here. Grant types, token TTLs,
 * scopes, JWKS and client-authentication method are configured on
 * {@link ApplicationOAuthSettings} — the same surfaces used by every other
 * OIDC application.
 */
public class AgentSettings {

    private AgentType agentType;
    private Map<String, String> requiredClaims;

    public AgentSettings() {
    }

    public AgentSettings(AgentSettings other) {
        this.agentType = other.agentType;
        this.requiredClaims = other.requiredClaims != null ? Map.copyOf(other.requiredClaims) : null;
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public void setAgentType(AgentType agentType) {
        this.agentType = agentType;
    }

    public Map<String, String> getRequiredClaims() {
        return requiredClaims;
    }

    public void setRequiredClaims(Map<String, String> requiredClaims) {
        this.requiredClaims = requiredClaims;
    }
}
