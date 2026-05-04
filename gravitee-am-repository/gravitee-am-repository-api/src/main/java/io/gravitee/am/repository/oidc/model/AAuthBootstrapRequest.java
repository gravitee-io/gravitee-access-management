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
package io.gravitee.am.repository.oidc.model;

import java.util.Date;

/**
 * AAUTH bootstrap request entity. Persisted during the bootstrap flow where
 * an agent announces its ephemeral key and the user approves the binding.
 *
 * @author GraviteeSource Team
 */
public class AAuthBootstrapRequest {

    private String id;
    private String status;
    private String domain;
    private String agentServerUrl;
    private String agentServerName;
    private String agentServerLogoUri;
    private String ephemeralKeyJwk;
    private String ephemeralKeyThumbprint;
    private String interactionCode;
    private String bootstrapToken;
    private String userId;
    private String pairwiseSub;
    private String domainHint;
    private String loginHint;
    private String tenant;
    private Date createdAt;
    private Date lastAccessAt;
    private Date expireAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getAgentServerUrl() {
        return agentServerUrl;
    }

    public void setAgentServerUrl(String agentServerUrl) {
        this.agentServerUrl = agentServerUrl;
    }

    public String getAgentServerName() {
        return agentServerName;
    }

    public void setAgentServerName(String agentServerName) {
        this.agentServerName = agentServerName;
    }

    public String getAgentServerLogoUri() {
        return agentServerLogoUri;
    }

    public void setAgentServerLogoUri(String agentServerLogoUri) {
        this.agentServerLogoUri = agentServerLogoUri;
    }

    public String getEphemeralKeyJwk() {
        return ephemeralKeyJwk;
    }

    public void setEphemeralKeyJwk(String ephemeralKeyJwk) {
        this.ephemeralKeyJwk = ephemeralKeyJwk;
    }

    public String getEphemeralKeyThumbprint() {
        return ephemeralKeyThumbprint;
    }

    public void setEphemeralKeyThumbprint(String ephemeralKeyThumbprint) {
        this.ephemeralKeyThumbprint = ephemeralKeyThumbprint;
    }

    public String getInteractionCode() {
        return interactionCode;
    }

    public void setInteractionCode(String interactionCode) {
        this.interactionCode = interactionCode;
    }

    public String getBootstrapToken() {
        return bootstrapToken;
    }

    public void setBootstrapToken(String bootstrapToken) {
        this.bootstrapToken = bootstrapToken;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPairwiseSub() {
        return pairwiseSub;
    }

    public void setPairwiseSub(String pairwiseSub) {
        this.pairwiseSub = pairwiseSub;
    }

    public String getDomainHint() {
        return domainHint;
    }

    public void setDomainHint(String domainHint) {
        this.domainHint = domainHint;
    }

    public String getLoginHint() {
        return loginHint;
    }

    public void setLoginHint(String loginHint) {
        this.loginHint = loginHint;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(Date lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }

    public Date getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Date expireAt) {
        this.expireAt = expireAt;
    }
}
