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

@Getter
@Setter
@Table(JdbcTrustedDomain.TABLE_NAME)
public class JdbcTrustedDomain {

    public static final String TABLE_NAME = "trusted_domains";

    public static final String FIELD_TRUSTED_DOMAIN_ID = "trusted_domain_id";

    @Id
    private String id;

    @Column("reference_id")
    private String referenceId;

    @Column("reference_type")
    private String referenceType;

    private String name;

    private String description;

    @Column("domain_identifier")
    private String domainIdentifier;

    @Column("key_material")
    private String keyMaterial;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @Table(JdbcTokenExchange.TABLE_NAME)
    public static class JdbcTokenExchange {

        public static final String TABLE_NAME = "trusted_domains_token_exchange";

        @Id
        @Column(FIELD_TRUSTED_DOMAIN_ID)
        private String trustedDomainId;

        private boolean enabled;

        @Column("scope_mappings")
        private String scopeMappings;

        @Column("user_binding_enabled")
        private boolean userBindingEnabled;

        @Column("user_binding_criteria")
        private String userBindingCriteria;
    }

    @Getter
    @Setter
    @Table(JdbcSpiffe.TABLE_NAME)
    public static class JdbcSpiffe {

        public static final String TABLE_NAME = "trusted_domains_spiffe";

        @Id
        @Column(FIELD_TRUSTED_DOMAIN_ID)
        private String trustedDomainId;

        @Column("reference_id")
        private String referenceId;

        @Column("reference_type")
        private String referenceType;

        @Column("spiffe_trust_domain")
        private String spiffeTrustDomain;

        @Column("allowed_algorithms")
        private String allowedAlgorithms;
    }

    @Getter
    @Setter
    @Table(JdbcXaa.TABLE_NAME)
    public static class JdbcXaa {

        public static final String TABLE_NAME = "trusted_domains_xaa";

        @Id
        @Column(FIELD_TRUSTED_DOMAIN_ID)
        private String trustedDomainId;

        private boolean enabled;

        @Column("aud_sub_mapping")
        private String audSubMapping;

        @Column("scope_mappings")
        private String scopeMappings;
    }

    @Getter
    @Setter
    @Table(JdbcXaaResourceServer.TABLE_NAME)
    public static class JdbcXaaResourceServer {

        public static final String TABLE_NAME = "trusted_domains_xaa_resource_servers";

        @Id
        private String id;

        @Column(FIELD_TRUSTED_DOMAIN_ID)
        private String trustedDomainId;

        private String name;

        private String resource;
    }
}
