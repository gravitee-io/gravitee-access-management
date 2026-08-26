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
package io.gravitee.am.repository.jdbc.oauth2.api.model;

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
@Table("device_authorization_requests")
public class JdbcDeviceAuthorizationRequest {

    @Id
    private String id;

    @Column("user_code")
    private String userCode;

    private String status;

    private String client;

    private String subject;

    private String scopes;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("last_access_at")
    private LocalDateTime lastAccessAt;

    @Column("expire_at")
    private LocalDateTime expireAt;

    @Column("interval_increment")
    private int intervalIncrement;
}
