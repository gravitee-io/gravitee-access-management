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
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ScopeApprovalNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.UserConsentAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ScopeApprovalServiceImpl implements ScopeApprovalService {

    public static final Logger LOGGER = LoggerFactory.getLogger(ScopeApprovalServiceImpl.class);

    @Lazy
    @Autowired
    private ScopeApprovalRepository scopeApprovalRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<ScopeApproval> findById(String id) {
        LOGGER.debug("Find scope approval by id: {}", id);
        return scopeApprovalRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a scope approval by id: {}", id);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a scope approval by id %s", id), ex));
                });
    }

    @Override
    public Single<Set<ScopeApproval>> findByDomainAndUser(String domain, String user) {
        LOGGER.debug("Find scope approvals by domain: {} and user: {}", domain, user);
        return scopeApprovalRepository.findByDomainAndUser(domain, user)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a scope approval for domain: {} and user: {}", domain, user);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a scope approval for domain: %s and user: %s", domain, user), ex));
                });
    }

    @Override
    public Single<Set<ScopeApproval>> findByDomainAndUserAndClient(String domain, String user, String client) {
        LOGGER.debug("Find scope approvals by domain: {} and user: {} and client: {}", domain, user);
        return scopeApprovalRepository.findByDomainAndUserAndClient(domain, user, client)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a scope approval for domain: {}, user: {} and client: {}", domain, user, client);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a scope approval for domain: %s, user: %s and client: %s", domain, user, client), ex));
                });
    }

    @Override
    public Single<List<ScopeApproval>> saveConsent(String domain, Client client, List<ScopeApproval> approvals, User principal) {
        LOGGER.debug("Save approvals for user: {}", approvals.get(0).getUserId());
        return Observable.fromIterable(approvals)
                .flatMapSingle(approval -> scopeApprovalRepository.upsert(approval))
                .toList()
                .doOnSuccess(__ -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).domain(domain).client(client).principal(principal).type(EventType.USER_CONSENT_CONSENTED).approvals(approvals)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).domain(domain).client(client).principal(principal).type(EventType.USER_CONSENT_CONSENTED).throwable(throwable)))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to save consent for domain: {}, client: {} and user: {} ", domain, client.getId(), approvals.get(0).getUserId());
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to save consent for domain: %s, client: %s and user: %s", domain, client.getId(), approvals.get(0).getUserId()), ex));
                });
    }

    @Override
    public Completable revokeByConsent(String domain, String userId, String consentId, User principal) {
        LOGGER.debug("Revoke approval for consent: {} and user: {}", consentId, userId);

        return userService.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapCompletable(user -> scopeApprovalRepository.findById(consentId)
                        .switchIfEmpty(Maybe.error(new ScopeApprovalNotFoundException(consentId)))
                        .flatMapCompletable(scopeApproval -> scopeApprovalRepository.delete(consentId)
                                .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).type(EventType.USER_CONSENT_REVOKED).domain(domain).principal(principal).user(user).approvals(Collections.singleton(scopeApproval))))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).type(EventType.USER_CONSENT_REVOKED).domain(domain).principal(principal).user(user).throwable(throwable))))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to revoke approval for scope: {}", consentId);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to revoke approval for scope: %s", consentId), ex));
                });
    }

    @Override
    public Completable revokeByUser(String domain, String user, User principal) {
        LOGGER.debug("Revoke approvals for domain: {} and user: {}", domain, user);
        return userService.findById(user)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(user)))
                .flatMapCompletable(user1 -> scopeApprovalRepository.findByDomainAndUser(domain, user)
                        .flatMapCompletable(scopeApprovals -> scopeApprovalRepository.deleteByDomainAndUser(domain, user)
                                .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).type(EventType.USER_CONSENT_REVOKED).domain(domain).principal(principal).user(user1).approvals(scopeApprovals)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).type(EventType.USER_CONSENT_REVOKED).domain(domain).principal(principal).user(user1).throwable(throwable))))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to revoke scope approvals for domain: {} and user : {}", domain, user);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to revoke scope approvals for domain: %s and user: %s", domain, user), ex));
                });
    }

    @Override
    public Completable revokeByUserAndClient(String domain, String user, String clientId, User principal) {
        LOGGER.debug("Revoke approvals for domain: {}, user: {} and client: {}", domain, user, clientId);
        return userService.findById(user)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(user)))
                .flatMapCompletable(user1 -> scopeApprovalRepository.findByDomainAndUserAndClient(domain, user, clientId)
                        .flatMapCompletable(scopeApprovals -> scopeApprovalRepository.deleteByDomainAndUserAndClient(domain, user, clientId)
                                .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).type(EventType.USER_CONSENT_REVOKED).domain(domain).principal(principal).user(user1).approvals(scopeApprovals)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserConsentAuditBuilder.class).type(EventType.USER_CONSENT_REVOKED).domain(domain).principal(principal).user(user1).throwable(throwable))))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to revoke scope approvals for domain: {}, user: {} and client: {}", domain, user, clientId);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to revoke scope approvals for domain: %s, user: %s and client: %s", domain, user, clientId), ex));
                });

    }
}
