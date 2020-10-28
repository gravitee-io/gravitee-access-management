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
@Table("domains")
public class JdbcDomain {
    @Id
    private String id;
    private String name;
    private String description;
    private boolean enabled;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
    private String path;
    private boolean vhostMode = false;
    private String oidc;
    private String uma;
    @Column("login_settings")
    private String loginSettings;
    @Column("web_authn_settings")
    private String webAuthnSettings;
    private String scim;
    @Column("account_settings")
    private String accountSettings;
    @Column("reference_type")
    private String referenceType;
    @Column("reference_id")
    private String referenceId;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isVhostMode() {
        return vhostMode;
    }

    public void setVhostMode(boolean vhostMode) {
        this.vhostMode = vhostMode;
    }

    public String getOidc() {
        return oidc;
    }

    public void setOidc(String oidc) {
        this.oidc = oidc;
    }

    public String getUma() {
        return uma;
    }

    public void setUma(String uma) {
        this.uma = uma;
    }

    public String getLoginSettings() {
        return loginSettings;
    }

    public void setLoginSettings(String loginSettings) {
        this.loginSettings = loginSettings;
    }

    public String getWebAuthnSettings() {
        return webAuthnSettings;
    }

    public void setWebAuthnSettings(String webAuthnSettings) {
        this.webAuthnSettings = webAuthnSettings;
    }

    public String getScim() {
        return scim;
    }

    public void setScim(String scim) {
        this.scim = scim;
    }

    public String getAccountSettings() {
        return accountSettings;
    }

    public void setAccountSettings(String accountSettings) {
        this.accountSettings = accountSettings;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    /**
     * Bean class to provide list of identity providers linked to the Domain
     */
    @Table("domain_identities")
    public static class Identity {
        @Column("domain_id")
        String domainId;

        @Column("identity_id")
        String identity;

        public String getDomainId() {
            return domainId;
        }

        public void setDomainId(String domainId) {
            this.domainId = domainId;
        }

        public String getIdentity() {
            return identity;
        }

        public void setIdentity(String identity) {
            this.identity = identity;
        }
    }
    /**
     * Bean class to provide list of tags linked to the Domain
     */
    @Table("domain_tags")
    public static class Tag {
        @Column("domain_id")
        String domainId;

        String tag;

        public String getDomainId() {
            return domainId;
        }

        public void setDomainId(String domainId) {
            this.domainId = domainId;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }
    }
    /**
     * Bean class to provide list of vhost linked to the Domain
     */
    @Table("domain_vhosts")
    public static class Vhost {
        @Column("domain_id")
        String domainId;

        String host;

        String path;

        @Column("override_endpoints")
        boolean overrideEndpoint;

        public String getDomainId() {
            return domainId;
        }

        public void setDomainId(String domainId) {
            this.domainId = domainId;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isOverrideEndpoint() {
            return overrideEndpoint;
        }

        public void setOverrideEndpoint(boolean overrideEndpoint) {
            this.overrideEndpoint = overrideEndpoint;
        }
    }

}
