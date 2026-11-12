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
package io.gravitee.am.management.handlers.management.api.model;

import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AuthorizationDetailApprovalEntity extends AuthorizationDetailApproval {

    private ApplicationEntity clientEntity;

    @Schema(description = "The domain's definition of the type, absent when the type has been deleted.")
    private AuthorizationDetailTypeEntity typeEntity;

    public AuthorizationDetailApprovalEntity(AuthorizationDetailApproval approval) {
        setId(approval.getId());
        setUserId(approval.getUserId());
        setClientId(approval.getClientId());
        setDomain(approval.getDomain());
        setDetail(approval.getDetail());
        setDigest(approval.getDigest());
        setStatus(approval.getStatus());
        setExpiresAt(approval.getExpiresAt());
        setCreatedAt(approval.getCreatedAt());
        setUpdatedAt(approval.getUpdatedAt());
    }
}
