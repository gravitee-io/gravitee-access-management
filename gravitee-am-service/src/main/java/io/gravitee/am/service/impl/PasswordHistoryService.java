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
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.PasswordHistoryRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.exception.PasswordHistoryException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

import static java.lang.Boolean.FALSE;
import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;

/**
 * Service providing password history.
 */
@Component
public class PasswordHistoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordHistoryService.class);
    private final PasswordHistoryRepository repository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public PasswordHistoryService(@Lazy PasswordHistoryRepository repository, AuditService auditService, @Named("argon2IdEncoder") PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Checks that a password has not already been used in the allotted history and adds it if it has not.
     * Returns a {@link PasswordHistory} instance on completion.
     *
     * @param referenceType    the reference type (DOMAIN, APPLICATION)
     * @param referenceId      id of the reference
     * @param user           id of user for this password
     * @param rawPassword      unencrypted password provided by the user. Passed separate from the user object as its password is nulled after creation to avoid leakage.
     * @param principal        user performing this action
     * @param passwordPolicy domain/application password policy
     * @return Single containing {@link PasswordHistory} or an error if the password was already in the history.
     */
    public Maybe<PasswordHistory> addPasswordToHistory(ReferenceType referenceType, String referenceId, io.gravitee.am.model.User user, String rawPassword, User principal, PasswordPolicy passwordPolicy) {
        LOGGER.debug("Adding password history entry for user {}", user);
        if (rawPassword == null || passwordPolicy == null || isNull(passwordPolicy.getPasswordHistoryEnabled()) || FALSE.equals(passwordPolicy.getPasswordHistoryEnabled())) {
            LOGGER.debug("Password history not added for user {} due to null password or settings, or because paswword history is disabled.", user.getUsername());
            return Maybe.empty();
        }
        return repository.findUserHistory(referenceType, referenceId, user.getId())
                         .toList()
                         .flatMap(passwordHistories -> {
                             if (passwordAlreadyUsed(rawPassword, passwordHistories)) {
                                 return Single.error(() -> PasswordHistoryException.passwordAlreadyInHistory(passwordPolicy));
                             }
                             int passwordCount = passwordHistories.size();
                             if (passwordCount >= passwordPolicy.getOldPasswords()) {
                                 passwordHistories.sort(comparing(PasswordHistory::getCreatedAt));
                                 return repository.delete(passwordHistories.get(0).getId())
                                                  .andThen(repository.create(getPasswordHistory(referenceType, referenceId, user, rawPassword)));
                             } else {
                                 return repository.create(getPasswordHistory(referenceType, referenceId, user, rawPassword));
                             }
                         })
                         .doOnSuccess(a -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                                                                           .user(user)
                                                                           .principal(principal)
                                                                           .type(EventType.PASSWORD_HISTORY_CREATED)))
                         .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                                                                                 .user(user)
                                                                                 .principal(principal)
                                                                                 .type(EventType.PASSWORD_HISTORY_CREATED)
                                                                                 .throwable(throwable))).toMaybe();

    }

    /**
     * Checks if a password is already in the history.
     *
     * @param referenceType    the reference type (DOMAIN, APPLICATION)
     * @param referenceId      id of the reference
     * @param userId           id of user for this password
     * @param password         the password to add
     * @param passwordPolicy domain/application password settings
     * @return Single containing a {@link Boolean} {@code true} if the password is already present the allotted number of previous passwords.
     */
    public Single<Boolean> passwordAlreadyUsed(ReferenceType referenceType, String referenceId, String userId, String password, PasswordPolicy passwordPolicy) {
        LOGGER.debug("Checking password history for user {}", userId);
        if (passwordPolicy == null || isNull(passwordPolicy.getPasswordHistoryEnabled()) || FALSE.equals(passwordPolicy.getPasswordHistoryEnabled())) {
            return Single.just(false);
        }
        return repository.findUserHistory(referenceType, referenceId, userId)
                         .toList()
                         .flatMap(passwordHistories -> Single.just(passwordAlreadyUsed(password, passwordHistories)));
    }

    /**
     * Returns a user's password history.
     *
     * @param referenceType    the reference type (DOMAIN, APPLICATION)
     * @param referenceId      id of the reference
     * @param userId           id of user for this password
     * @return Flowable containing the user's password history.
     */
    public Flowable<PasswordHistory> findUserHistory(ReferenceType referenceType, String referenceId, String userId) {
        return repository.findUserHistory(referenceType, referenceId, userId);
    }

    /**
     * Find all password history for an application or domain.
     *
     * @param referenceType type of reference (e.g. DOMAIN, APPLICATION)
     * @param referenceId ID of the reference
     * @return Flowable containing password histories, if any, for the referenced entity.
     */
    public Flowable<PasswordHistory> findByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("Find password histories by reference id {} and reference type {}", referenceId, referenceType);
        return repository.findByReference(referenceType, referenceId)
                         .onErrorResumeNext(ex -> {
                             LOGGER.error("Error finding password histories by reference id {} and reference type {}", referenceId, referenceType, ex);
                             return Flowable.error(new TechnicalManagementException(
                                     String.format("Error finding password histories by reference id %s and reference type %s", referenceId, referenceType), ex));
                         });
    }

    /**
     * Delete all password history for an application or domain.
     *
     * @param referenceType type of reference (e.g. DOMAIN, APPLICATION)
     * @param referenceId ID of the reference
     * @return Completable that indicates a successful delete operation.
     */
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        return repository.deleteByReference(referenceType, referenceId);
    }

    /**
     * Delete all password history for a user.
     *
     * @param userId unique ID of the user
     * @return Completable that indicates a successful delete operation.
     */
    public Completable deleteByUser(String userId) {
        return repository.deleteByUserId(userId);
    }

    private boolean passwordAlreadyUsed(String password, List<PasswordHistory> passwordHistories) {
        return passwordHistories.stream()
                                .anyMatch(passwordHistory -> passwordEncoder.matches(password, passwordHistory.getPassword()));
    }

    private PasswordHistory getPasswordHistory(ReferenceType referenceType, String referenceId, io.gravitee.am.model.User user, CharSequence rawPassword) {
        PasswordHistory item = new PasswordHistory();
        item.setUserId(user.getId());
        item.setPassword(passwordEncoder.encode(rawPassword));
        item.setCreatedAt(new Date());
        item.setReferenceType(referenceType);
        item.setReferenceId(referenceId);
        return item;
    }
}
