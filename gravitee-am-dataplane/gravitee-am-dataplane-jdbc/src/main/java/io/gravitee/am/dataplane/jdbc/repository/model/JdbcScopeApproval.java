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
package io.gravitee.am.dataplane.jdbc.repository.model;

import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.dataplane.jdbc.repository.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.provider.common.DateHelper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("scope_approvals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdbcScopeApproval {
    @Id
    private String id;
    @Column("transaction_id")
    private String transactionId;
    @Column("user_id")
    private String userId;
    @Column(AbstractJdbcRepository.USER_EXTERNAL_ID_FIELD)
    private String userExternalId;
    @Column(AbstractJdbcRepository.USER_SOURCE_FIELD)
    private String userSource;
    @Column("client_id")
    private String clientId;
    private String domain;
    private String scope;
    private String status;
    @Column("expires_at")
    private LocalDateTime expiresAt;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public static JdbcScopeApproval of(ScopeApproval entity) {
        var approval = new JdbcScopeApproval();
        approval.setId(entity.getId());
        approval.setTransactionId(entity.getTransactionId());
        approval.setUserId(entity.getUserId().id());
        approval.setUserSource(entity.getUserId().source());
        approval.setUserExternalId(entity.getUserId().externalId());
        approval.setClientId(entity.getClientId());
        approval.setDomain(entity.getDomain());
        approval.setScope(entity.getScope());
        approval.setStatus(entity.getStatus().name());
        approval.setExpiresAt(DateHelper.toLocalDateTime(entity.getExpiresAt()));
        approval.setCreatedAt(DateHelper.toLocalDateTime(entity.getCreatedAt()));
        approval.setUpdatedAt(DateHelper.toLocalDateTime(entity.getUpdatedAt()));
        return approval;
    }

    public ScopeApproval toEntity() {
        var approval = new ScopeApproval();
        approval.setId(id);
        approval.setTransactionId(transactionId);
        approval.setUserId(new UserId(userId, userExternalId, userSource));
        approval.setClientId(clientId);
        approval.setDomain(domain);
        approval.setScope(scope);
        approval.setStatus(ScopeApproval.ApprovalStatus.valueOf(status));
        approval.setExpiresAt(DateHelper.toDate(expiresAt));
        approval.setCreatedAt(DateHelper.toDate(createdAt));
        approval.setUpdatedAt(DateHelper.toDate(updatedAt));
        return approval;
    }

}
