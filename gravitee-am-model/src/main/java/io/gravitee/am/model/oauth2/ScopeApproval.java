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
package io.gravitee.am.model.oauth2;

import java.util.Date;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ScopeApproval {

    public enum ApprovalStatus {
        APPROVED,
        DENIED
    }

    private String id;

    private String transactionId;

    private String userId;

    private String clientId;

    private String domain;

    private String scope;

    private ApprovalStatus status;

    private Date expiresAt;

    private Date createdAt;

    private Date updatedAt;

    public ScopeApproval() {}

    public ScopeApproval(String transactionId, String userId, String clientId, String domain, String scope, ApprovalStatus status) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.clientId = clientId;
        this.domain = domain;
        this.scope = scope;
        this.status = status;
    }

    public ScopeApproval(String transactionId, String userId, String clientId, String domain, String scope, ApprovalStatus status, Date expiresAt) {
        this(transactionId, userId, clientId, domain, scope, status);
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScopeApproval that = (ScopeApproval) o;

        if (!userId.equals(that.userId)) return false;
        if (!clientId.equals(that.clientId)) return false;
        return scope.equals(that.scope);
    }

    @Override
    public int hashCode() {
        int result = userId.hashCode();
        result = 31 * result + clientId.hashCode();
        result = 31 * result + scope.hashCode();
        return result;
    }
}
