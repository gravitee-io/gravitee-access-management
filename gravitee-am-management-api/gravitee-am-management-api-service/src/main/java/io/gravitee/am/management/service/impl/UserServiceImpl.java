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
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.*;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.validators.email.EmailValidator;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.gravitee.am.model.ReferenceType.DOMAIN;

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
    private EmailValidator emailValidator;

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
        // user must have a password in no pre registration mode
        if (newUser.getPassword() == null) {
            if (!newUser.isPreRegistration()) {
                return Single.error(new UserInvalidException("Field [password] is required"));
            }
        }

        // set user idp source
        if (newUser.getSource() == null) {
            newUser.setSource(DEFAULT_IDP_PREFIX + domain.getId());
        }

        // check user
        return userService.findByDomainAndUsernameAndSource(domain.getId(), newUser.getUsername(), newUser.getSource())
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (!isEmpty) {
                        return Single.error(new UserAlreadyExistsException(newUser.getUsername()));
                    } else {
                        // check user provider
                        return identityProviderManager.getUserProvider(newUser.getSource())
                                .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(newUser.getSource())))
                                .flatMapSingle(userProvider -> {
                                    // check client
                                    return checkClientFunction().apply(domain.getId(), newUser.getClient())
                                            .map(Optional::of)
                                            .defaultIfEmpty(Optional.empty())
                                            .flatMapSingle(optClient -> {
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
                                                String password = newUser.getPassword();
                                                if (password != null && isInvalidUserPassword(password, client, domain, transform)) {
                                                    return Single.error(InvalidPasswordException.of("Field [password] is invalid", "invalid_password_value"));
                                                }
                                                // store user in its identity provider:
                                                // - perform first validation of user to avoid error status 500 when the IDP is based on relational databases
                                                // - in case of error, trace the event otherwise continue the creation process
                                                return userValidator.validate(transform)
                                                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).throwable(throwable)))
                                                        .andThen(userProvider.create(convert(newUser)))
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
                                                                                }));
                                                            } else {
                                                                return Single.error(ex);
                                                            }
                                                        })
                                                        .flatMap(newUser1 -> {
                                                            return Single.fromCallable(() -> {
                                                                User user = transform(newUser1);
                                                                AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                                                                if (newUser.isPreRegistration() && accountSettings != null && accountSettings.isDynamicUserRegistration()) {
                                                                    user.setRegistrationUserUri(domainService.buildUrl(domain, "/confirmRegistration"));
                                                                    user.setRegistrationAccessToken(getUserRegistrationToken(user));
                                                                }
                                                                return user;
                                                            }).flatMap(user -> userService.create(user)
                                                                    .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).user(user1)))
                                                                    .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).throwable(throwable))));
                                                        })
                                                        .flatMap(user -> {
                                                            // end pre-registration user if required
                                                            AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                                                            if (newUser.isPreRegistration() && (accountSettings == null || !accountSettings.isDynamicUserRegistration())) {
                                                                return sendRegistrationConfirmation(user.getReferenceId(), user.getId(), principal).toSingleDefault(user);
                                                            } else {
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
                            .flatMapSingle(optClient -> {
                                // check user password
                                if (isInvalidUserPassword(password, optClient.orElse(null), domain, user)) {
                                    return Single.error(InvalidPasswordException.of("Field [password] is invalid", "invalid_password_value"));
                                }
                                final Client client = optClient.filter(Objects::nonNull).map(Application::toClient).orElse(new Client());
                                return identityProviderManager.getUserProvider(user.getSource())
                                        .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                                        .flatMapSingle(userProvider -> {
                                            // update idp user
                                            return userProvider.findByUsername(user.getUsername())
                                                    .switchIfEmpty(Maybe.error(new UserNotFoundException(user.getUsername())))
                                                    .flatMapSingle(idpUser -> userProvider.updatePassword(idpUser, password))
                                                    .onErrorResumeNext(ex -> {
                                                        if (ex instanceof UserNotFoundException) {
                                                            // idp user not found, create its account
                                                            user.setPassword(password);
                                                            return userProvider.create(convert(user));
                                                        }
                                                        return Single.error(ex);
                                                    });
                                        })
                                        .flatMap(idpUser -> {
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
                                        })// after audit, invalidate tokens whatever is the domain or app settings
                                        // as it is an admin action here, we want to force the user to login
                                        .flatMap(updatedUser -> Single.defer(() -> tokenService.deleteByUserId(updatedUser.getId())
                                                .toSingleDefault(updatedUser)
                                                .onErrorResumeNext(err -> {
                                                    logger.warn("Tokens not invalidated for user {} due to : {}", userId, err.getMessage());
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
                    return loginAttemptService.reset(criteria);
                });
    }

    @Override
    public Completable sendRegistrationConfirmation(String domainId, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return domainService.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
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
                                    .doOnSuccess(optClient -> new Thread(() -> emailService.send(domain1, optClient.orElse(null), Template.REGISTRATION_CONFIRMATION, user)).start())
                                    .doOnSuccess(__ -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.REGISTRATION_CONFIRMATION_REQUESTED).user(user)))
                                    .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.REGISTRATION_CONFIRMATION_REQUESTED).throwable(throwable)))
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
                            .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                            .flatMapSingle(__ -> {
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
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_LOCKED).throwable(throwable)))
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
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UNLOCKED).throwable(throwable)))
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
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapSingle(oldUser -> {
                    User userToUpdate = new User(oldUser);
                    userToUpdate.setFactors(factors);
                    return userService.update(userToUpdate)
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(oldUser)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).throwable(throwable)));
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
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_ROLES_ASSIGNED).throwable(throwable)));
                });
    }

    protected BiFunction<String, String, Maybe<Application>> checkClientFunction() {
        return (domain, client) -> {
            if (client == null) {
                return Maybe.empty();
            }
            return applicationService.findById(client)
                    .switchIfEmpty(Maybe.defer(() -> applicationService.findByDomainAndClientId(domain, client)))
                    .switchIfEmpty(Maybe.error(new ClientNotFoundException(client)))
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
                }).toCompletable();
    }

    private boolean isInvalidUserPassword(String password, Application application, Domain domain, User user) {
        return PasswordSettings.getInstance(application, domain)
                .map(ps -> !passwordService.isValid(password, ps, user))
                .orElseGet(() -> !passwordService.isValid(password, null, user));
    }

    private String getUserRegistrationToken(User user) {
        // fetch email to get the custom expiresAfter time
        io.gravitee.am.model.Email email = emailService.getEmailTemplate(Template.REGISTRATION_CONFIRMATION, user);
        return getUserRegistrationToken(user, email.getExpiresAfter());
    }

    private String getUserRegistrationToken(User user, Integer expiresAfter) {
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        Instant now = Instant.now();
        claims.put(Claims.iat, now.getEpochSecond());
        claims.put(Claims.exp, now.plusSeconds((expiresAfter != null ? expiresAfter : expireAfter)).getEpochSecond());
        claims.put(Claims.sub, user.getId());
        if (user.getClient() != null) {
            claims.put(Claims.aud, user.getClient());
        }
        return jwtBuilder.sign(new JWT(claims));
    }

    private User transform(NewUser newUser) {
        return transform(newUser, DOMAIN, newUser.getDomain());
    }
}
