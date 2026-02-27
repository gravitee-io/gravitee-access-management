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
@Table("authorization_schema_versions")
public class JdbcAuthorizationSchemaVersion {
    @Id
    private String id;
    @Column("schema_id")
    private String schemaId;
    private int version;
    private String content;
    @Column("commit_message")
    private String commitMessage;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("created_by")
    private String createdBy;
}
