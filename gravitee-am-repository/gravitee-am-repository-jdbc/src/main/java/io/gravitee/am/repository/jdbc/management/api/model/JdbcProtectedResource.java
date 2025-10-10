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
@Table(JdbcProtectedResource.TABLE_NAME)
public class JdbcProtectedResource {
    public static final String TABLE_NAME = "protected_resources";

    @Id
    private String id;

    private String name;

    @Column("client_id")
    private String clientId;

    private String domainId;

    private String description;

    private String type;

    @Column("resource_identifiers")
    private String resourceIdentifiers;

    @Column("secret_settings")
    private String secretSettings;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @Table(JdbcProtectedResourceClientSecret.TABLE_NAME)
    public static class JdbcProtectedResourceClientSecret {
        public static final String TABLE_NAME = "protected_resource_client_secrets";
        public static final String FIELD_PROTECTED_RESOURCE_ID = "protected_resource_id";

        @Id
        private String id;

        @Column(FIELD_PROTECTED_RESOURCE_ID)
        private String protectedResourceId;

        @Column("settings_id")
        private String settingsId;

        private String name;

        private String secret;

        @Column("created_at")
        private LocalDateTime createdAt;

        @Column("expires_at")
        private LocalDateTime expiresAt;

    }
}
