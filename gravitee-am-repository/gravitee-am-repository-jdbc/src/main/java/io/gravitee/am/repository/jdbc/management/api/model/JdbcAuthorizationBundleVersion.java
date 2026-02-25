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
@Table("authorization_bundle_versions")
public class JdbcAuthorizationBundleVersion {
    @Id
    private String id;
    @Column("bundle_id")
    private String bundleId;
    @Column("domain_id")
    private String domainId;
    private int version;
    private String schema;
    private String policies;
    private String entities;
    private String comment;
    @Column("created_by")
    private String createdBy;
    @Column("created_at")
    private LocalDateTime createdAt;
}
