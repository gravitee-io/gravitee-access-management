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
package io.gravitee.am.model.oauth2;

import io.gravitee.am.model.UserId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 * A user's decision on one authorization detail (RFC 9396) for a client, matched on {@code digest}.
 */
@Data
public class AuthorizationDetailApproval {

    private String id;

    private UserId userId;

    private String clientId;

    private String domain;

    private Map<String, Object> detail;

    private String digest;

    private ScopeApproval.ApprovalStatus status;

    @Schema(type = "java.lang.Long")
    private Date expiresAt;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;
}
