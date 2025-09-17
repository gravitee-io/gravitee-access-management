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
package io.gravitee.am.repository.jdbc.management.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("applications")
public class JdbcApplication {
    @Id
    private String id;
    private String name;
    private String type;
    private String description;
    @Column("agent_card_url")
    private String agentCardUrl;
    private String domain;

    private boolean enabled;
    private boolean template;
    private String certificate;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
    // JSON
    private String metadata;
    private String settings;

    private String secretSettings;
    public JdbcApplication() {
    }

    public JdbcApplication(String id, String name, String type, String description, String domain, boolean enabled, boolean template, String certificate, LocalDateTime createdAt, LocalDateTime updatedAt, String metadata, String settings, String secretSettings) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.domain = domain;
        this.enabled = enabled;
        this.template = template;
        this.certificate = certificate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.metadata = metadata;
        this.settings = settings;
        this.secretSettings = secretSettings;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAgentCardUrl() {
        return agentCardUrl;
    }

    public void setAgentCardUrl(String agentCardUrl) {
        this.agentCardUrl = agentCardUrl;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTemplate() {
        return template;
    }

    public void setTemplate(boolean template) {
        this.template = template;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }

    public String getSecretSettings() {
        return secretSettings;
    }

    public void setSecretSettings(String secretSettings) {
        this.secretSettings = secretSettings;
    }

    @Table("application_identities")
    public static class Identity {
        @Column("application_id")
        private String applicationId;
        private String identity;
        @Column("selection_rule")
        private String selectionRule;
        private int priority;

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        public String getIdentity() {
            return identity;
        }

        public void setIdentity(String identity) {
            this.identity = identity;
        }

        public String getSelectionRule() {
            return selectionRule;
        }

        public int getPriority() {
            return priority;
        }
    }

    @Table("application_client_secrets")
    public static class ClientSecret {
        @Column("application_id")
        private String applicationId;

        private String id;

        @Column("settings_id")
        private String settingsId;
        private String name;
        private String secret;
        @Column("created_at")
        private LocalDateTime createdAt;
        @Column("expires_at")
        private LocalDateTime expiresAt;

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSettingsId() {
            return settingsId;
        }

        public void setSettingsId(String settingsId) {
            this.settingsId = settingsId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    @Table("application_grants")
    public static class Grant {
        @Column("application_id")
        private String applicationId;
        @Column("grant_type")
        private String grant;

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        public String getGrant() {
            return grant;
        }

        public void setGrant(String grant) {
            this.grant = grant;
        }
    }

    @Table("application_factors")
    public static class Factor {
        @Column("application_id")
        private String applicationId;
        private String factor;

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        public String getFactor() {
            return factor;
        }

        public void setFactor(String factor) {
            this.factor = factor;
        }
    }

    @Table("application_scope_settings")
    public static class ScopeSettings {
        @Column("application_id")
        private String applicationId;
        private String scope;
        @Column("is_default")
        private boolean defaultScope;
        @Column("scope_approval")
        private Integer scopeApproval;

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public boolean isDefaultScope() {
            return defaultScope;
        }

        public void setDefaultScope(boolean defaultScope) {
            this.defaultScope = defaultScope;
        }

        public Integer getScopeApproval() {
            return scopeApproval;
        }

        public void setScopeApproval(Integer scopeApproval) {
            this.scopeApproval = scopeApproval;
        }
    }
}
