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
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("authorization_codes")
@Getter
@Setter
public class JdbcAuthorizationCode {
    @Id
    private String id;
    @Column("transaction_id")
    private String transactionId;
    @Column("context_version")
    private int contextVersion;
    private String code;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("expire_at")
    private LocalDateTime expireAt;
    @Column("client_id")
    private String clientId;
    @Column("resources")
    private String resources;
    private String subject;
    @Column("redirect_uri")
    private String redirectUri;
    @Column("scopes")
    private String scopes;
    @Column("request_parameters")
    private String requestParameters;
}
