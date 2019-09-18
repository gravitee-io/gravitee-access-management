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

import io.gravitee.am.model.oauth2.ScopeApproval;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ScopeApprovalEntity extends ScopeApproval {

    private ApplicationEntity clientEntity;

    private ScopeEntity scopeEntity;

    public ScopeApprovalEntity() {}

    public ScopeApprovalEntity(ScopeApproval scopeApproval) {
        setId(scopeApproval.getId());
        setUserId(scopeApproval.getUserId());
        setClientId(scopeApproval.getClientId());
        setDomain(scopeApproval.getDomain());
        setScope(scopeApproval.getScope());
        setStatus(scopeApproval.getStatus());
        setExpiresAt(scopeApproval.getExpiresAt());
        setCreatedAt(scopeApproval.getCreatedAt());
        setUpdatedAt(scopeApproval.getUpdatedAt());
    }

    public ApplicationEntity getClientEntity() {
        return clientEntity;
    }

    public void setClientEntity(ApplicationEntity clientEntity) {
        this.clientEntity = clientEntity;
    }

    public ScopeEntity getScopeEntity() {
        return scopeEntity;
    }

    public void setScopeEntity(ScopeEntity scopeEntity) {
        this.scopeEntity = scopeEntity;
    }
}
