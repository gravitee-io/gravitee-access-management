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
 * AAUTH pending request entity. Persisted during the deferred authorization flow
 * (202 Accepted → user consent → agent polls for auth token).
 * Follows the same persistence pattern as {@link CibaAuthRequest}.
 *
 * @author GraviteeSource Team
 */
public class AAuthPendingRequest {

    private String id;
    private String status;
    private String domain;
    private String agentId;
    private String agentSub;
    private String agentJkt;
    private String agentPublicKey;
    private String applicationId;
    private String resourceIss;
    private String scope;
    private String justification;
    private String interactionCode;
    private String psIssuerUrl;
    private String authToken;
    private long authTokenExpiresIn;
    private String userId;
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

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentSub() {
        return agentSub;
    }

    public void setAgentSub(String agentSub) {
        this.agentSub = agentSub;
    }

    public String getAgentJkt() {
        return agentJkt;
    }

    public void setAgentJkt(String agentJkt) {
        this.agentJkt = agentJkt;
    }

    public String getAgentPublicKey() {
        return agentPublicKey;
    }

    public void setAgentPublicKey(String agentPublicKey) {
        this.agentPublicKey = agentPublicKey;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getResourceIss() {
        return resourceIss;
    }

    public void setResourceIss(String resourceIss) {
        this.resourceIss = resourceIss;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public String getInteractionCode() {
        return interactionCode;
    }

    public void setInteractionCode(String interactionCode) {
        this.interactionCode = interactionCode;
    }

    public String getPsIssuerUrl() {
        return psIssuerUrl;
    }

    public void setPsIssuerUrl(String psIssuerUrl) {
        this.psIssuerUrl = psIssuerUrl;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public long getAuthTokenExpiresIn() {
        return authTokenExpiresIn;
    }

    public void setAuthTokenExpiresIn(long authTokenExpiresIn) {
        this.authTokenExpiresIn = authTokenExpiresIn;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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
