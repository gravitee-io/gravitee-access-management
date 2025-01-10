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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.MovingFactorUtils;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.management.service.CommonUserService;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.RateLimiterService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.am.service.VerifyAttemptService;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.model.ReferenceType.DOMAIN;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractUserService<T extends io.gravitee.am.service.CommonUserService> implements CommonUserService {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected IdentityProviderManager identityProviderManager;

    @Autowired
    protected PasswordService passwordService;

    @Autowired
    protected UserValidator userValidator;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected MembershipService membershipService;

    @Autowired
    protected UserActivityService userActivityService;

    @Autowired
    protected RateLimiterService rateLimiterService;

    @Autowired
    protected PasswordHistoryService passwordHistoryService;

    @Autowired
    protected VerifyAttemptService verifyAttemptService;

    @Autowired
    protected CredentialService credentialService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    protected abstract BiFunction<String, String, Maybe<Application>> checkClientFunction();

    protected abstract T getUserService();

    @Override
    public Single<User> findById(ReferenceType referenceType, String referenceId, String id) {
        return getUserService().findById(referenceType, referenceId, id);
    }

    @Override
    public Single<User> update(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal) {
        return this.updateUser(referenceType, referenceId, id, updateUser, principal);
    }

    private Single<User> updateUser(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal) {
        return getUserService().findById(referenceType, referenceId, id).flatMap(user -> {
                    // This handles identity providers not enforcing email
                    final boolean validateEmailIfNecessary = !(isNullOrEmpty(user.getEmail()) && isNullOrEmpty(updateUser.getEmail()));
                    return userValidator.validate(updateUser, validateEmailIfNecessary).andThen(Single.just(user));
                })
                .flatMap(user -> updateWithProviderIfNecessary(updateUser, user))
                .flatMap(user -> getUserService().update(referenceType, referenceId, id, user)
                        .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).oldValue(user).user(user1)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).throwable(throwable))));
    }

    private Single<UpdateUser> updateWithProviderIfNecessary(UpdateUser updateUser, User user) {
        final Maybe<UserProvider> userProvider = identityProviderManager.getUserProvider(user.getSource());
        return userProvider.isEmpty().flatMap(noProvider -> noProvider ?
                Single.just(updateUser) :
                updateWithUserProvider(updateUser, user, userProvider.toSingle())
        );
    }

    private Single<UpdateUser> updateWithUserProvider(UpdateUser updateUser, User
            user, Single<UserProvider> userProvider) {
        return userProvider.flatMap(provider -> provider.findByUsername(user.getUsername())
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(user.getUsername())))
                .flatMap(idpUser -> provider.update(idpUser.getId(), convert(user.getUsername(), updateUser)))
                .map(idpUser -> {
                    updateUser.setExternalId(idpUser.getId());
                    return updateUser;
                })).onErrorResumeNext(ex -> {
            if (ex instanceof UserNotFoundException) {
                // idp user does not exist, return the updated user to save in AM
                return Single.just(updateUser);
            }
            return Single.error(ex);
        });
    }

    @Override
    public Single<User> updateStatus(ReferenceType referenceType, String referenceId, String userId, boolean status, io.
            gravitee.am.identityprovider.api.User principal) {
        return getUserService().findById(referenceType, referenceId, userId)
                .flatMap(user -> {
                    user.setEnabled(status);
                    return getUserService().update(user);
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type((status ? EventType.USER_ENABLED : EventType.USER_DISABLED)).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type((status ? EventType.USER_ENABLED : EventType.USER_DISABLED)).throwable(throwable)));
    }

    @Override
    public Single<User> updateUsername(ReferenceType referenceType, String referenceId, String id, String
            username, io.gravitee.am.identityprovider.api.User principal) {
        final AtomicReference<String> oldUsername = new AtomicReference<>();
        return userValidator.validateUsername(username).andThen(Single.defer(() ->
                getUserService().findById(referenceType, referenceId, id).flatMap(user -> getUserService()
                                .findByUsernameAndSource(referenceType, referenceId, username, user.getSource())
                                //If the user is empty we throw a UserNotFoundException to allow the update
                                .switchIfEmpty(Single.error(() -> new UserNotFoundException(referenceId, username)))
                                //If the user is not empty we throw a InvalidUserException to prevent username update
                                .flatMap(existingUser -> Single.<User>error(new InvalidUserException(String.format("User with username [%s] and idp [%s] already exists", username, user.getSource()))))
                                .onErrorResumeNext(ex -> {
                                    if (ex instanceof UserNotFoundException) {
                                        return Single.just(user);
                                    }
                                    return Single.error(ex);
                                })
                        ).flatMap(user -> identityProviderManager.getUserProvider(user.getSource())
                                .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(user.getSource())))
                                .flatMap(userProvider -> userProvider.findByUsername(user.getUsername())
                                        .switchIfEmpty(Single.error(() -> new UserNotFoundException()))
                                        .flatMap(idpUser -> userProvider.updateUsername(idpUser, username))
                                        .flatMap(idpUser -> {
                                            oldUsername.set(user.getUsername());
                                            return updateCredentialUsername(referenceType, referenceId, oldUsername.get(), idpUser);
                                        })
                                        .flatMap(idpUser -> {
                                            user.updateUsername(username);

                                            // Generate a new moving factor based on user id instead of username. Necessary
                                            // since username can be changed.
                                            generateNewMovingFactorBasedOnUserId(user);

                                            return getUserService().update(user).onErrorResumeNext(ex -> {
                                                // In the case we cannot update on our side, we rollback the username on the iDP and these credentials
                                                ((DefaultUser) idpUser).setUsername(oldUsername.get());
                                                return userProvider.updateUsername(idpUser, idpUser.getUsername())
                                                        .flatMap(idpUser1 -> updateCredentialUsername(referenceType, referenceId, idpUser1.getUsername(), oldUsername.get()))
                                                        .flatMap(rolledBackUser -> Single.error(ex));
                                            });
                                        })
                                ))
                        .doOnSuccess(user1 -> {
                            auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USERNAME_UPDATED).user(user1));
                            if (DOMAIN.equals(referenceType)) {
                                loginAttemptService.reset(createLoginAttemptCriteria(referenceId, oldUsername.get()))
                                        .onErrorResumeNext(error -> {
                                            logger.warn("Could not delete login attempt {}", error.getMessage());
                                            return Completable.complete();
                                        })
                                        .subscribe();
                            }
                        })
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USERNAME_UPDATED).throwable(throwable)))
        ));
    }

    @SuppressWarnings("ReactiveStreamsUnusedPublisher")
    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String
            userId, io.gravitee.am.identityprovider.api.User principal) {
        return getUserService().findById(referenceType, referenceId, userId)
                .flatMapCompletable(user -> identityProviderManager.getUserProvider(user.getSource())
                        .map(Optional::ofNullable)
                        .flatMapCompletable(optUserProvider -> {
                            // no user provider found, continue
                            if (!optUserProvider.isPresent()) {
                                return Completable.complete();
                            }
                            // user has never been created in the identity provider, continue
                            if (user.getExternalId() == null || user.getExternalId().isEmpty()) {
                                return Completable.complete();
                            }
                            return optUserProvider.get().delete(user.getExternalId())
                                    .onErrorResumeNext(ex -> {
                                        if (ex instanceof UserNotFoundException) {
                                            // idp user does not exist, continue
                                            return Completable.complete();
                                        }
                                        return Completable.error(ex);
                                    });
                        })
                        // Delete trace of user activity
                        .andThen((DOMAIN.equals(referenceType)) ? userActivityService.deleteByDomainAndUser(referenceId, userId) : Completable.complete())
                        // Delete rate limit
                        .andThen(rateLimiterService.deleteByUser(user))
                        .andThen(verifyAttemptService.deleteByUser(user))
                        .andThen(getUserService().delete(userId))
                        // remove from memberships if user is an administrative user
                        .andThen((ReferenceType.ORGANIZATION != referenceType) ? Completable.complete() :
                                membershipService.findByMember(userId, MemberType.USER)
                                        .flatMapCompletable(membership -> membershipService.delete(membership.getId())))
                        .andThen(passwordHistoryService.deleteByUser(userId))
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_DELETED).user(user)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_DELETED).throwable(throwable)))
                );
    }

    protected io.gravitee.am.identityprovider.api.User convert(NewUser newUser) {
        DefaultUser user = new DefaultUser(newUser.getUsername());
        user.setCredentials(newUser.getPassword());

        Map<String, Object> additionalInformation = new HashMap<>();
        if (newUser.getFirstName() != null) {
            user.setFirstName(newUser.getFirstName());
            additionalInformation.put(StandardClaims.GIVEN_NAME, newUser.getFirstName());
        }
        if (newUser.getLastName() != null) {
            user.setLastName(newUser.getLastName());
            additionalInformation.put(StandardClaims.FAMILY_NAME, newUser.getLastName());
        }
        if (newUser.getEmail() != null) {
            user.setEmail(newUser.getEmail());
            additionalInformation.put(StandardClaims.EMAIL, newUser.getEmail());
        }
        if (newUser.getAdditionalInformation() != null) {
            newUser.getAdditionalInformation().forEach(additionalInformation::putIfAbsent);
        }
        user.setAdditionalInformation(additionalInformation);

        return user;
    }

    protected User transform(NewUser newUser, ReferenceType referenceType, String referenceId) {
        User user = new User();
        user.setId(RandomString.generate());
        user.setExternalId(newUser.getExternalId());
        user.setReferenceId(referenceId);
        user.setReferenceType(referenceType);
        user.setClient(newUser.getClient());
        user.setEnabled(newUser.isEnabled());
        user.setUsername(newUser.getUsername());
        user.setFirstName(newUser.getFirstName());
        user.setLastName(newUser.getLastName());
        user.setEmail(newUser.getEmail());
        user.setSource(newUser.getSource());
        user.setInternal(newUser.isInternal());
        user.setPreRegistration(newUser.isPreRegistration());
        user.setRegistrationCompleted(newUser.isRegistrationCompleted());
        user.setPreferredLanguage(newUser.getPreferredLanguage());
        user.setAdditionalInformation(newUser.getAdditionalInformation());
        user.setCreatedAt(new Date());
        user.setUpdatedAt(user.getCreatedAt());
        user.setLastPasswordReset(user.getCreatedAt());
        return user;
    }

    protected void updateInfos(User user, NewUser newUser) {
        user.setFirstName(newUser.getFirstName());
        user.setLastName(newUser.getLastName());
        user.setEmail(newUser.getEmail());
        user.setAdditionalInformation(newUser.getAdditionalInformation());
    }

    protected io.gravitee.am.identityprovider.api.User convert(String username, UpdateUser updateUser) {
        // update additional information
        DefaultUser user = new DefaultUser(username);
        Map<String, Object> additionalInformation = new HashMap<>();
        if (updateUser.getFirstName() != null) {
            user.setFirstName(updateUser.getFirstName());
            additionalInformation.put(StandardClaims.GIVEN_NAME, updateUser.getFirstName());
        }
        if (updateUser.getLastName() != null) {
            user.setLastName(updateUser.getLastName());
            additionalInformation.put(StandardClaims.FAMILY_NAME, updateUser.getLastName());
        }
        if (updateUser.getEmail() != null) {
            user.setEmail(updateUser.getEmail());
            additionalInformation.put(StandardClaims.EMAIL, updateUser.getEmail());
        }
        if (updateUser.getAdditionalInformation() != null) {
            updateUser.getAdditionalInformation().forEach(additionalInformation::putIfAbsent);
        }
        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    protected io.gravitee.am.identityprovider.api.User convert(User user) {
        DefaultUser idpUser = new DefaultUser(user.getUsername());
        idpUser.setCredentials(user.getPassword());

        Map<String, Object> additionalInformation = new HashMap<>();
        if (user.getFirstName() != null) {
            idpUser.setFirstName(user.getFirstName());
            additionalInformation.put(StandardClaims.GIVEN_NAME, user.getFirstName());
        }
        if (user.getLastName() != null) {
            idpUser.setLastName(user.getLastName());
            additionalInformation.put(StandardClaims.FAMILY_NAME, user.getLastName());
        }
        if (user.getEmail() != null) {
            idpUser.setEmail(user.getEmail());
            additionalInformation.put(StandardClaims.EMAIL, user.getEmail());
        }
        if (user.getAdditionalInformation() != null) {
            user.getAdditionalInformation().forEach((k, v) -> additionalInformation.putIfAbsent(k, v));
        }
        idpUser.setAdditionalInformation(additionalInformation);
        return idpUser;
    }

    private LoginAttemptCriteria createLoginAttemptCriteria(String domainId, String username) {
        return new LoginAttemptCriteria.Builder()
                .domain(domainId)
                .username(username)
                .build();
    }

    private Single<io.gravitee.am.identityprovider.api.User> updateCredentialUsername(ReferenceType
                                                                                              referenceType, String referenceId, String oldUsername, io.gravitee.am.identityprovider.api.User user) {
        return updateCredentialUsername(referenceType, referenceId, oldUsername, user.getUsername())
                .flatMap(__ -> Single.just(user));
    }

    private Single<String> updateCredentialUsername(ReferenceType referenceType, String referenceId, String
            oldUsername, String newUsername) {
        return credentialService.findByUsername(referenceType, referenceId, oldUsername)
                .map(credential -> {
                    credential.setUsername(newUsername);
                    return credentialService.update(credential).subscribe();
                })
                .toList()
                .flatMapMaybe(singles -> Maybe.just(newUsername))
                .toSingle();
    }

    private void generateNewMovingFactorBasedOnUserId(User user) {

        Optional.ofNullable(user.getFactors()).ifPresent(enrolledFactors ->

                user.getFactors()
                        .stream()
                        .filter(enrolledFactor -> Optional.ofNullable(enrolledFactor.getSecurity()).isPresent())
                        .forEach(enrolledFactor -> {

                            final var additionalData = enrolledFactor.getSecurity().getAdditionalData();

                            if (additionalData.containsKey(FactorDataKeys.KEY_MOVING_FACTOR)) {
                                additionalData.put(
                                        FactorDataKeys.KEY_MOVING_FACTOR,
                                        MovingFactorUtils.generateInitialMovingFactor(user.getId())
                                );
                                enrolledFactor.setUpdatedAt(new Date());
                            }
                        })
        );
    }
}
