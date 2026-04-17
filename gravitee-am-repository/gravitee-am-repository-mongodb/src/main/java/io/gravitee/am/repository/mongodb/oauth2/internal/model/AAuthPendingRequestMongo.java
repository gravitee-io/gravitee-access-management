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
package io.gravitee.am.repository.mongodb.oauth2.internal.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.Date;

/**
 * MongoDB representation of an AAUTH pending request.
 *
 * @author GraviteeSource Team
 */
public class AAuthPendingRequestMongo {

    @BsonId
    private String id;

    private String status;
    private String domain;

    @BsonProperty("agent_id")
    private String agentId;

    @BsonProperty("agent_sub")
    private String agentSub;

    @BsonProperty("agent_jkt")
    private String agentJkt;

    @BsonProperty("agent_public_key")
    private String agentPublicKey;

    @BsonProperty("application_id")
    private String applicationId;

    @BsonProperty("resource_iss")
    private String resourceIss;

    private String scope;
    private String justification;

    @BsonProperty("login_hint")
    private String loginHint;

    @BsonProperty("domain_hint")
    private String domainHint;

    private String tenant;

    @BsonProperty("interaction_code")
    private String interactionCode;

    @BsonProperty("ps_issuer_url")
    private String psIssuerUrl;

    @BsonProperty("auth_token")
    private String authToken;

    @BsonProperty("auth_token_expires_in")
    private long authTokenExpiresIn;

    @BsonProperty("user_id")
    private String userId;

    private String clarification;

    @BsonProperty("clarification_response")
    private String clarificationResponse;

    @BsonProperty("clarification_supported")
    private boolean clarificationSupported;

    @BsonProperty("clarification_round_count")
    private int clarificationRoundCount;

    @BsonProperty("created_at")
    private Date createdAt;

    @BsonProperty("last_access_at")
    private Date lastAccessAt;

    @BsonProperty("expire_at")
    private Date expireAt;

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

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getLastAccessAt() { return lastAccessAt; }
    public void setLastAccessAt(Date lastAccessAt) { this.lastAccessAt = lastAccessAt; }

    public Date getExpireAt() { return expireAt; }
    public void setExpireAt(Date expireAt) { this.expireAt = expireAt; }
}
