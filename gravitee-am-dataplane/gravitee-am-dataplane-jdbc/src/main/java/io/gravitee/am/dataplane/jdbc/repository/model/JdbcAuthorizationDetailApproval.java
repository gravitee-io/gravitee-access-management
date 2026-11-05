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

import io.gravitee.am.dataplane.jdbc.repository.AbstractJdbcRepository;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.jdbc.provider.common.DateHelper;
import io.gravitee.am.repository.jdbc.provider.common.JSONMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Map;

@Table("authorization_detail_approvals")
@Data
@NoArgsConstructor
public class JdbcAuthorizationDetailApproval {
    @Id
    private String id;
    @Column("user_id")
    private String userId;
    @Column(AbstractJdbcRepository.USER_EXTERNAL_ID_FIELD)
    private String userExternalId;
    @Column(AbstractJdbcRepository.USER_SOURCE_FIELD)
    private String userSource;
    @Column("client_id")
    private String clientId;
    private String domain;
    private String detail;
    private String digest;

    private String status;
    @Column("expires_at")
    private LocalDateTime expiresAt;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public static JdbcAuthorizationDetailApproval of(AuthorizationDetailApproval entity) {
        var approval = new JdbcAuthorizationDetailApproval();
        approval.setId(entity.getId());
        approval.setUserId(entity.getUserId().id());
        approval.setUserExternalId(entity.getUserId().externalId());
        approval.setUserSource(entity.getUserId().source());
        approval.setClientId(entity.getClientId());
        approval.setDomain(entity.getDomain());
        approval.setDetail(JSONMapper.toJson(entity.getDetail()));
        approval.setDigest(entity.getDigest());
        approval.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        approval.setExpiresAt(DateHelper.toLocalDateTime(entity.getExpiresAt()));
        approval.setCreatedAt(DateHelper.toLocalDateTime(entity.getCreatedAt()));
        approval.setUpdatedAt(DateHelper.toLocalDateTime(entity.getUpdatedAt()));
        return approval;
    }

    @SuppressWarnings("unchecked")
    public AuthorizationDetailApproval toEntity() {
        var approval = new AuthorizationDetailApproval();
        approval.setId(id);
        approval.setUserId(new UserId(userId, userExternalId, userSource));
        approval.setClientId(clientId);
        approval.setDomain(domain);
        approval.setDetail(JSONMapper.toBean(detail, Map.class));
        approval.setDigest(digest);
        approval.setStatus(status == null ? null : ScopeApproval.ApprovalStatus.valueOf(status));
        approval.setExpiresAt(DateHelper.toDate(expiresAt));
        approval.setCreatedAt(DateHelper.toDate(createdAt));
        approval.setUpdatedAt(DateHelper.toDate(updatedAt));
        return approval;
    }
}
