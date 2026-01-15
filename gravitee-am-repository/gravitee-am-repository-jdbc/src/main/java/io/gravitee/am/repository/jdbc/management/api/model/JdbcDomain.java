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

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("domains")
@Getter
@Setter
public class JdbcDomain {
    @Id
    private String id;
    private String hrid;
    private String name;
    private String version;
    private String description;
    private boolean enabled;
    @Column("alert_enabled")
    private boolean alertEnabled;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
    private String path;
    private boolean vhostMode = false;
    private String oidc;
    private String uma;
    private String saml;
    @Column("login_settings")
    private String loginSettings;
    @Column("web_authn_settings")
    private String webAuthnSettings;
    private String scim;
    @Column("account_settings")
    private String accountSettings;
    @Column("password_settings")
    private String passwordSettings;
    @Column("self_service_account_settings")
    private String selfServiceAccountManagementSettings;
    @Column("reference_type")
    private String referenceType;
    @Column("reference_id")
    private String referenceId;
    private boolean master = false;
    @Column("cors_settings")
    private String corsSettings;
    @Column("data_plane_id")
    private String dataPlaneId;
    @Column("secret_expiration_settings")
    private String secretExpirationSettings;
    @Column("post_login_action")
    private String postLoginAction;


    /**
     * Bean class to provide list of identity providers linked to the Domain
     */
    @Getter
    @Setter
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
    @Getter
    @Setter
    @Table("domain_tags")
    public static class Tag {
        @Column("domain_id")
        String domainId;
        String tag;
    }
    /**
     * Bean class to provide list of vhost linked to the Domain
     */
    @Getter
    @Setter
    @Table("domain_vhosts")
    public static class Vhost {
        @Column("domain_id")
        String domainId;
        String host;
        String path;
        @Column("override_entrypoint")
        boolean overrideEntrypoint;
    }
}
