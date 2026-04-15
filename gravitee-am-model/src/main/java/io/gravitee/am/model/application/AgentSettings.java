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

import io.gravitee.am.model.oidc.JWKSet;

import java.util.List;
import java.util.Map;

/**
 * Configuration block for Blueprint (agent identity) applications.
 * Stored on {@link ApplicationSettings} when {@code agentIdentityMode} is true.
 */
public class AgentSettings {

    private AgentType agentType;
    private List<String> allowedGrantTypes;
    private Integer tokenTtlSeconds;
    private boolean refreshTokenEnabled;
    private List<String> allowedScopes;
    private int maxPublicKeysPerWorkload = 10;
    private Map<String, String> requiredClaims;
    private String clientAssertionType;
    private JWKSet jwks;

    public AgentSettings() {
    }

    public AgentSettings(AgentSettings other) {
        this.agentType = other.agentType;
        this.allowedGrantTypes = other.allowedGrantTypes != null ? List.copyOf(other.allowedGrantTypes) : null;
        this.tokenTtlSeconds = other.tokenTtlSeconds;
        this.refreshTokenEnabled = other.refreshTokenEnabled;
        this.allowedScopes = other.allowedScopes != null ? List.copyOf(other.allowedScopes) : null;
        this.maxPublicKeysPerWorkload = other.maxPublicKeysPerWorkload;
        this.requiredClaims = other.requiredClaims != null ? Map.copyOf(other.requiredClaims) : null;
        this.clientAssertionType = other.clientAssertionType;
        if (other.jwks != null) {
            try {
                this.jwks = other.jwks.clone();
            } catch (CloneNotSupportedException e) {
                this.jwks = null;
            }
        }
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public void setAgentType(AgentType agentType) {
        this.agentType = agentType;
    }

    public List<String> getAllowedGrantTypes() {
        return allowedGrantTypes;
    }

    public void setAllowedGrantTypes(List<String> allowedGrantTypes) {
        this.allowedGrantTypes = allowedGrantTypes;
    }

    public Integer getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(Integer tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public boolean isRefreshTokenEnabled() {
        return refreshTokenEnabled;
    }

    public void setRefreshTokenEnabled(boolean refreshTokenEnabled) {
        this.refreshTokenEnabled = refreshTokenEnabled;
    }

    public List<String> getAllowedScopes() {
        return allowedScopes;
    }

    public void setAllowedScopes(List<String> allowedScopes) {
        this.allowedScopes = allowedScopes;
    }

    public int getMaxPublicKeysPerWorkload() {
        return maxPublicKeysPerWorkload;
    }

    public void setMaxPublicKeysPerWorkload(int maxPublicKeysPerWorkload) {
        this.maxPublicKeysPerWorkload = maxPublicKeysPerWorkload;
    }

    public Map<String, String> getRequiredClaims() {
        return requiredClaims;
    }

    public void setRequiredClaims(Map<String, String> requiredClaims) {
        this.requiredClaims = requiredClaims;
    }

    public String getClientAssertionType() {
        return clientAssertionType;
    }

    public void setClientAssertionType(String clientAssertionType) {
        this.clientAssertionType = clientAssertionType;
    }

    public JWKSet getJwks() {
        return jwks;
    }

    public void setJwks(JWKSet jwks) {
        this.jwks = jwks;
    }
}
