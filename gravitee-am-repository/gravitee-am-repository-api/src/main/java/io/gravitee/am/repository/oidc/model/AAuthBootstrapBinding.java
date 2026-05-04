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
 * AAUTH bootstrap binding entity. Records the permanent relationship between
 * a user and an agent server after a successful bootstrap flow.
 *
 * @author GraviteeSource Team
 */
public class AAuthBootstrapBinding {

    private String id;
    private String domain;
    private String userId;
    private String agentServerUrl;
    private String agentIdentifier;
    private String pairwiseSub;
    private Date createdAt;
    private Date updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAgentServerUrl() {
        return agentServerUrl;
    }

    public void setAgentServerUrl(String agentServerUrl) {
        this.agentServerUrl = agentServerUrl;
    }

    public String getAgentIdentifier() {
        return agentIdentifier;
    }

    public void setAgentIdentifier(String agentIdentifier) {
        this.agentIdentifier = agentIdentifier;
    }

    public String getPairwiseSub() {
        return pairwiseSub;
    }

    public void setPairwiseSub(String pairwiseSub) {
        this.pairwiseSub = pairwiseSub;
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
}
