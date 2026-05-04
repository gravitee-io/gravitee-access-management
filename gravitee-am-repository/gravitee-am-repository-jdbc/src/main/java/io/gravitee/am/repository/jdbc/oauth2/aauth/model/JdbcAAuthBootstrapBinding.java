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
 * JDBC representation of an AAUTH bootstrap binding.
 *
 * @author GraviteeSource Team
 */
@Table("aauth_bootstrap_bindings")
public class JdbcAAuthBootstrapBinding {

    @Id
    private String id;
    private String domain;
    @Column("user_id")
    private String userId;
    @Column("agent_server_url")
    private String agentServerUrl;
    @Column("agent_identifier")
    private String agentIdentifier;
    @Column("pairwise_sub")
    private String pairwiseSub;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAgentServerUrl() { return agentServerUrl; }
    public void setAgentServerUrl(String agentServerUrl) { this.agentServerUrl = agentServerUrl; }

    public String getAgentIdentifier() { return agentIdentifier; }
    public void setAgentIdentifier(String agentIdentifier) { this.agentIdentifier = agentIdentifier; }

    public String getPairwiseSub() { return pairwiseSub; }
    public void setPairwiseSub(String pairwiseSub) { this.pairwiseSub = pairwiseSub; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
