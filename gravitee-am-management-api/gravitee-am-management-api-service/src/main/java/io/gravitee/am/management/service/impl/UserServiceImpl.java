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
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.PasswordPolicyService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
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
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementUserService")
public class UserServiceImpl extends AbstractUserService<io.gravitee.am.service.UserService> implements UserService {

    private static final String DEFAULT_IDP_PREFIX = "default-idp-";

    @Value("${user.registration.token.expire-after:86400}")
    private Integer expireAfter;

    @Autowired
    private EmailService emailService;

    @Autowired
    @Qualifier("managementJwtBuilder")
    private JWTBuilder jwtBuilder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private DomainService domainService;

    @Autowired
    protected io.gravitee.am.service.UserService userService;

    @Autowired
    protected TokenService tokenService;

    @Autowired
    protected PasswordPolicyService passwordPolicyService;


    @Override
    protected io.gravitee.am.service.UserService getUserService() {
        return this.userService;
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        return userService.search(referenceType, referenceId, query, page, size);
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria, int page, int size) {
        return userService.search(referenceType, referenceId, filterCriteria, page, size);
    }

    @Override
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        return userService.findAll(referenceType, referenceId, page, size);
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        return findAll(ReferenceType.DOMAIN, domain, page, size);
    }

    @Override
    public Maybe<User> findById(String id) {
        return userService.findById(id);
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
        return userService.findByDomainAndUsernameAndSource(domain.getId(), newUser.getUsername(), newUser.getSource())
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
                                                        .doOnError(throwable -> {
                                                            if (throwable instanceof InvalidPasswordException) {
                                                                auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_PASSWORD_VALIDATION).reference(Reference.domain(domain.getId())).throwable(throwable));
                                                            } else {
                                                                auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).reference(Reference.domain(domain.getId())).throwable(throwable));
                                                            }

                                                        })
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
                                                                        .flatMapSingle(idpUser -> userService.findByDomainAndUsernameAndSource(domain.getId(), idpUser.getUsername(), newUser.getSource())
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
                                                                .flatMap(user -> userService.create(user)
                                                                        .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).user(user1)))
                                                                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).reference(Reference.domain(domain.getId())).throwable(throwable)))))
                                                        .flatMap(user -> {
                                                            // end pre-registration user if required
                                                            AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                                                            if (TRUE.equals(newUser.isPreRegistration()) && (accountSettings == null || !accountSettings.isDynamicUserRegistration())) {
                                                                return sendRegistrationConfirmation(user.getReferenceId(), user.getId(), principal).toSingleDefault(user);
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
    public Single<User> update(String domain, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal) {
        return update(DOMAIN, domain, id, updateUser, principal);
    }

    @Override
    public Single<User> delete(ReferenceType referenceType, String referenceId, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return super.delete(referenceType, referenceId, userId, principal)
                .flatMap(user -> tokenService.deleteByUser(user).toSingleDefault(user));
    }

    @Override
    public Single<User> updateStatus(String domain, String id, boolean status, io.gravitee.am.identityprovider.api.User principal) {
        return updateStatus(DOMAIN, domain, id, status, principal);
    }

    @Override
    public Completable resetPassword(Domain domain, String userId, String password, io.gravitee.am.identityprovider.api.User principal) {
        return userService.findById(DOMAIN, domain.getId(), userId)
                .flatMap(user -> {
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
                                                            .flatMapMaybe(ignore -> passwordHistoryService.addPasswordToHistory(DOMAIN, domain.getId(), user, password, principal, optPolicy.orElse(null))))
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
                                            return userService.update(user);
                                        })
                                        // after audit, invalidate tokens whatever is the domain or app settings
                                        // as it is an admin action here, we want to force the user to login
                                        .flatMap(updatedUser -> Single.defer(() -> tokenService.deleteByUser(updatedUser)
                                                .toSingleDefault(updatedUser)
                                                .onErrorResumeNext(err -> {
                                                    logger.warn("Tokens not invalidated for user {} due to : {}", userId, err.getMessage());
                                                    return Single.just(updatedUser);
                                                })))
                                        .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).client(client).principal(principal).type(EventType.USER_PASSWORD_RESET).user(user)))
                                        .doOnError(throwable -> {
                                            if(throwable instanceof InvalidPasswordException){
                                                auditService.report(AuditBuilder.builder(UserAuditBuilder.class).client(client).user(user).principal(principal).type(EventType.USER_PASSWORD_VALIDATION).reference(Reference.domain(domain.getId())).throwable(throwable));
                                            } else {
                                                auditService.report(AuditBuilder.builder(UserAuditBuilder.class).client(client).principal(principal).type(EventType.USER_PASSWORD_RESET).user(user).throwable(throwable));
                                            }
                                        });
                            });
                }).flatMapCompletable(user -> {
                    // reset login attempts in case of reset password action
                    LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                            .domain(user.getReferenceId())
                            .client(user.getClient())
                            .username(user.getUsername())
                            .build();
                    return loginAttemptService.reset(criteria);
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
    public Completable sendRegistrationConfirmation(String domainId, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return domainService.findById(domainId)
                .switchIfEmpty(Maybe.error(() -> new DomainNotFoundException(domainId)))
                .flatMapCompletable(domain1 -> findById(DOMAIN, domainId, userId)
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
                                            logger.warn("Cannot find template for provided case. Email will not be send.");
                                        } else {
                                            emailService.send(domain1, optClient.orElse(null), template, user)
                                                    .doOnSuccess(__ -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(resoleEventType(template)).user(user)))
                                                    .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(resoleEventType(template)).reference(Reference.domain(domainId)).throwable(throwable)))
                                                    .subscribe();
                                        }
                                    })
                                    .onErrorComplete()
                                    .ignoreElement();
                        }));
    }

    @Override
    public Completable lock(ReferenceType referenceType, String referenceId, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return findById(referenceType, referenceId, userId)
                .flatMap(user -> {
                    user.setAccountNonLocked(false);
                    user.setAccountLockedAt(null);
                    user.setAccountLockedUntil(null);

                    return identityProviderManager.getUserProvider(user.getSource())
                            .switchIfEmpty(identityProviderManager.getUserProvider(user.getSource()))
                            .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(user.getSource())))
                            .flatMap(__ -> {
                                // reset login attempts and update user
                                // We also make sure to make it at lock not to interfere with LoginAttempt if active
                                LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                                        .domain(user.getReferenceId())
                                        .client(user.getClient())
                                        .username(user.getUsername())
                                        .build();
                                return loginAttemptService.reset(criteria).andThen(userService.update(user));
                            });
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_LOCKED).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_LOCKED).reference(new Reference(referenceType, referenceId)).throwable(throwable)))
                .ignoreElement();
    }


    @Override
    public Completable unlock(ReferenceType referenceType, String referenceId, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return findById(referenceType, referenceId, userId)
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
                    return loginAttemptService.reset(criteria).andThen(userService.update(user));
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UNLOCKED).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UNLOCKED).reference(new Reference(referenceType, referenceId)).throwable(throwable)))
                .ignoreElement();
    }

    @Override
    public Single<User> assignRoles(ReferenceType referenceType, String referenceId, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal) {
        return assignRoles0(referenceType, referenceId, userId, roles, principal, false);
    }

    @Override
    public Single<User> revokeRoles(ReferenceType referenceType, String referenceId, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal) {
        return assignRoles0(referenceType, referenceId, userId, roles, principal, true);
    }

    @Override
    public Single<User> enrollFactors(String userId, List<EnrolledFactor> factors, io.gravitee.am.identityprovider.api.User principal) {
        return userService.findById(userId)
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(userId)))
                .flatMap(oldUser -> {
                    User userToUpdate = new User(oldUser);
                    userToUpdate.setFactors(factors);
                    return userService.update(userToUpdate)
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(oldUser)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(userToUpdate).throwable(throwable)));
                });
    }

    @Override
    public Single<User> unlinkIdentity(String userId, String identityId, io.gravitee.am.identityprovider.api.User principal) {
        return userService.findById(userId)
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
                    return userService.update(userToUpdate)
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(oldUser)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).reference(new Reference(oldUser.getReferenceType(), oldUser.getId())).type(EventType.USER_UPDATED).throwable(throwable)));
                });
    }

    public void setExpireAfter(Integer expireAfter) {
        this.expireAfter = expireAfter;
    }

    private Single<User> assignRoles0(ReferenceType referenceType, String referenceId, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal, boolean revoke) {
        return findById(referenceType, referenceId, userId)
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
                            .andThen(Single.defer(() -> userService.update(userToUpdate)))
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_ROLES_ASSIGNED).oldValue(oldUser).user(user1)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_ROLES_ASSIGNED).reference(new Reference(referenceType, referenceId)).throwable(throwable)));
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
                .flatMap(optPolicy -> passwordHistoryService.addPasswordToHistory(DOMAIN, domain.getId(), user, rawPassword, principal, optPolicy.orElse(null)))
                .subscribe(passwordHistory -> logger.debug("Created password history for user with ID {}", user),
                        throwable -> logger.debug("Failed to create password history", throwable));
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

}
