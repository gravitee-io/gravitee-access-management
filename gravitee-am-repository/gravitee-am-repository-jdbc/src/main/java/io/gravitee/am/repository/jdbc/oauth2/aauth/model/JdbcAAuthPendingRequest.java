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
package io.gravitee.am.repository.jdbc.oauth2.aauth.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * JDBC representation of an AAUTH pending request.
 *
 * @author GraviteeSource Team
 */
@Table("aauth_pending_requests")
public class JdbcAAuthPendingRequest {

    @Id
    private String id;
    private String status;
    private String domain;
    @Column("agent_id")
    private String agentId;
    @Column("agent_sub")
    private String agentSub;
    @Column("agent_jkt")
    private String agentJkt;
    @Column("agent_public_key")
    private String agentPublicKey;
    @Column("application_id")
    private String applicationId;
    @Column("resource_iss")
    private String resourceIss;
    private String scope;
    private String justification;
    @Column("login_hint")
    private String loginHint;
    @Column("domain_hint")
    private String domainHint;
    private String tenant;
    @Column("interaction_code")
    private String interactionCode;
    @Column("ps_issuer_url")
    private String psIssuerUrl;
    @Column("auth_token")
    private String authToken;
    @Column("auth_token_expires_in")
    private long authTokenExpiresIn;
    @Column("user_id")
    private String userId;

    private String clarification;

    @Column("clarification_response")
    private String clarificationResponse;

    @Column("clarification_supported")
    private boolean clarificationSupported;

    @Column("clarification_round_count")
    private int clarificationRoundCount;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("last_access_at")
    private LocalDateTime lastAccessAt;
    @Column("expire_at")
    private LocalDateTime expireAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAgentSub() { return agentSub; }
    public void setAgentSub(String agentSub) { this.agentSub = agentSub; }

    public String getAgentJkt() { return agentJkt; }
    public void setAgentJkt(String agentJkt) { this.agentJkt = agentJkt; }

    public String getAgentPublicKey() { return agentPublicKey; }
    public void setAgentPublicKey(String agentPublicKey) { this.agentPublicKey = agentPublicKey; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getResourceIss() { return resourceIss; }
    public void setResourceIss(String resourceIss) { this.resourceIss = resourceIss; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    public String getLoginHint() { return loginHint; }
    public void setLoginHint(String loginHint) { this.loginHint = loginHint; }

    public String getDomainHint() { return domainHint; }
    public void setDomainHint(String domainHint) { this.domainHint = domainHint; }

    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }

    public String getInteractionCode() { return interactionCode; }
    public void setInteractionCode(String interactionCode) { this.interactionCode = interactionCode; }

    public String getPsIssuerUrl() { return psIssuerUrl; }
    public void setPsIssuerUrl(String psIssuerUrl) { this.psIssuerUrl = psIssuerUrl; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public long getAuthTokenExpiresIn() { return authTokenExpiresIn; }
    public void setAuthTokenExpiresIn(long authTokenExpiresIn) { this.authTokenExpiresIn = authTokenExpiresIn; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getClarification() { return clarification; }
    public void setClarification(String clarification) { this.clarification = clarification; }

    public String getClarificationResponse() { return clarificationResponse; }
    public void setClarificationResponse(String clarificationResponse) { this.clarificationResponse = clarificationResponse; }

    public boolean isClarificationSupported() { return clarificationSupported; }
    public void setClarificationSupported(boolean clarificationSupported) { this.clarificationSupported = clarificationSupported; }

    public int getClarificationRoundCount() { return clarificationRoundCount; }
    public void setClarificationRoundCount(int clarificationRoundCount) { this.clarificationRoundCount = clarificationRoundCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastAccessAt() { return lastAccessAt; }
    public void setLastAccessAt(LocalDateTime lastAccessAt) { this.lastAccessAt = lastAccessAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
}
