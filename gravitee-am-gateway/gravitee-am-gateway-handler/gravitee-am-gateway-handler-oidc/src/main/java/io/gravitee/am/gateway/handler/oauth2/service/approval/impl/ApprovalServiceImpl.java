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
package io.gravitee.am.gateway.handler.oauth2.service.approval.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.service.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.UserConsentAuditBuilder;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApprovalServiceImpl implements ApprovalService {

    @Autowired
    private ScopeApprovalRepository scopeApprovalRepository;

    @Autowired
    private Domain domain;

    @Autowired
    private ScopeManager scopeManager;

    @Autowired
    private AuditService auditService;

    @Value("${oauth2.approval.expiry:-1}")
    private int approvalExpirySeconds;

    @Override
    public Single<AuthorizationRequest> checkApproval(AuthorizationRequest authorizationRequest, Client client, User user) {
        if (authorizationRequest.getPrompts() != null && authorizationRequest.getPrompts().contains("consent")) {
            // Set denied scopes for the user consent page
            authorizationRequest.setDeniedScopes(authorizationRequest.getScopes());

            // Send an access denied exception to force consent approval page
            return Single.error(new AccessDeniedException("User denied access"));
        }

        // check client auto approval option
        return checkAutoApproval(authorizationRequest, client)
                .flatMap(authorizationRequest1 ->  {
                    if (authorizationRequest1.isApproved()) {
                        return Single.just(authorizationRequest1);
                    }
                    // check user approval
                    return checkUserApproval(authorizationRequest, user);
                });
    }

    @Override
    public Single<AuthorizationRequest> saveApproval(AuthorizationRequest authorizationRequest, Client client, User user, io.gravitee.am.identityprovider.api.User principal) {
        // Get the unapproved requested scopes
        Set<String> requestedScopes = authorizationRequest.getDeniedScopes();
        Set<String> approvedScopes = new HashSet<>();
        Set<ScopeApproval> approvals = new HashSet<>();

        // Store the scopes that have been approved / denied
        Map<String, String> approvalParameters = authorizationRequest.getApprovalParameters();
        for (String requestedScope : requestedScopes) {
            String approvalParameter = requestedScope;
            String value = approvalParameters.get(OAuth2Constants.SCOPE_PREFIX + approvalParameter);
            value = value == null ? "" : value.toLowerCase();
            Date expiry = computeExpiry(client, requestedScope);
            if ("true".equals(value) || value.startsWith("approve")) {
                approvedScopes.add(requestedScope);
                approvals.add(new ScopeApproval(user.getId(), authorizationRequest.getClientId(),
                        requestedScope, ScopeApproval.ApprovalStatus.APPROVED, expiry, domain.getId()));
            }
            else {
                approvals.add(new ScopeApproval(user.getId(), authorizationRequest.getClientId(),
                        requestedScope, ScopeApproval.ApprovalStatus.DENIED, expiry, domain.getId()));
            }
        }

        return Observable.fromIterable(approvals)
                .flatMapSingle(approval -> scopeApprovalRepository.upsert(approval))
                .toList()
                .flatMap(savedApprovals -> {
                    boolean approved;
                    authorizationRequest.setScopes(approvedScopes);
                    if (approvedScopes.isEmpty() && !requestedScopes.isEmpty()) {
                        approved = false;
                    }
                    else {
                        approved = true;
                    }
                    authorizationRequest.setApproved(approved);
                    return Single.just(authorizationRequest);
                })
                .doOnSuccess(__ -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).domain(domain.getId()).client(client).principal(principal).type(EventType.USER_CONSENT_CONSENTED).approvals(approvals)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).domain(domain.getId()).client(client).principal(principal).type(EventType.USER_CONSENT_CONSENTED).throwable(throwable)));
    }

    private Single<AuthorizationRequest> checkUserApproval(AuthorizationRequest authorizationRequest, User user) {
        Set<String> requestedScopes = authorizationRequest.getScopes();
        Set<String> approvedScopes = new HashSet<>();
        return scopeApprovalRepository.findByDomainAndUserAndClient(domain.getId(), user.getId(), authorizationRequest.getClientId())
                .flatMap(userApprovals -> {
                    // Look at the scopes and see if they have expired
                    if (userApprovals != null) {
                        Date today = new Date();
                        for (ScopeApproval approval : userApprovals) {
                            if (approval.getExpiresAt().after(today)) {
                                if (approval.getStatus() == ScopeApproval.ApprovalStatus.APPROVED) {
                                    approvedScopes.add(approval.getScope());
                                }
                            }
                        }
                    }

                    // If the requested scopes have already been acted upon by the user,
                    // this request is approved
                    if (approvedScopes.containsAll(requestedScopes)) {
                        approvedScopes.retainAll(requestedScopes);
                        // Set only the scopes that have been approved by the user
                        authorizationRequest.setScopes(approvedScopes);
                        authorizationRequest.setApproved(true);
                        return Single.just(authorizationRequest);
                    }

                    // set denied scopes for the user consent page
                    authorizationRequest.setDeniedScopes(requestedScopes.stream().filter(requestedScope -> !approvedScopes.contains(requestedScope)).collect(Collectors.toSet()));
                    return Single.error(new AccessDeniedException("User denied access"));
                });
    }

    private Single<AuthorizationRequest> checkAutoApproval(AuthorizationRequest authorizationRequest, Client client) {
        List<String> clientAutoApproveScopes = client.getAutoApproveScopes();
        Set<String> requestedScopes = authorizationRequest.getScopes();
        Set<String> approvedScopes = requestedScopes.stream().filter(scope -> isAutoApprove(clientAutoApproveScopes, scope)).collect(Collectors.toSet());
        if (approvedScopes.containsAll(requestedScopes)) {
            authorizationRequest.setApproved(true);
        }
        return Single.just(authorizationRequest);
    }

    private boolean isAutoApprove(List<String> autoApproveScopes, String scope) {
        if (autoApproveScopes == null) {
            return false;
        }
        for (String auto : autoApproveScopes) {
            if (auto.equals("true") || scope.matches(auto)) {
                return true;
            }
        }
        return false;
    }

    private Date computeExpiry(Client client, String scope) {
        Calendar expiresAt = Calendar.getInstance();

        // if client has approval settings, apply them
        if (client.getScopeApprovals() != null && client.getScopeApprovals().containsKey(scope)) {
            expiresAt.add(Calendar.SECOND, client.getScopeApprovals().get(scope));
            return expiresAt.getTime();
        }

        // if domain has approval settings, apply them
        Scope domainScope = scopeManager.findByKey(scope);
        if (domainScope != null && domainScope.getExpiresIn() != null) {
            expiresAt.add(Calendar.SECOND, domainScope.getExpiresIn());
            return expiresAt.getTime();
        }

        // default approval time
        if (approvalExpirySeconds == -1) { // use default of 1 month
            expiresAt.add(Calendar.MONTH, 1);
        }
        else {
            expiresAt.add(Calendar.SECOND, approvalExpirySeconds);
        }
        return expiresAt.getTime();
    }
}
