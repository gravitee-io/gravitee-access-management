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
 * JDBC representation of an AAUTH bootstrap request.
 *
 * @author GraviteeSource Team
 */
@Table("aauth_bootstrap_requests")
public class JdbcAAuthBootstrapRequest {

    @Id
    private String id;
    private String status;
    private String domain;
    @Column("agent_server_url")
    private String agentServerUrl;
    @Column("agent_server_name")
    private String agentServerName;
    @Column("agent_server_logo_uri")
    private String agentServerLogoUri;
    @Column("ephemeral_key_jwk")
    private String ephemeralKeyJwk;
    @Column("ephemeral_key_thumbprint")
    private String ephemeralKeyThumbprint;
    @Column("interaction_code")
    private String interactionCode;
    @Column("bootstrap_token")
    private String bootstrapToken;
    @Column("user_id")
    private String userId;
    @Column("pairwise_sub")
    private String pairwiseSub;
    @Column("domain_hint")
    private String domainHint;
    @Column("login_hint")
    private String loginHint;
    private String tenant;
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

    public String getAgentServerUrl() { return agentServerUrl; }
    public void setAgentServerUrl(String agentServerUrl) { this.agentServerUrl = agentServerUrl; }

    public String getAgentServerName() { return agentServerName; }
    public void setAgentServerName(String agentServerName) { this.agentServerName = agentServerName; }

    public String getAgentServerLogoUri() { return agentServerLogoUri; }
    public void setAgentServerLogoUri(String agentServerLogoUri) { this.agentServerLogoUri = agentServerLogoUri; }

    public String getEphemeralKeyJwk() { return ephemeralKeyJwk; }
    public void setEphemeralKeyJwk(String ephemeralKeyJwk) { this.ephemeralKeyJwk = ephemeralKeyJwk; }

    public String getEphemeralKeyThumbprint() { return ephemeralKeyThumbprint; }
    public void setEphemeralKeyThumbprint(String ephemeralKeyThumbprint) { this.ephemeralKeyThumbprint = ephemeralKeyThumbprint; }

    public String getInteractionCode() { return interactionCode; }
    public void setInteractionCode(String interactionCode) { this.interactionCode = interactionCode; }

    public String getBootstrapToken() { return bootstrapToken; }
    public void setBootstrapToken(String bootstrapToken) { this.bootstrapToken = bootstrapToken; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPairwiseSub() { return pairwiseSub; }
    public void setPairwiseSub(String pairwiseSub) { this.pairwiseSub = pairwiseSub; }

    public String getDomainHint() { return domainHint; }
    public void setDomainHint(String domainHint) { this.domainHint = domainHint; }

    public String getLoginHint() { return loginHint; }
    public void setLoginHint(String loginHint) { this.loginHint = loginHint; }

    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastAccessAt() { return lastAccessAt; }
    public void setLastAccessAt(LocalDateTime lastAccessAt) { this.lastAccessAt = lastAccessAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
}
