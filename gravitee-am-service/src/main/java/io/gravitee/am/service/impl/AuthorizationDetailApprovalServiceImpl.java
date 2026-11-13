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
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthorizationDetailApprovalService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthorizationDetailApprovalNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.UserConsentAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

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
    public Single<CursorPage<AuthorizationDetailApproval, AuthorizationDetailApprovalCursorRequest>> findByDomainAndUser(
            Domain domain, UserId userId, AuthorizationDetailApprovalCursorRequest cursor, int limit) {
        log.debug("Find authorization detail approvals by domain: {} and user: {}", domain.getId(), userId);
        return dataPlaneRegistry.getAuthorizationDetailApprovalRepository(domain).findByDomainAndUser(domain.getId(), userId, cursor, limit)
                .onErrorResumeNext(ex -> Single.error(new TechnicalManagementException(
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
        return Completable.fromAction(() -> requireTypeAndStatus(approvals))
                .andThen(Observable.fromIterable(approvals).flatMapSingle(repository::upsert).ignoreElements())
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

    private static void requireTypeAndStatus(List<AuthorizationDetailApproval> approvals) {
        for (AuthorizationDetailApproval approval : approvals) {
            if (approval.getType() == null || approval.getStatus() == null) {
                throw new IllegalArgumentException("An authorization detail approval must have a type and a status");
            }
        }
    }

    @Override
    public Completable revokeByConsent(Domain domain, UserId userId, String consentId,
                                       BiFunction<Domain, RevokeToken, Completable> revokeTokenProcessor, User principal) {
        log.debug("Revoke authorization detail approval: {} of user: {}", consentId, userId);
        final var repository = dataPlaneRegistry.getAuthorizationDetailApprovalRepository(domain);
        return findUser(domain, userId)
                .flatMapCompletable(user -> repository.findById(consentId)
                        .filter(approval -> isOwnedBy(approval, user.getFullId()))
                        .switchIfEmpty(Maybe.error(new AuthorizationDetailApprovalNotFoundException(consentId)))
                        .flatMapCompletable(approval -> auditedRevocation(domain, user, principal, List.of(approval), repository.delete(consentId))
                                .andThen(revokeTokenProcessor.apply(domain, RevokeToken.byUserAndClientId(domain, approval.getClientId(),
                                        user.getId(), user.getUsername(), principal.getId(), principal.getUsername())))))
                .onErrorResumeNext(ex -> toRevokeError(ex, String.format(
                        "An error occurred while trying to revoke authorization detail approval: %s of user: %s", consentId, userId)));
    }

    @Override
    public Completable revokeByUserAndClient(Domain domain, UserId userId, String clientId,
                                             BiFunction<Domain, RevokeToken, Completable> revokeTokenProcessor, User principal) {
        log.debug("Revoke authorization detail approvals of user: {} for domain: {} and client: {}", userId, domain.getId(), clientId);
        final var repository = dataPlaneRegistry.getAuthorizationDetailApprovalRepository(domain);
        return findUser(domain, userId)
                .flatMapCompletable(user -> repository.findByDomainAndUserAndClient(domain.getId(), user.getFullId(), clientId).toList()
                        .flatMapCompletable(approvals -> auditedRevocation(domain, user, principal, approvals,
                                repository.deleteByDomainAndUserAndClient(domain.getId(), user.getFullId(), clientId)))
                        .andThen(revokeTokenProcessor.apply(domain, RevokeToken.byUserAndClientId(domain, clientId,
                                user.getId(), user.getUsername(), principal.getId(), principal.getUsername()))))
                .onErrorResumeNext(ex -> toRevokeError(ex, String.format(
                        "An error occurred while trying to revoke authorization detail approvals of user: %s for domain: %s and client: %s",
                        userId, domain.getId(), clientId)));
    }

    @Override
    public Completable revokeByUser(Domain domain, UserId userId,
                                    BiFunction<Domain, RevokeToken, Completable> revokeTokenProcessor, User principal) {
        log.debug("Revoke authorization detail approvals of user: {} for domain: {}", userId, domain.getId());
        final var repository = dataPlaneRegistry.getAuthorizationDetailApprovalRepository(domain);
        return findUser(domain, userId)
                .flatMapCompletable(user -> repository.findByDomainAndUser(domain.getId(), user.getFullId()).toList()
                        .flatMapCompletable(approvals -> auditedRevocation(domain, user, principal, approvals,
                                repository.deleteByDomainAndUser(domain.getId(), user.getFullId())))
                        .andThen(revokeTokenProcessor.apply(domain, RevokeToken.byUser(domain,
                                user.getId(), user.getUsername(), principal.getId(), principal.getUsername()))))
                .onErrorResumeNext(ex -> toRevokeError(ex, String.format(
                        "An error occurred while trying to revoke authorization detail approvals of user: %s for domain: %s", userId, domain.getId())));
    }

    private Maybe<io.gravitee.am.model.User> findUser(Domain domain, UserId userId) {
        return dataPlaneRegistry.getUserRepository(domain).findById(domain.asReference(), userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)));
    }

    private Completable auditedRevocation(Domain domain, io.gravitee.am.model.User user, User principal,
                                          Collection<AuthorizationDetailApproval> approvals, Completable deletion) {
        if (approvals.isEmpty()) {
            return deletion;
        }
        return deletion
                .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class)
                        .type(EventType.USER_CONSENT_REVOKED)
                        .reference(domain.asReference())
                        .principal(principal)
                        .user(user)
                        .authorizationDetails(approvals)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class)
                        .type(EventType.USER_CONSENT_REVOKED)
                        .reference(domain.asReference())
                        .principal(principal)
                        .user(user)
                        .throwable(throwable)));
    }

    private static boolean isOwnedBy(AuthorizationDetailApproval approval, UserId userId) {
        UserId owner = approval.getUserId();
        if (owner.id() != null && owner.id().equals(userId.id())) {
            return true;
        }
        return owner.hasExternal() && owner.externalId().equals(userId.externalId()) && owner.source().equals(userId.source());
    }

    private static Completable toRevokeError(Throwable ex, String message) {
        if (ex instanceof AbstractManagementException) {
            return Completable.error(ex);
        }
        return Completable.error(new TechnicalManagementException(message, ex));
    }

    @Override
    public Completable purgeDataPlane(Domain domain, User principal) {
        return dataPlaneRegistry.getAuthorizationDetailApprovalRepository(domain).deleteByDomain(domain.getId());
    }
}
