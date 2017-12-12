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
package io.gravitee.am.gateway.handler.oauth2.provider.approval;

import io.gravitee.am.gateway.service.ScopeApprovalService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.ScopeApproval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.provider.approval.Approval;
import org.springframework.security.oauth2.provider.approval.ApprovalStore;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultApprovalStore implements ApprovalStore {

    @Autowired
    private ScopeApprovalService scopeApprovalService;

    @Autowired
    private Domain domain;

    @Override
    public boolean addApprovals(Collection<Approval> approvals) {
        for (Approval approval : approvals) {
            scopeApprovalService.create(map(approval));
        }

        return true;
    }

    @Override
    public boolean revokeApprovals(Collection<Approval> approvals) {
        for (Approval approval : approvals) {
            scopeApprovalService.revoke(map(approval));
        }

        return true;
    }

    @Override
    public Collection<Approval> getApprovals(String userId, String clientId) {
        return scopeApprovalService.findByUserAndClient(domain.getId(), userId, clientId)
                .stream()
                .map(this::map)
                .collect(Collectors.toList());
    }

    private ScopeApproval map(Approval approval) {
        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setDomain(domain.getId());
        scopeApproval.setClientId(approval.getClientId());
        scopeApproval.setUserId(approval.getUserId());
        scopeApproval.setScope(approval.getScope());
        scopeApproval.setUpdatedAt(approval.getLastUpdatedAt());
        scopeApproval.setExpiresAt(approval.getExpiresAt());
        scopeApproval.setStatus(ScopeApproval.ApprovalStatus.valueOf(approval.getStatus().name().toUpperCase()));
        return scopeApproval;
    }

    private Approval map(ScopeApproval scopeApproval) {
        return new Approval(
                scopeApproval.getUserId(),
                scopeApproval.getClientId(),
                scopeApproval.getScope(),
                scopeApproval.getExpiresAt(),
                Approval.ApprovalStatus.valueOf(scopeApproval.getStatus().name().toUpperCase()),
                scopeApproval.getUpdatedAt());
    }
}
