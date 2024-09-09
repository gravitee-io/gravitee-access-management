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
import java.util.Objects;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class ScopeApproval {

    public enum ApprovalStatus {
        APPROVED,
        DENIED
    }

    private String id;

    private String transactionId;

    private UserId userId;

    private String clientId;

    private String domain;

    private String scope;

    private ApprovalStatus status;

    @Schema(type = "java.lang.Long")
    private Date expiresAt;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public ScopeApproval() {
    }

    public ScopeApproval(String transactionId, UserId userId, String clientId, String domain, String scope, ApprovalStatus status) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.clientId = clientId;
        this.domain = domain;
        this.scope = scope;
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScopeApproval that = (ScopeApproval) o;
        return Objects.equals(userId, that.userId)
                && Objects.equals(clientId, that.clientId)
                && Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, clientId, scope);
    }
}
