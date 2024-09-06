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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.repository.management.api.CommonUserRepository.UpdateActions;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.impl.user.UserEnhancer;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserServiceImpl extends AbstractUserService implements UserService {

    @Lazy
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    protected TokenService tokenService;

    @Autowired
    private UserEnhancer userEnhancer;

    @Override
    protected UserRepository getUserRepository() {
        return this.userRepository;
    }

    @Override
    protected UserEnhancer getUserEnhancer() {
        return userEnhancer;
    }

    @Override
    public Flowable<User> findByDomain(String domain) {
        LOGGER.debug("Find users by domain: {}", domain);
        return userRepository.findAll(ReferenceType.DOMAIN, domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by domain {}", domain, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        return findAll(ReferenceType.DOMAIN, domain, page, size);
    }

    @Override
    public Maybe<User> findById(String id) {
        LOGGER.debug("Find user by id : {}", id);
        return userRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", id), ex));
                });
    }

    @Override
    public Maybe<User> findByDomainAndUsername(String domain, String username) {
        LOGGER.debug("Find user by username and domain: {} {}", username, domain);
        return userRepository.findByUsernameAndDomain(domain, username)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID: {} for the domain {}", username, domain, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s for the domain %s", username, domain), ex));
                });
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source) {
        return findByUsernameAndSource(ReferenceType.DOMAIN, domain, username, source);
    }

    @Override
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source, boolean includeLinkedIdentities) {
        LOGGER.debug("Find user by {} {}, username and source: {} {} (include linked idp: {})", referenceType, referenceId, username, source, includeLinkedIdentities);
        return getUserRepository().findByUsernameAndSource(referenceType, referenceId, username, source, includeLinkedIdentities)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its username: {} for the {} {}  and source {}", username, referenceType, referenceId, source, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its username: %s for the %s %s and source %s", username, referenceType, referenceId, source), ex));
                });
    }

    @Override
    public Single<User> create(String domain, NewUser newUser) {
        return create(ReferenceType.DOMAIN, domain, newUser);
    }


    @Override
    public Single<User> update(String domain, String id, UpdateUser updateUser) {
        return update(ReferenceType.DOMAIN, domain, id, updateUser);
    }

    @Override
    public Single<User> update(User user) {
        return update(user, UpdateActions.updateAll());
    }

    @Override
    public Single<User> update(User user, UpdateActions updateActions) {
        LOGGER.debug("Update a user {}", user);
        // updated date
        user.setUpdatedAt(new Date());
        return userValidator.validate(user).andThen(getUserRepository().update(user, updateActions)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                }));
    }

    @Override
    public Single<User> delete(String userId) {
        return super.delete(userId)
                .flatMap(user -> tokenService.deleteByUser((User) user).toSingleDefault(user));
    }

    @Override
    public Single<Long> countByDomain(String domain) {
        LOGGER.debug("Count user by domain {}", domain);

        return userRepository.countByReference(ReferenceType.DOMAIN, domain)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to count users by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while count users to delete user: %s", domain), ex));
                });
    }

    @Override
    public Single<Long> countByApplication(String domain, String application) {
        LOGGER.debug("Count user by application {}", application);

        return userRepository.countByApplication(domain, application).onErrorResumeNext(ex -> {
            if (ex instanceof AbstractManagementException) {
                return Single.error(ex);
            }
            LOGGER.error("An error occurs while trying to count users by application: {}", application, ex);
            return Single.error(new TechnicalManagementException(
                    String.format("An error occurs while count users to delete user: %s", application), ex));
        });
    }

    @Override
    public Single<Map<Object, Object>> statistics(AnalyticsQuery query) {
        LOGGER.debug("Get user collection analytics {}", query);

        return userRepository.statistics(query)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to get users analytics : {}", query, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while count users analytics : %s", query), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domain) {
        LOGGER.debug("Delete all users for domain {}", domain);
        return credentialService.deleteByReference(ReferenceType.DOMAIN, domain)
                .andThen(userRepository.deleteByReference(ReferenceType.DOMAIN, domain));
    }

    @Override
    public Completable removeFactor(String userId, String factorId, io.gravitee.am.identityprovider.api.User principal) {
        return findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapCompletable(oldUser -> {
                    if (oldUser.getFactors() == null) {
                        return Completable.complete();
                    }
                    List<EnrolledFactor> enrolledFactors = oldUser.getFactors()
                            .stream()
                            .filter(enrolledFactor -> !factorId.equals(enrolledFactor.getFactorId()))
                            .collect(Collectors.toList());
                    User userToUpdate = new User(oldUser);
                    userToUpdate.setFactors(enrolledFactors);
                    return update(userToUpdate)
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(oldUser)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).throwable(throwable)))
                            .ignoreElement();
                });
    }
}
