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
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
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
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final DataPlaneRegistry dataPlaneRegistry;

    @Autowired
    public PasswordHistoryService(@Lazy DataPlaneRegistry dataPlaneRegistry, AuditService auditService, @Named("argon2IdEncoder") PasswordEncoder passwordEncoder) {
        this.dataPlaneRegistry = dataPlaneRegistry;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Checks that a password has not already been used in the allotted history and adds it if it has not.
     * Returns a {@link PasswordHistory} instance on completion.
     *
     * @param domain         domain on which password entry has to be created
     * @param user           id of user for this password
     * @param rawPassword      unencrypted password provided by the user. Passed separate from the user object as its password is nulled after creation to avoid leakage.
     * @param principal        user performing this action
     * @param passwordPolicy domain/application password policy
     * @return Single containing {@link PasswordHistory} or an error if the password was already in the history.
     */
    public Maybe<PasswordHistory> addPasswordToHistory(Domain domain, io.gravitee.am.model.User user, String rawPassword, User principal, PasswordPolicy passwordPolicy) {
        LOGGER.debug("Adding password history entry for user {}", user);
        if (rawPassword == null || passwordPolicy == null || isNull(passwordPolicy.getPasswordHistoryEnabled()) || FALSE.equals(passwordPolicy.getPasswordHistoryEnabled())) {
            LOGGER.debug("Password history not added for user {} due to null password or settings, or because paswword history is disabled.", user.getUsername());
            return Maybe.empty();
        }
        final var repository = dataPlaneRegistry.getPasswordHistoryRepository(domain);
        return repository.findUserHistory(domain.asReference(), user.getId())
                         .toList()
                         .flatMap(passwordHistories -> {
                             if (passwordAlreadyUsed(rawPassword, passwordHistories)) {
                                 return Single.error(() -> PasswordHistoryException.passwordAlreadyInHistory(passwordPolicy));
                             }
                             int passwordCount = passwordHistories.size();
                             if (passwordCount >= passwordPolicy.getOldPasswords()) {
                                 passwordHistories.sort(comparing(PasswordHistory::getCreatedAt));
                                 return repository.delete(passwordHistories.get(0).getId())
                                                  .andThen(repository.create(getPasswordHistory(domain.asReference(), user, rawPassword)));
                             } else {
                                 return repository.create(getPasswordHistory(domain.asReference(), user, rawPassword));
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
     * @param domain         domain on which password entry should be found
     * @param userId           id of user for this password
     * @param password         the password to add
     * @param passwordPolicy domain/application password settings
     * @return Single containing a {@link Boolean} {@code true} if the password is already present the allotted number of previous passwords.
     */
    public Single<Boolean> passwordAlreadyUsed(Domain domain, String userId, String password, PasswordPolicy passwordPolicy) {
        LOGGER.debug("Checking password history for user {}", userId);
        if (passwordPolicy == null || isNull(passwordPolicy.getPasswordHistoryEnabled()) || FALSE.equals(passwordPolicy.getPasswordHistoryEnabled())) {
            return Single.just(false);
        }
        final var repository = dataPlaneRegistry.getPasswordHistoryRepository(domain);
        return repository.findUserHistory(domain.asReference(), userId)
                         .toList()
                         .flatMap(passwordHistories -> Single.just(passwordAlreadyUsed(password, passwordHistories)));
    }

    /**
     * Returns a user's password history.
     *
     * @param domain         domain on which password entry should be found
     * @param userId           id of user for this password
     * @return Flowable containing the user's password history.
     */
    public Flowable<PasswordHistory> findUserHistory(Domain domain, String userId) {
        final var repository = dataPlaneRegistry.getPasswordHistoryRepository(domain);
        return repository.findUserHistory(domain.asReference(), userId);
    }

    /**
     * Find all password history for an application or domain.
     *
     * @param domain domain on which password entry should be found
     * @return Flowable containing password histories, if any, for the referenced entity.
     */
    public Flowable<PasswordHistory> findByReference(Domain domain) {
        LOGGER.debug("Find password histories by domain id {}", domain.getId());
        final var repository = dataPlaneRegistry.getPasswordHistoryRepository(domain);
        return repository.findByReference(domain.asReference())
                         .onErrorResumeNext(ex -> {
                             LOGGER.error("Error finding password histories by domain id {}", domain.getId(), ex);
                             return Flowable.error(new TechnicalManagementException(
                                     String.format("Error finding password histories by domain id %s", domain.getId()), ex));
                         });
    }

    /**
     * Delete all password history for an application or domain.
     *
     * @param domain         domain on which password entry should be found
     * @return Completable that indicates a successful delete operation.
     */
    public Completable deleteByReference(Domain domain) {
        final var repository = dataPlaneRegistry.getPasswordHistoryRepository(domain);
        return repository.deleteByReference(domain.asReference());
    }

    /**
     * Delete all password history for a user.
     *
     * @param domain         domain on which password entry should be found
     * @param userId unique ID of the user
     * @return Completable that indicates a successful delete operation.
     */
    public Completable deleteByUser(Domain domain, String userId) {
        final var repository = dataPlaneRegistry.getPasswordHistoryRepository(domain);
        return repository.deleteByUserId(userId);
    }

    private boolean passwordAlreadyUsed(String password, List<PasswordHistory> passwordHistories) {
        return passwordHistories.stream()
                                .anyMatch(passwordHistory -> passwordEncoder.matches(password, passwordHistory.getPassword()));
    }

    private PasswordHistory getPasswordHistory(Reference reference, io.gravitee.am.model.User user, CharSequence rawPassword) {
        PasswordHistory item = new PasswordHistory();
        item.setUserId(user.getId());
        item.setPassword(passwordEncoder.encode(rawPassword));
        item.setCreatedAt(new Date());
        item.setReferenceType(reference.type());
        item.setReferenceId(reference.id());
        return item;
    }
}
