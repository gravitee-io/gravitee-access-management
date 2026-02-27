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
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Table("authorization_bundles")
public class JdbcAuthorizationBundle {
    @Id
    private String id;
    @Column("domain_id")
    private String domainId;
    private String name;
    private String description;
    @Column("engine_type")
    private String engineType;
    @Column("policy_set_id")
    private String policySetId;
    @Column("policy_set_version")
    private int policySetVersion;
    @Column("policy_set_pin_to_latest")
    private boolean policySetPinToLatest;
    @Column("schema_id")
    private String schemaId;
    @Column("schema_version")
    private int schemaVersion;
    @Column("schema_pin_to_latest")
    private boolean schemaPinToLatest;
    @Column("entity_store_id")
    private String entityStoreId;
    @Column("entity_store_version")
    private int entityStoreVersion;
    @Column("entity_store_pin_to_latest")
    private boolean entityStorePinToLatest;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
