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
package io.gravitee.am.model;

import java.util.Date;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */

public class RateLimit {
    private String id;
    private String userId;
    private String client;
    private String factorId;
    private long tokenLeft;
    private boolean allowRequest;
    private Date createdAt;
    private Date updatedAt;
    private String referenceId;
    private ReferenceType referenceType;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public String getFactorId() {
        return factorId;
    }

    public void setFactorId(String factorId) {
        this.factorId = factorId;
    }

    public long getTokenLeft() {
        return tokenLeft;
    }

    public void setTokenLeft(long tokenLeft) {
        this.tokenLeft = tokenLeft;
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

    public boolean isAllowRequest() {
        return allowRequest;
    }

    public void setAllowRequest(boolean allowRequest) {
        this.allowRequest = allowRequest;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    @Override
    public String toString() {
        return "RateLimit{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", client='" + client + '\'' +
                ", factorId='" + factorId + '\'' +
                ", tokenLeft=" + tokenLeft +
                ", allowRequest=" + allowRequest +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", referenceId='" + referenceId + '\'' +
                ", referenceType=" + referenceType +
                '}';
    }
}
