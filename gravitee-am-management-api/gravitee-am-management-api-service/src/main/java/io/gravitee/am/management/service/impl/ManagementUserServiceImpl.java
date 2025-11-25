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

import io.gravitee.am.business.user.UpdateUserRule;
import io.gravitee.am.business.user.UpdateUsernameDomainRule;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.ManagementUserService;
import io.gravitee.am.management.service.RevokeTokenManagementService;
import io.gravitee.am.management.service.dataplane.CredentialManagementService;
import io.gravitee.am.management.service.dataplane.LoginAttemptManagementService;
import io.gravitee.am.management.service.dataplane.UserActivityManagementService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.PasswordPolicyService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.model.AbstractNewUser;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.utils.UserFactorUpdater;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.service.utils.UserProfileUtils.buildDisplayName;
import static io.gravitee.am.service.utils.UserProfileUtils.hasGeneratedDisplayName;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementUserService")
@Slf4j
public class ManagementUserServiceImpl implements ManagementUserService {

    private static final String DEFAULT_IDP_PREFIX = "default-idp-";

    @Value("${user.registration.token.expire-after:86400}")
    private Integer expireAfter;

    @Autowired
    private EmailService emailService;

    @Autowired
    @Qualifier("managementJwtBuilder")
    private JWTBuilder jwtBuilder;

    @Autowired
    private LoginAttemptManagementService loginAttemptService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Autowired
    protected RevokeTokenManagementService tokenService;

    @Autowired
    protected PasswordPolicyService passwordPolicyService;

    @Autowired
    protected CredentialManagementService credentialService;

    @Autowired
    protected UserActivityManagementService userActivityService;

    @Autowired
    protected IdentityProviderManager identityProviderManager;

    @Autowired
    protected PasswordService passwordService;

    @Autowired
    protected UserValidator userValidator;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected PasswordHistoryService passwordHistoryService;
    @Autowired
    private EventService eventService;

    @Override
    public Single<Page<User>> search(Domain domain, String query, int page, int size) {
        return dataPlaneRegistry.getUserRepository(domain).search(domain.asReference(), query, page, size);
    }

    @Override
    public Single<Page<User>> search(Domain domain, FilterCriteria filterCriteria, int page, int size) {
        return dataPlaneRegistry.getUserRepository(domain).search(domain.asReference(), filterCriteria, page, size);
    }

    @Override
    public Single<Page<User>> findAll(Domain domain, int page, int size) {
        return dataPlaneRegistry.getUserRepository(domain).findAll(domain.asReference(), page, size);
    }

    @Override
    public Maybe<User> findById(Domain domain, String id) {
        return dataPlaneRegistry.getUserRepository(domain).findById(domain.asReference(), UserId.internal(id));
    }

    @Override
    public Single<User> create(Domain domain, NewUser newUser, io.gravitee.am.identityprovider.api.User principal) {
        if (StringUtils.isBlank(newUser.getUsername())) {
            return Single.error(() -> new UserInvalidException("Field [username] is required"));
        }
        // user must have a password in no pre registration mode
        if (newUser.getPassword() == null && FALSE.equals(newUser.isPreRegistration())) {
            return Single.error(new UserInvalidException("Field [password] is required"));
        }

        final var rawPassword = newUser.getPassword();
        // set user idp source
        if (newUser.getSource() == null) {
            newUser.setSource(DEFAULT_IDP_PREFIX + domain.getId());
        }

        // check user
        final var userRepository = dataPlaneRegistry.getUserRepository(domain);
        return userRepository.findByUsernameAndSource(domain.asReference(), newUser.getUsername(), newUser.getSource())
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (FALSE.equals(isEmpty)) {
                        return Single.error(new UserAlreadyExistsException(newUser.getUsername()));
                    } else {
                        // check user provider
                        return identityProviderManager.getUserProvider(newUser.getSource())
                                .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(newUser.getSource())))
                                .flatMap(userProvider -> {
                                    // check client
                                    return checkClientFunction().apply(domain.getId(), newUser.getClient())
                                            .map(Optional::of)
                                            .defaultIfEmpty(Optional.empty())
                                            .flatMap(optClient -> {
                                                Application client = optClient.orElse(null);
                                                newUser.setDomain(domain.getId());
                                                newUser.setClient(client != null ? client.getId() : null);
                                                // user is flagged as internal user
                                                newUser.setInternal(true);
                                                if (newUser.isPreRegistration()) {
                                                    newUser.setPassword(null);
                                                    newUser.setRegistrationCompleted(false);
                                                    newUser.setEnabled(false);
                                                } else {
                                                    newUser.setRegistrationCompleted(true);
                                                    newUser.setEnabled(true);
                                                    newUser.setDomain(domain.getId());
                                                }
                                                final User transform = transform(newUser);
                                                // store user in its identity provider:
                                                // - perform first validation of user to avoid error status 500 when the IDP is based on relational databases
                                                // - in case of error, trace the event otherwise continue the creation process
                                                final var identityProvider = identityProviderManager.getIdentityProvider(newUser.getSource());
                                                return passwordPolicyService.retrievePasswordPolicy(transform, client, identityProvider.orElse(null))
                                                        .map(Optional::ofNullable)
                                                        .switchIfEmpty(Maybe.just(Optional.empty()))
                                                        .flatMapSingle(optPolicy -> ensurePasswordMatchesPolicy(newUser.getPassword(), transform, optPolicy))
                                                        .flatMapCompletable(user -> userValidator.validate(user))
                                                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).reference(domain.asReference()).throwable(throwable)))
                                                        .andThen(Single.defer(() -> userProvider.create(convert(newUser))))
                                                        .map(idpUser -> {
                                                            // AM 'users' collection is not made for authentication (but only management stuff)
                                                            // clear password
                                                            newUser.setPassword(null);
                                                            // set external id
                                                            newUser.setExternalId(idpUser.getId());
                                                            return newUser;
                                                        })
                                                        // if a user is already in the identity provider but not in the AM users collection,
                                                        // it means that the user is coming from a pre-filled AM compatible identity provider (user creation enabled)
                                                        // try to create the user with the idp user information
                                                        .onErrorResumeNext(ex -> {
                                                            if (ex instanceof UserAlreadyExistsException) {
                                                                return userProvider.findByUsername(newUser.getUsername())
                                                                        // double check user existence for case sensitive
                                                                        .flatMapSingle(idpUser -> userRepository.findByUsernameAndSource(domain.asReference(), idpUser.getUsername(), newUser.getSource())
                                                                                .isEmpty()
                                                                                .map(empty -> {
                                                                                    if (!empty) {
                                                                                        throw new UserAlreadyExistsException(newUser.getUsername());
                                                                                    } else {
                                                                                        // AM 'users' collection is not made for authentication (but only management stuff)
                                                                                        // clear password
                                                                                        newUser.setPassword(null);
                                                                                        // set external id
                                                                                        newUser.setExternalId(idpUser.getId());
                                                                                        // set username
                                                                                        newUser.setUsername(idpUser.getUsername());
                                                                                        return newUser;
                                                                                    }
                                                                                })).toSingle();
                                                            } else {
                                                                return Single.error(ex);
                                                            }
                                                        })
                                                        .flatMap(newUser1 -> Single.just(transform(newUser1)).flatMapMaybe(user -> {
                                                                    AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                                                                    if (TRUE.equals(newUser.isPreRegistration() && accountSettings != null) && accountSettings.isDynamicUserRegistration()) {
                                                                        return getUserRegistrationToken(user).map(token -> {
                                                                            user.setRegistrationUserUri(domainService.buildUrl(domain, "/confirmRegistration"));
                                                                            user.setRegistrationAccessToken(token);
                                                                            return user;
                                                                        }).defaultIfEmpty(user).toMaybe();
                                                                    }
                                                                    return Maybe.just(user);
                                                                })
                                                                .toSingle()
                                                                .flatMap(user -> userRepository.create(user)
                                                                        .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).user(user1)))
                                                                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).reference(domain.asReference()).throwable(throwable)))))
                                                        .flatMap(user -> {
                                                            // end pre-registration user if required
                                                            AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                                                            if (TRUE.equals(newUser.isPreRegistration()) && (accountSettings == null || !accountSettings.isDynamicUserRegistration())) {
                                                                return sendRegistrationConfirmation(domain, user.getId(), principal).toSingleDefault(user);
                                                            } else {
                                                                createPasswordHistory(domain, client, user, rawPassword, principal, identityProvider.orElse(null));
                                                                return Single.just(user);
                                                            }
                                                        });
                                            });
                                });
                    }
                });
    }

    @Override
    public Single<User> update(Domain domain, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal) {
            if (id == null) {
                return Single.error(new InvalidUserException("User id is required"));
            }
        final var userRepository = dataPlaneRegistry.getUserRepository(domain);
        return userRepository.findById(domain.asReference(), UserId.internal(id))
                    .switchIfEmpty(Single.defer(() -> Single.error(new UserNotFoundException(id))))
                    .flatMap(user -> {

                        if (Boolean.FALSE.equals(user.isInternal()) && Boolean.TRUE.equals(updateUser.getForceResetPassword())) {
                            return Single.error(new InvalidUserException("forceResetPassword is forbidden on external users"));
                        }

                        // This handles identity providers not enforcing email
                        final boolean validateEmailIfNecessary = !(isNullOrEmpty(user.getEmail()) && isNullOrEmpty(updateUser.getEmail()));
                        return userValidator.validate(updateUser, validateEmailIfNecessary).andThen(Single.just(user));
                    })
                    .flatMap(oldUser -> updateWithProviderIfNecessary(updateUser, oldUser).flatMap(user -> {
                        User tmpUser = new User();
                        tmpUser.setEmail(updateUser.getEmail());
                        tmpUser.setAdditionalInformation(updateUser.getAdditionalInformation());
                        UserFactorUpdater.updateFactors(oldUser.getFactors(), oldUser, tmpUser);

                        final boolean generatedDisplayName = hasGeneratedDisplayName(oldUser);

                        oldUser.setClient(updateUser.getClient());
                        oldUser.setExternalId(updateUser.getExternalId());
                        oldUser.setFirstName(updateUser.getFirstName());
                        oldUser.setLastName(updateUser.getLastName());
                        if (generatedDisplayName && !isNullOrEmpty(updateUser.getFirstName()) && Objects.equals(updateUser.getDisplayName(), oldUser.getDisplayName())) {
                            oldUser.setDisplayName(buildDisplayName(oldUser));
                        } else {
                            oldUser.setDisplayName(updateUser.getDisplayName());
                        }
                        oldUser.setEmail(updateUser.getEmail());
                        oldUser.setEnabled(updateUser.isEnabled());
                        if (!StringUtils.isEmpty(updateUser.getPreferredLanguage())) {
                            oldUser.setPreferredLanguage(updateUser.getPreferredLanguage());
                        }
                        oldUser.setUpdatedAt(new Date());
                        oldUser.setAdditionalInformation(updateUser.getAdditionalInformation());
                        oldUser.setForceResetPassword(updateUser.getForceResetPassword());

                        return new UpdateUserRule(userValidator, userRepository::update).update(oldUser)
                                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).oldValue(user).user(user1)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).reference(domain.asReference()).throwable(throwable)));
                    }));
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
    public Single<User> updateStatus(Domain domain, String userId, boolean status, io.
            gravitee.am.identityprovider.api.User principal) {
        Completable removeTokens = status ? Completable.complete() : tokenService.deleteByUser(domain, User.simpleUser(userId, DOMAIN, domain.getId()));
        final var userRepository = dataPlaneRegistry.getUserRepository(domain);
        return userRepository.findById(domain.asReference(), UserId.internal(userId))
                .switchIfEmpty(Single.defer(() -> Single.error(new UserNotFoundException(userId))))
                .flatMap(user -> {
                    user.setEnabled(status);
                    final var action = new UpdateUserRule(userValidator, userRepository::update);
                    return removeTokens.andThen(action.update(user));
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type((status ? EventType.USER_ENABLED : EventType.USER_DISABLED)).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type((status ? EventType.USER_ENABLED : EventType.USER_DISABLED)).throwable(throwable)));
    }

    @Override
    public Completable resetPassword(Domain domain, String userId, String password, io.gravitee.am.identityprovider.api.User principal) {
        final var userRepository = dataPlaneRegistry.getUserRepository(domain);
        return userRepository.findById(domain.asReference(), UserId.internal(userId))
                .flatMapSingle(user -> {
                    // get client for password settings
                    return checkClientFunction().apply(domain.getId(), user.getClient())
                            .map(Optional::ofNullable)
                            .defaultIfEmpty(Optional.empty())
                            .flatMap(optClient -> {
                                final Client client = optClient.map(Application::toClient).orElse(new Client());
                                return identityProviderManager.getUserProvider(user.getSource())
                                        .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(user.getSource())))
                                        .flatMap(userProvider -> {
                                            final var identityProvider = identityProviderManager.getIdentityProvider(user.getSource());
                                            return passwordPolicyService.retrievePasswordPolicy(user, client, identityProvider.orElse(null))
                                                    .map(Optional::ofNullable)
                                                    .switchIfEmpty(Maybe.just(Optional.empty()))
                                                    .flatMap(optPolicy -> ensurePasswordMatchesPolicy(password, user, optPolicy)
                                                            .flatMapMaybe(ignore -> passwordHistoryService.addPasswordToHistory(domain, user, password, principal, optPolicy.orElse(null))))
                                                    .ignoreElement()
                                                    .andThen(Single.defer(() -> userProvider.findByUsername(user.getUsername())
                                                            .switchIfEmpty(Single.error(() -> new UserNotFoundException(user.getUsername())))
                                                            .flatMap(idpUser -> userProvider.updatePassword(idpUser, password))
                                                            .onErrorResumeNext(ex -> {
                                                                if (ex instanceof UserNotFoundException) {
                                                                    // idp user not found, create its account
                                                                    user.setPassword(password);
                                                                    return userProvider.create(convert(user));
                                                                }
                                                                return Single.error(ex);
                                                            })));
                                        }).flatMap(idpUser -> {
                                            // update 'users' collection for management and audit purpose
                                            // if user was in pre-registration mode, end the registration process
                                            if (user.isPreRegistration()) {
                                                user.setRegistrationCompleted(true);
                                                user.setEnabled(true);
                                            }
                                            user.setPassword(null);
                                            user.setExternalId(idpUser.getId());
                                            user.setLastPasswordReset(new Date());
                                            user.setUpdatedAt(new Date());
                                            return new UpdateUserRule(userValidator, userRepository::update).update(user);
                                        })
                                        // after audit, invalidate tokens whatever is the domain or app settings
                                        // as it is an admin action here, we want to force the user to login
                                        .flatMap(updatedUser -> Single.defer(() -> tokenService.deleteByUser(domain, updatedUser)
                                                .toSingleDefault(updatedUser)
                                                .onErrorResumeNext(err -> {
                                                    log.warn("Tokens not invalidated for user {} due to : {}", userId, err.getMessage());
                                                    return Single.just(updatedUser);
                                                })))
                                        .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).client(client).principal(principal).type(EventType.USER_PASSWORD_RESET).user(user)))
                                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).client(client).principal(principal).type(EventType.USER_PASSWORD_RESET).user(user).throwable(throwable)));
                            });
                }).flatMapCompletable(user -> {
                    // reset login attempts in case of reset password action
                    LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                            .domain(user.getReferenceId())
                            .client(user.getClient())
                            .username(user.getUsername())
                            .build();
                    return loginAttemptService.reset(domain, criteria);
                });
    }

    private Single<User> ensurePasswordMatchesPolicy(String password, User user, Optional<PasswordPolicy> optPolicy) {
        var policy = optPolicy.orElse(null);
        var passEvaluation = passwordService.evaluate(password, policy, user);
        if (password == null || passEvaluation.isValid()) {
            return Single.just(user);
        } else {
            return Single.error(InvalidPasswordException.of(passEvaluation, policy, "invalid_password_value"));
        }
    }

    @Override
    public Completable sendRegistrationConfirmation(Domain domain, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return domainService.findById(domain.getId())
                .switchIfEmpty(Maybe.error(() -> new DomainNotFoundException(domain.getId())))
                .flatMapCompletable(domain1 -> findById(domain, userId)
                        .flatMapCompletable(user -> {
                            if (!user.isPreRegistration()) {
                                return Completable.error(new UserInvalidException("Pre-registration is disabled for the user " + userId));
                            }
                            if (user.isPreRegistration() && user.isRegistrationCompleted()) {
                                return Completable.error(new UserInvalidException("Registration is completed for the user " + userId));
                            }
                            // fetch the client
                            return checkClientFunction().apply(user.getReferenceId(), user.getClient())
                                    .map(Optional::of)
                                    .defaultIfEmpty(Optional.empty())
                                    .doOnSuccess(optClient -> {
                                        var template = getTemplate(domain1, optClient, user);
                                        if (template == Template.ERROR) {
                                            log.warn("Cannot find template for provided case. Email will not be send.");
                                        } else {
                                            emailService.send(domain1, optClient.orElse(null), template, user)
                                                    .doOnSuccess(__ -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(resoleEventType(template)).user(user)))
                                                    .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(resoleEventType(template)).reference(Reference.domain(domain.getId())).throwable(throwable)))
                                                    .subscribe();
                                        }
                                    })
                                    .onErrorComplete()
                                    .ignoreElement();
                        }));
    }

    @Override
    public Completable lock(Domain domain, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return findById(domain, userId)
                .switchIfEmpty(Single.error(new UserNotFoundException(userId)))
                .flatMap(user -> {
                    user.setAccountNonLocked(false);
                    user.setAccountLockedAt(null);
                    user.setAccountLockedUntil(null);

                    return identityProviderManager.getUserProvider(user.getSource())
                            .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(user.getSource())))
                            .flatMap(__ -> {
                                // reset login attempts and update user
                                // We also make sure to make it at lock not to interfere with LoginAttempt if active
                                LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                                        .domain(user.getReferenceId())
                                        .client(user.getClient())
                                        .username(user.getUsername())
                                        .build();
                                final var action = new UpdateUserRule(userValidator, dataPlaneRegistry.getUserRepository(domain)::update);
                                return loginAttemptService.reset(domain, criteria).andThen(action.update(user));
                            });
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_LOCKED).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_LOCKED).reference(domain.asReference()).throwable(throwable)))
                .ignoreElement();
    }


    @Override
    public Completable unlock(Domain domain, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return findById(domain, userId)
                .switchIfEmpty(Single.error(new UserNotFoundException(userId)))
                .flatMap(user -> {
                    user.setAccountNonLocked(true);
                    user.setAccountLockedAt(null);
                    user.setAccountLockedUntil(null);
                    // reset login attempts and update user
                    LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                            .domain(user.getReferenceId())
                            .client(user.getClient())
                            .username(user.getUsername())
                            .build();
                    final var action = new UpdateUserRule(userValidator, dataPlaneRegistry.getUserRepository(domain)::update);
                    return loginAttemptService.reset(domain, criteria).andThen(action.update(user));
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UNLOCKED).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UNLOCKED).reference(domain.asReference()).throwable(throwable)))
                .ignoreElement();
    }

    @Override
    public Single<User> assignRoles(Domain domain, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal) {
        return assignRoles0(domain, userId, roles, principal, false);
    }

    @Override
    public Single<User> revokeRoles(Domain domain, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal) {
        return assignRoles0(domain, userId, roles, principal, true);
    }

    // FIXME this method is not well named, should be removeFactor and should include the factor filtering logic present into the UserFactoResource...
    @Override
    public Single<User> enrollFactors(Domain domain, String userId, List<EnrolledFactor> factors, io.gravitee.am.identityprovider.api.User principal) {
        final var userRepository = dataPlaneRegistry.getUserRepository(domain);
        return userRepository.findById(userId)
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(userId)))
                .flatMap(oldUser -> {
                    User userToUpdate = new User(oldUser);
                    userToUpdate.setFactors(factors);
                    final var action = new UpdateUserRule(userValidator, userRepository::update);
                    return action.update(userToUpdate)
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(oldUser)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(userToUpdate).throwable(throwable)));
                });
    }

    @Override
    public Single<User> unlinkIdentity(Domain domain, String userId, String identityId, io.gravitee.am.identityprovider.api.User principal) {
        final var userRepository = dataPlaneRegistry.getUserRepository(domain);
        return userRepository.findById(userId)
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(userId)))
                .flatMap(oldUser -> {
                    if (oldUser.getIdentities() == null) {
                        return Single.just(oldUser);
                    }
                    User userToUpdate = new User(oldUser);
                    List<UserIdentity> linkedIdentities = userToUpdate.getIdentities()
                            .stream()
                            .filter(linkedIdentity -> !identityId.equals(linkedIdentity.getUserId()))
                            .collect(Collectors.toList());
                    userToUpdate.setIdentities(linkedIdentities);
                    final var action = new UpdateUserRule(userValidator, userRepository::update);
                    return action.update(userToUpdate)
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(oldUser)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).reference(new Reference(oldUser.getReferenceType(), oldUser.getId())).type(EventType.USER_UPDATED).throwable(throwable)));
                });
    }

    public void setExpireAfter(Integer expireAfter) {
        this.expireAfter = expireAfter;
    }

    private Single<User> assignRoles0(Domain domain, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal, boolean revoke) {
        return findById(domain, userId)
                .switchIfEmpty(Single.error(new UserNotFoundException(userId)))
                .flatMap(oldUser -> {
                    User userToUpdate = new User(oldUser);
                    // remove existing roles from the user
                    if (revoke) {
                        if (userToUpdate.getRoles() != null) {
                            userToUpdate.getRoles().removeAll(roles);
                        }
                    } else {
                        userToUpdate.setRoles(roles);
                    }
                    // check roles
                    return checkRoles(roles)
                            // and update the user
                            .andThen(Single.defer(() -> new UpdateUserRule(userValidator, dataPlaneRegistry.getUserRepository(domain)::update).update(userToUpdate)))
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_ROLES_ASSIGNED).oldValue(oldUser).user(user1)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_ROLES_ASSIGNED).reference(domain.asReference()).throwable(throwable)));
                });
    }

    protected BiFunction<String, String, Maybe<Application>> checkClientFunction() {
        return (domain, client) -> {
            if (client == null) {
                return Maybe.empty();
            }
            return applicationService.findById(client)
                    .switchIfEmpty(Maybe.defer(() -> applicationService.findByDomainAndClientId(domain, client)))
                    .switchIfEmpty(Maybe.empty())
                    .map(app1 -> {
                        if (!domain.equals(app1.getDomain())) {
                            throw new ClientNotFoundException(client);
                        }
                        return app1;
                    });
        };
    }

    private Completable checkRoles(List<String> roles) {
        return roleService.findByIdIn(roles)
                .map(roles1 -> {
                    if (roles1.size() != roles.size()) {
                        // find difference between the two list
                        roles.removeAll(roles1.stream().map(Role::getId).collect(Collectors.toList()));
                        throw new RoleNotFoundException(String.join(",", roles));
                    }
                    return roles1;
                }).ignoreElement();
    }

    private Maybe<String> getUserRegistrationToken(User user) {
        // fetch email to get the custom expiresAfter time
        return emailService.getEmailTemplate(Template.REGISTRATION_CONFIRMATION, user)
                .map(email -> getUserRegistrationToken(user, email.getExpiresAfter()));
    }

    private String getUserRegistrationToken(User user, Integer expiresAfter) {
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        Instant now = Instant.now();
        claims.put(Claims.IAT, now.getEpochSecond());
        claims.put(Claims.EXP, now.plusSeconds((expiresAfter != null ? expiresAfter : expireAfter)).getEpochSecond());
        claims.put(Claims.SUB, user.getId());
        if (user.getClient() != null) {
            claims.put(Claims.AUD, user.getClient());
        }
        return jwtBuilder.sign(new JWT(claims));
    }

    private User transform(NewUser newUser) {
        return transform(newUser, DOMAIN, newUser.getDomain());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void createPasswordHistory(Domain domain, Application client, User user, String rawPassword, io.gravitee.am.identityprovider.api.User principal, IdentityProvider provider) {
        passwordPolicyService.retrievePasswordPolicy(user, client, provider)
                .map(Optional::of)
                .switchIfEmpty(Maybe.just(Optional.empty()))
                .flatMap(optPolicy -> passwordHistoryService.addPasswordToHistory(domain, user, rawPassword, principal, optPolicy.orElse(null)))
                .subscribe(passwordHistory -> log.debug("Created password history for user with ID {}", user),
                        throwable -> log.debug("Failed to create password history", throwable));
    }

    private static Template getTemplate(Domain domain, Optional<Application> optClient, User user) {
        //Users with registration Uri comes from self-registration and should skip registration confirmation
        if (user.getRegistrationUserUri() == null || !user.getRegistrationUserUri().contains(Template.REGISTRATION_VERIFY.redirectUri())) {
            return Template.REGISTRATION_CONFIRMATION;
        }
        if (isSendVerifyRegistrationEmailEnabled(domain, optClient)) {
            return Template.REGISTRATION_VERIFY;
        }
        return Template.ERROR;
    }

    private static boolean isSendVerifyRegistrationEmailEnabled(Domain domain, Optional<Application> optClient) {
        return optClient.map(application -> {
                    var settings = AccountSettings.getInstance(domain, application);
                    return settings != null && settings.isSendVerifyRegistrationAccountEmail();
                })
                .orElseGet(() -> domain
                        .getAccountSettings() != null && domain.getAccountSettings().isSendVerifyRegistrationAccountEmail());
    }

    private String resoleEventType(Template template) {
        if (template == Template.REGISTRATION_VERIFY) {
            return EventType.REGISTRATION_VERIFY_REQUESTED;
        } else if (template == Template.REGISTRATION_CONFIRMATION) {
            return EventType.REGISTRATION_CONFIRMATION_REQUESTED;
        }

        return null;
    }

    @Override
    public Single<User> updateUsername(Domain domain, String id, String username, io.gravitee.am.identityprovider.api.User principal) {
        final var repository = dataPlaneRegistry.getUserRepository(domain);
        return new UpdateUsernameDomainRule(userValidator,
                repository::update,
                repository::findByUsernameAndSource,
                auditService,
                credentialService,
                loginAttemptService::reset)
                .updateUsername(domain, username, principal,
                        (User user) -> identityProviderManager.getUserProvider(user.getSource())
                                .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(user.getSource()))),
                        () -> repository.findById(domain.asReference(), UserId.internal(id)).switchIfEmpty(Single.defer(() -> Single.error(new UserNotFoundException(id)))));
    }

    @SuppressWarnings("ReactiveStreamsUnusedPublisher")
    @Override
    public Single<User> delete(Domain domain, String userId, io.gravitee.am.identityprovider.api.User principal) {
        final var repository = dataPlaneRegistry.getUserRepository(domain);
        final var eventPayload = new Payload(userId, Reference.domain(domain.getId()), Action.DELETE);
        final var deleteUseEvent = new Event(Type.USER, eventPayload);
        return repository.findById(domain.asReference(), UserId.internal(userId))
                .switchIfEmpty(Single.defer(() -> Single.error(new UserNotFoundException(userId))))
                .flatMap(user -> identityProviderManager.getUserProvider(user.getSource())
                        .map(Optional::ofNullable)
                        .flatMapCompletable(optUserProvider -> {
                            // no user provider found, continue
                            if (optUserProvider.isEmpty()) {
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
                        .andThen(userActivityService.deleteByDomainAndUser(domain, userId))
                        .andThen(passwordHistoryService.deleteByUser(domain, userId))
                        // Delete WebAuthn credentials for the user
                        .andThen(credentialService.deleteByUserId(domain, userId))
                        .andThen(repository.delete(userId))
                        .andThen(eventService.create(deleteUseEvent).ignoreElement())
                        .toSingleDefault(user))
                .doOnSuccess(u -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_DELETED).user(u)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_DELETED).reference(domain.asReference()).throwable(throwable)))
                .flatMap(user -> tokenService.deleteByUser(domain, user).toSingleDefault(user));
    }


    protected io.gravitee.am.identityprovider.api.User convert(AbstractNewUser newUser) {
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

    protected User transform(AbstractNewUser newUser, ReferenceType referenceType, String referenceId) {
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
        user.setForceResetPassword(newUser.getForceResetPassword());
        user.setCreatedAt(new Date());
        user.setUpdatedAt(user.getCreatedAt());
        if (newUser.getLastPasswordReset() != null) {
            if (newUser.getLastPasswordReset().toInstant().isAfter(Instant.now())) {
                throw new UserInvalidException("lastPasswordReset cannot be in the future");
            }
            user.setLastPasswordReset(newUser.getLastPasswordReset());
        } else if(!newUser.isPreRegistration()){
            user.setLastPasswordReset(user.getCreatedAt());
        }
        user.setServiceAccount(false);
        return user;
    }

    protected void updateInfos(User user, AbstractNewUser newUser) {
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
        if (updateUser.getForceResetPassword() != null) {
            user.setForceResetPassword(updateUser.getForceResetPassword());
        }
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
            user.getAdditionalInformation().forEach(additionalInformation::putIfAbsent);
        }
        idpUser.setAdditionalInformation(additionalInformation);
        idpUser.setForceResetPassword(Boolean.FALSE);
        return idpUser;
    }
}
