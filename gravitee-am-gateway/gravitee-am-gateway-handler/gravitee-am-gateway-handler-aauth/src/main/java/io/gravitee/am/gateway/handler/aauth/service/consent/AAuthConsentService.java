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
package io.gravitee.am.gateway.handler.aauth.service.consent;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.ScopeApprovalService;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.gravitee.am.model.oauth2.ScopeApproval.ApprovalStatus.APPROVED;

/**
 * AAUTH consent service. Wraps {@link ScopeApprovalService} (from the shared parent context)
 * to provide consent check and save functionality without depending on the OIDC plugin's classloader.
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthConsentService {

    private static final int DEFAULT_APPROVAL_EXPIRY_MONTHS = 1;

    private final ScopeApprovalService scopeApprovalService;
    private final Domain domain;

    /**
     * Check which scopes the user has already approved for the given client.
     *
     * @param userId   the user's full ID
     * @param clientId the agent's client ID (metadata URL)
     * @return the set of approved scope keys
     */
    public Single<Set<String>> checkConsent(UserId userId, String clientId) {
        return scopeApprovalService.findByDomainAndUserAndClient(domain, userId, clientId)
                .filter(approval -> {
                    Date now = new Date();
                    return approval.getExpiresAt() != null
                            && approval.getExpiresAt().after(now)
                            && APPROVED.equals(approval.getStatus());
                })
                .map(ScopeApproval::getScope)
                .collect(HashSet::new, Set::add);
    }

    /**
     * Save consent approvals for the given scopes.
     *
     * @param client    the agent Application as Client
     * @param userId    the user's full ID
     * @param scopes    the approved scope keys
     * @param principal the authenticated user principal
     * @return the saved approvals
     */
    public Single<List<ScopeApproval>> saveConsent(Client client, UserId userId, Set<String> scopes,
                                                     io.gravitee.am.identityprovider.api.User principal) {
        List<ScopeApproval> approvals = scopes.stream()
                .map(scope -> {
                    ScopeApproval approval = new ScopeApproval(
                            null, userId, client.getClientId(), domain.getId(),
                            scope, APPROVED);
                    approval.setExpiresAt(defaultExpiry());
                    return approval;
                })
                .toList();

        return scopeApprovalService.saveConsent(domain, client, approvals, principal);
    }

    private Date defaultExpiry() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, DEFAULT_APPROVAL_EXPIRY_MONTHS);
        return cal.getTime();
    }
}
