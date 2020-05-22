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
import io.gravitee.am.common.email.Email;
import io.gravitee.am.common.email.EmailBuilder;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.*;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.jsonwebtoken.JwtBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementUserService")
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_IDP_PREFIX = "default-idp-";

    @Value("${user.registration.email.subject:New user registration}")
    private String registrationSubject;

    @Value("${user.registration.token.expire-after:86400}")
    private Integer expireAfter;

    @Value("${gateway.url:http://localhost:8092}")
    private String gatewayUrl;

    @Autowired
    private io.gravitee.am.service.UserService userService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtBuilder jwtBuilder;

    @Autowired
    private EmailManager emailManager;

    @Autowired
    private AuditService auditService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private DomainService domainService;

    @Override
    public Single<Page<User>> search(String domain, String query, int page, int size) {
        return userService.search(domain, query, page, size);
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        return userService.findByDomain(domain, page, size);
    }

    @Override
    public Maybe<User> findById(String id) {
        return userService.findById(id);
    }

    @Override
    public Single<User> create(String domain, NewUser newUser, io.gravitee.am.identityprovider.api.User principal) {
        // set user idp source
        if (newUser.getSource() == null) {
            newUser.setSource(DEFAULT_IDP_PREFIX + domain);
        }
        // check domain
        return domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(domain1 -> {
                    // check user
                    return userService.findByDomainAndUsernameAndSource(domain1.getId(), newUser.getUsername(), newUser.getSource())
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
                                                return checkClient(domain, newUser.getClient())
                                                        .map(Optional::of)
                                                        .defaultIfEmpty(Optional.empty())
                                                        .flatMapSingle(optClient -> {
                                                            Client client = optClient.orElse(null);
                                                            newUser.setDomain(domain1.getId());
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
                                                                newUser.setDomain(domain);
                                                            }
                                                            // store user in its identity provider
                                                            return userProvider.create(convert(newUser))
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
                                                                                    .flatMapSingle(idpUser -> userService.findByDomainAndUsernameAndSource(domain1.getId(), idpUser.getUsername(), newUser.getSource())
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
                                                                        User user = transform(newUser1);
                                                                        AccountSettings accountSettings = getAccountSettings(domain1, client);
                                                                        if (newUser.isPreRegistration() && accountSettings != null && accountSettings.isDynamicUserRegistration()) {
                                                                            user.setRegistrationUserUri(getUserRegistrationUri(domain1.getPath(), "/confirmRegistration"));
                                                                            user.setRegistrationAccessToken(getUserRegistrationToken(user));
                                                                        }
                                                                        return userService.create(user);
                                                                    })
                                                                    .doOnSuccess(user -> {
                                                                        auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).user(user));
                                                                        // in pre registration mode an email will be sent to the user to complete his account
                                                                        AccountSettings accountSettings = getAccountSettings(domain1, client);
                                                                        if (newUser.isPreRegistration() && accountSettings != null && !accountSettings.isDynamicUserRegistration()) {
                                                                            new Thread(() -> completeUserRegistration(user)).start();
                                                                        }
                                                                    })
                                                                    .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).throwable(throwable)));
                                                        });
                                            });
                                }
                            });
                });
    }

    @Override
    public Single<User> update(String domain, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal) {
        return userService.findById(id)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(id)))
                .flatMapSingle(user -> identityProviderManager.getUserProvider(user.getSource())
                        .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                        // check client
                        .flatMapSingle(userProvider -> {
                            String client = updateUser.getClient() != null ? updateUser.getClient() : user.getClient();
                            if (client != null) {
                                return checkClient(domain, client)
                                        .flatMapSingle(client1 -> {
                                            updateUser.setClient(client1.getId());
                                            return Single.just(userProvider);
                                        });
                            }
                            return Single.just(userProvider);
                        })
                        // update the idp user
                        .flatMap(userProvider -> userProvider.findByUsername(user.getUsername())
                                .switchIfEmpty(Maybe.error(new UserNotFoundException(user.getUsername())))
                                .flatMapSingle(idpUser -> userProvider.update(idpUser.getId(), convert(user.getUsername(), updateUser))))
                        .flatMap(idpUser ->  {
                            // set external id
                            updateUser.setExternalId(idpUser.getId());
                            return userService.update(domain, id, updateUser);
                        })
                        .onErrorResumeNext(ex -> {
                            if (ex instanceof UserNotFoundException) {
                                // idp user does not exist, only update AM user
                                return userService.update(domain, id, updateUser);
                            }
                            return Single.error(ex);
                        })
                        .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).oldValue(user).user(user1)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).throwable(throwable)))
                );
    }

    @Override
    public Single<User> updateStatus(String domain, String id, boolean status, io.gravitee.am.identityprovider.api.User principal) {
        return userService.findById(id)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(id)))
                .flatMapSingle(user -> {
                    user.setEnabled(status);
                    return userService.update(user);
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type((status ? EventType.USER_ENABLED : EventType.USER_DISABLED)).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type((status ? EventType.USER_ENABLED : EventType.USER_DISABLED)).throwable(throwable)));
    }

    @Override
    public Completable delete(String userId, io.gravitee.am.identityprovider.api.User principal) {
        return userService.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
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
                        .andThen(userService.delete(userId))
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_DELETED).user(user)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_DELETED).throwable(throwable)))
                );
    }

    @Override
    public Completable resetPassword(String domain, String userId, String password, io.gravitee.am.identityprovider.api.User principal) {
        return userService.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapSingle(user -> identityProviderManager.getUserProvider(user.getSource())
                        .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                        .flatMapSingle(userProvider -> {
                            // update idp user
                            return userProvider.findByUsername(user.getUsername())
                                    .switchIfEmpty(Maybe.error(new UserNotFoundException(user.getUsername())))
                                    .flatMapSingle(idpUser -> {
                                        // set password
                                        ((DefaultUser) idpUser).setCredentials(password);
                                        return userProvider.update(idpUser.getId(), idpUser);
                                    })
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
                            }
                            user.setPassword(null);
                            user.setExternalId(idpUser.getId());
                            user.setUpdatedAt(new Date());
                            return userService.update(user);
                        })
                        .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_PASSWORD_RESET).user(user)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_PASSWORD_RESET).throwable(throwable)))
                // reset login attempts in case of reset password action
                ).flatMapCompletable(user -> {
                    LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                            .domain(user.getDomain())
                            .client(user.getClient())
                            .username(user.getUsername())
                            .build();
                    return loginAttemptService.reset(criteria);
                });
    }

    @Override
    public Completable sendRegistrationConfirmation(String userId, io.gravitee.am.identityprovider.api.User principal) {
        return findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .map(user -> {
                    if (!user.isPreRegistration()) {
                        throw new UserInvalidException("Pre-registration is disabled for the user " + userId);
                    }
                    if (user.isPreRegistration() && user.isRegistrationCompleted()) {
                        throw new UserInvalidException("Registration is completed for the user " + userId);
                    }
                    return user;
                })
                .doOnSuccess(user -> new Thread(() -> completeUserRegistration(user)).start())
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.REGISTRATION_CONFIRMATION_REQUESTED).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.REGISTRATION_CONFIRMATION_REQUESTED).throwable(throwable)))
                .toSingle()
                .toCompletable();
    }

    @Override
    public Completable unlock(String userId, io.gravitee.am.identityprovider.api.User principal) {
        return findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapSingle(user -> {
                    user.setAccountNonLocked(true);
                    user.setAccountLockedAt(null);
                    user.setAccountLockedUntil(null);
                    // reset login attempts and update user
                    LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                            .domain(user.getDomain())
                            .client(user.getClient())
                            .username(user.getUsername())
                            .build();
                    return loginAttemptService.reset(criteria)
                            .andThen(userService.update(user));
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UNLOCKED).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UNLOCKED).throwable(throwable)))
                .toCompletable();
    }

    @Override
    public Single<User> assignRoles(String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal) {
        return assignRoles0(userId, roles, principal, false);
    }

    @Override
    public Single<User> revokeRoles(String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal) {
        return assignRoles0(userId, roles, principal, true);
    }

    public void setExpireAfter(Integer expireAfter) {
        this.expireAfter = expireAfter;
    }

    private Single<User> assignRoles0(String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal, boolean revoke) {
        return findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapSingle(oldUser -> {
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

    private Maybe<Client> checkClient(String domain, String client) {
        if (client == null) {
            return Maybe.empty();
        }
        return clientService.findById(client)
                .switchIfEmpty(Maybe.defer(() -> clientService.findByDomainAndClientId(domain, client)))
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(client)))
                .map(client1 -> {
                    if (!domain.equals(client1.getDomain())) {
                        throw new ClientNotFoundException(client);
                    }
                    return client1;
                });
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

    private void completeUserRegistration(User user) {
        final String templateName = getTemplateName(user);
        io.gravitee.am.model.Email email = emailManager.getEmail(templateName, registrationSubject, expireAfter);
        Email email1 = buildEmail(user, email, "/confirmRegistration", "registrationUrl");
        emailService.send(email1, user);
    }

    private Email buildEmail(User user, io.gravitee.am.model.Email email, String redirectUri, String redirectUriName) {
        Map<String, Object> params = prepareEmail(user, email.getExpiresAfter(), redirectUri, redirectUriName);
        Email email1 = new EmailBuilder()
                .to(user.getEmail())
                .from(email.getFrom())
                .fromName(email.getFromName())
                .subject(email.getSubject())
                .template(email.getTemplate())
                .params(params)
                .build();
        return email1;
    }

    private Map<String, Object> prepareEmail(User user, int expiresAfter, String redirectUri, String redirectUriName) {
        final String token = getUserRegistrationToken(user);
        final String redirectUrl = getUserRegistrationUri(user.getDomain(), redirectUri) + "?token=" + token;

        Map<String, Object> params = new HashMap<>();
        params.put("user", user);
        params.put(redirectUriName, redirectUrl);
        params.put("token", token);
        params.put("expireAfterSeconds", expiresAfter);

        return params;
    }

    private String getUserRegistrationUri(String domain, String redirectUri) {
        String entryPoint = gatewayUrl;
        if (entryPoint != null && entryPoint.endsWith("/")) {
            entryPoint = entryPoint.substring(0, entryPoint.length() - 1);
        }

        return entryPoint + "/" + domain + redirectUri;
    }

    private String getUserRegistrationToken(User user) {
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.iat, new Date().getTime() / 1000);
        claims.put(Claims.exp, new Date(System.currentTimeMillis() + (expireAfter * 1000)).getTime() / 1000);
        claims.put(Claims.sub, user.getId());
        if (user.getClient() != null) {
            claims.put(Claims.aud, user.getClient());
        }
        claims.put(StandardClaims.EMAIL, user.getEmail());
        claims.put(StandardClaims.GIVEN_NAME, user.getFirstName());
        claims.put(StandardClaims.FAMILY_NAME, user.getLastName());

        return jwtBuilder.setClaims(claims).compact();
    }

    private String getTemplateName(User user) {
        return Template.REGISTRATION_CONFIRMATION.template()
                + EmailManager.TEMPLATE_NAME_SEPARATOR
                + user.getDomain()
                + ((user.getClient() != null) ? EmailManager.TEMPLATE_NAME_SEPARATOR +  user.getClient() : "");
    }

    private AccountSettings getAccountSettings(Domain domain, Client client) {
        if (client == null) {
            return domain.getAccountSettings();
        }
        // if client has no account config return domain config
        if (client.getAccountSettings() == null) {
            return domain.getAccountSettings();
        }

        // if client configuration is not inherited return the client config
        if (!client.getAccountSettings().isInherited()) {
            return client.getAccountSettings();
        }

        // return domain config
        return domain.getAccountSettings();
    }

    private io.gravitee.am.identityprovider.api.User convert(NewUser newUser) {
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
            newUser.getAdditionalInformation().forEach((k, v) -> additionalInformation.putIfAbsent(k, v));
        }
        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    private User transform(NewUser newUser) {
        User user = new User();
        user.setId(RandomString.generate());
        user.setExternalId(newUser.getExternalId());
        user.setDomain(newUser.getDomain());
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
        user.setAdditionalInformation(newUser.getAdditionalInformation());
        user.setCreatedAt(new Date());
        user.setUpdatedAt(user.getCreatedAt());
        return user;
    }

    private io.gravitee.am.identityprovider.api.User convert(String username, UpdateUser updateUser) {
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
            updateUser.getAdditionalInformation().forEach((k, v) -> additionalInformation.putIfAbsent(k, v));
        }
        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    private io.gravitee.am.identityprovider.api.User convert(User user) {
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
}
