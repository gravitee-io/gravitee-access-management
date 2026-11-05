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

import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailDigest;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailTypeManager;
import io.gravitee.am.gateway.handler.oauth2.service.consent.AuthorizationDetailsConsentService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationAuthorizationDetailTypeSettings;
import io.gravitee.am.model.oauth2.AuthorizationDetail;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthorizationDetailApprovalService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.gravitee.am.model.oauth2.ScopeApproval.ApprovalStatus.APPROVED;
import static io.gravitee.am.model.oauth2.ScopeApproval.ApprovalStatus.DENIED;

public class AuthorizationDetailsConsentServiceImpl implements AuthorizationDetailsConsentService {

    private final int approvalExpirySeconds;

    @Autowired
    private AuthorizationDetailApprovalService authorizationDetailApprovalService;

    @Autowired
    private AuthorizationDetailTypeManager authorizationDetailTypeManager;

    @Autowired
    private Domain domain;

    /**
     * @param approvalExpirySeconds how long an approval is remembered when neither the application nor the type sets
     *                              it, {@code -1} for one month
     */
    public AuthorizationDetailsConsentServiceImpl(int approvalExpirySeconds) {
        this.approvalExpirySeconds = approvalExpirySeconds;
    }

    @Override
    public Single<List<AuthorizationDetail>> getDetailsToPresent(Client client, User user, AuthorizationDetails requested, boolean promptConsent) {
        if (requested == null || requested.isEmpty()) {
            return Single.just(List.of());
        }
        if (promptConsent) {
            return Single.just(requested.getAll());
        }
        if (client.isSkipConsent()) {
            return Single.just(List.of());
        }
        final Date now = new Date();
        return authorizationDetailApprovalService.findByDomainAndUserAndClient(domain, user.getFullId(), client.getClientId())
                .filter(approval -> APPROVED.equals(approval.getStatus()))
                .filter(approval -> approval.getExpiresAt() == null || approval.getExpiresAt().after(now))
                .toList()
                .map(approvals -> requested.getAll().stream()
                        .filter(detail -> !isCovered(detail, approvals))
                        .toList());
    }

    /**
     * Strict matching: an approval covers only a detail with the same JSON value.
     */
    private static boolean isCovered(AuthorizationDetail detail, List<AuthorizationDetailApproval> approvals) {
        final String digest = AuthorizationDetailDigest.of(detail);
        return approvals.stream().anyMatch(approval -> digest.equals(approval.getDigest()));
    }

    @Override
    public Completable saveConsent(Client client, User user, io.gravitee.am.identityprovider.api.User principal,
                                   AuthorizationDetails requested, List<AuthorizationDetail> presented, boolean approved) {
        final List<AuthorizationDetailApproval> approvals = presented.stream()
                .map(detail -> {
                    AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
                    approval.setUserId(user.getFullId());
                    approval.setClientId(client.getClientId());
                    approval.setDomain(domain.getId());
                    approval.setDetail(detail);
                    approval.setDigest(AuthorizationDetailDigest.of(detail));
                    approval.setStatus(approved ? APPROVED : DENIED);
                    approval.setExpiresAt(expiry(client, detail.getType()));
                    return approval;
                })
                .toList();
        return authorizationDetailApprovalService.saveConsent(domain, client, principal, requested, approvals, approved);
    }

    /**
     * The application's setting for the type, then the type's own, then the gateway default.
     */
    private Date expiry(Client client, String type) {
        final Calendar expiresAt = Calendar.getInstance();
        final Optional<Integer> configured = Optional.ofNullable(client.getAuthorizationDetailTypes()).stream()
                .flatMap(List::stream)
                .filter(settings -> Objects.equals(settings.getType(), type))
                .map(ApplicationAuthorizationDetailTypeSettings::getExpiresIn)
                .filter(Objects::nonNull)
                .findFirst()
                .or(() -> authorizationDetailTypeManager.findByType(type).map(AuthorizationDetailType::getExpiresIn));
        if (configured.isPresent()) {
            expiresAt.add(Calendar.SECOND, configured.get());
        } else if (approvalExpirySeconds == -1) {
            expiresAt.add(Calendar.MONTH, 1);
        } else {
            expiresAt.add(Calendar.SECOND, approvalExpirySeconds);
        }
        return expiresAt.getTime();
    }
}
