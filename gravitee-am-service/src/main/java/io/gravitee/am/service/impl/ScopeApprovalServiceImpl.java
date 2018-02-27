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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ScopeApprovalServiceImpl implements ScopeApprovalService {
    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(ScopeApprovalServiceImpl.class);

    @Autowired
    private ScopeApprovalRepository scopeApprovalRepository;

    @Override
    public Single<ScopeApproval> create(ScopeApproval approval) {
        LOGGER.debug("Create a new scope approval client[{}] user[{}] scope[{}]", approval.getUserId(),
                approval.getClientId(), approval.getScope());
        return scopeApprovalRepository.create(approval)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to create a scope approval", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a scope approval", ex));
                });
    }

    @Override
    public ScopeApproval revoke(ScopeApproval approval) {
        return null;
    }

    @Override
    public Single<Collection<ScopeApproval>> findByUserAndClient(String domain, String userId, String clientId) {
        LOGGER.debug("Find scope approvals by domain[{}] client[{}] user[{}]", domain);
        return scopeApprovalRepository.findByDomainAndUserAndClient(domain, userId, clientId)
                .map(scopeApprovals -> (Collection<ScopeApproval>) scopeApprovals)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find scope approval", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find scope approval", ex));
                });
    }
}
