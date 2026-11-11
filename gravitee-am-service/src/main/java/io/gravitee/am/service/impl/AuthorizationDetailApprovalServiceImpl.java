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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthorizationDetailApprovalService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.UserConsentAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@CustomLog
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationDetailApprovalServiceImpl implements AuthorizationDetailApprovalService {

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Autowired
    private AuditService auditService;

    @Override
    public Flowable<AuthorizationDetailApproval> findByDomainAndUser(Domain domain, UserId userId) {
        log.debug("Find authorization detail approvals by domain: {} and user: {}", domain.getId(), userId);
        return dataPlaneRegistry.getAuthorizationDetailApprovalRepository(domain).findByDomainAndUser(domain.getId(), userId)
                .onErrorResumeNext(ex -> Flowable.error(new TechnicalManagementException(
                        String.format("An error occurred while trying to find authorization detail approvals for domain: %s and user: %s",
                                domain.getId(), userId), ex)));
    }

    @Override
    public Flowable<AuthorizationDetailApproval> findByDomainAndUserAndClient(Domain domain, UserId userId, String clientId) {
        log.debug("Find authorization detail approvals by domain: {}, user: {} and client: {}", domain.getId(), userId, clientId);
        return dataPlaneRegistry.getAuthorizationDetailApprovalRepository(domain).findByDomainAndUserAndClient(domain.getId(), userId, clientId)
                .onErrorResumeNext(ex -> Flowable.error(new TechnicalManagementException(
                        String.format("An error occurred while trying to find authorization detail approvals for domain: %s, user: %s and client: %s",
                                domain.getId(), userId, clientId), ex)));
    }

    @Override
    public Completable saveConsent(Domain domain, Client client, User principal, AuthorizationDetails authorizationDetails,
                                   List<AuthorizationDetailApproval> approvals, boolean approved) {
        final var repository = dataPlaneRegistry.getAuthorizationDetailApprovalRepository(domain);
        return Observable.fromIterable(approvals).flatMapSingle(repository::upsert).ignoreElements()
                .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class)
                        .reference(domain.asReference())
                        .client(client)
                        .principal(principal)
                        .type(EventType.USER_CONSENT_CONSENTED)
                        .authorizationDetails(authorizationDetails, approved)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class)
                        .reference(domain.asReference())
                        .client(client)
                        .principal(principal)
                        .type(EventType.USER_CONSENT_CONSENTED)
                        .throwable(throwable)))
                .onErrorResumeNext(ex -> Completable.error(new TechnicalManagementException(
                        String.format("An error occurred while trying to save authorization detail consent for domain: %s and client: %s",
                                domain.getId(), client.getId()), ex)));
    }

    @Override
    public Completable purgeDataPlane(Domain domain, User principal) {
        return dataPlaneRegistry.getAuthorizationDetailApprovalRepository(domain).deleteByDomain(domain.getId());
    }
}
