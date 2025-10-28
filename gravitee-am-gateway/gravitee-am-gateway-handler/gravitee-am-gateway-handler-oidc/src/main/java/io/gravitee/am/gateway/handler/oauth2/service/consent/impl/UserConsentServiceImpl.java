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
package io.gravitee.am.gateway.handler.oauth2.service.consent.impl;

import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeService;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.ScopeApprovalService;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.oauth2.service.utils.ParameterizedScopeUtils.getScopeBase;
import static io.gravitee.am.gateway.handler.oauth2.service.utils.ParameterizedScopeUtils.isParameterizedScope;
import static io.gravitee.am.model.oauth2.ScopeApproval.ApprovalStatus.APPROVED;
import static java.util.Objects.nonNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentServiceImpl implements UserConsentService {

    private final int approvalExpirySeconds;

    @Autowired
    private ScopeApprovalService scopeApprovalService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private ScopeManager scopeManager;

    @Autowired
    private Domain domain;

    public UserConsentServiceImpl(int approvalExpirySeconds) {
        this.approvalExpirySeconds = approvalExpirySeconds;
    }

    @Override
    public Single<Set<String>> checkConsent(Client client, io.gravitee.am.model.User user) {
        return scopeApprovalService.findByDomainAndUserAndClient(domain, user.getFullId(), client.getClientId())
                .filter(approval -> {
                    Date today = new Date();
                    return approval.getExpiresAt().after(today) && APPROVED.equals(approval.getStatus());
                })
                .map(ScopeApproval::getScope)
                .collect(HashSet::new, Set::add);
    }

    @Override
    public Single<List<ScopeApproval>> saveConsent(Client client, List<ScopeApproval> approvals, User principal) {
        // compute expiry date for each approval
        // Handle case where client.getScopeSettings() might be null or empty (e.g., scopes from protected resources)
        var scopeApprovals = client.getScopeSettings() != null 
                ? client.getScopeSettings().stream()
                    .filter(s -> nonNull(s.getScopeApproval()))
                    .collect(Collectors.toMap(ApplicationScopeSettings::getScope, Function.identity()))
                : Map.<String, ApplicationScopeSettings>of();
        
        var parameterizedScopes = client.getScopeSettings() != null 
                ? client.getScopeSettings().stream().map(ApplicationScopeSettings::getScope)
                    .filter(scopeManager::isParameterizedScope)
                    .collect(Collectors.toList())
                : List.<String>of();

        approvals.forEach(a -> a.setExpiresAt(computeExpiry(scopeApprovals, a.getScope(), parameterizedScopes)));
        // save consent
        return scopeApprovalService.saveConsent(domain, client, approvals, principal);
    }

    @Override
    public Single<List<Scope>> getConsentInformation(Set<String> consent) {
        return scopeService.getAll()
                .map(scopes -> {
                    List<Scope> requestedScopes = new ArrayList<>();
                    for (String requestScope : consent) {
                        Scope requestedScope = scopes
                                .stream()
                                .filter(scope -> scope.getKey().equalsIgnoreCase(requestScope))
                                .findAny()
                                .orElse(new Scope(requestScope));

                        requestedScopes.add(requestedScope);
                    }
                    return requestedScopes;
                });
    }

    private Date computeExpiry(Map<String, ApplicationScopeSettings> scopeApprovals, String scope, List<String> parameterizedScopes) {
        final boolean isParameterizedScope = isParameterizedScope(parameterizedScopes, scope);
        Calendar expiresAt = Calendar.getInstance();

        // if client has approval settings, apply them
        if (scopeApprovals != null) {
            // test scope using strict match
            if (scopeApprovals.containsKey(scope)) {
                expiresAt.add(Calendar.SECOND, scopeApprovals.get(scope).getScopeApproval());
                return expiresAt.getTime();
            }

            // test parameterized scope
            if (isParameterizedScope) {
                final String parameterizedScope = getScopeBase(scope);
                if (scopeApprovals.containsKey(parameterizedScope) &&
                        scopeManager.isParameterizedScope(parameterizedScope)) {
                    expiresAt.add(Calendar.SECOND, scopeApprovals.get(parameterizedScope).getScopeApproval());
                    return expiresAt.getTime();
                }
            }
        }

        // if domain has approval settings, apply them
        Scope domainScope = scopeService.findByKey(isParameterizedScope ? getScopeBase(scope) : scope);
        if (domainScope != null && domainScope.getExpiresIn() != null) {
            expiresAt.add(Calendar.SECOND, domainScope.getExpiresIn());
            return expiresAt.getTime();
        }

        // default approval time
        if (approvalExpirySeconds == -1) { // use default of 1 month
            expiresAt.add(Calendar.MONTH, 1);
        } else {
            expiresAt.add(Calendar.SECOND, approvalExpirySeconds);
        }
        return expiresAt.getTime();
    }
}
