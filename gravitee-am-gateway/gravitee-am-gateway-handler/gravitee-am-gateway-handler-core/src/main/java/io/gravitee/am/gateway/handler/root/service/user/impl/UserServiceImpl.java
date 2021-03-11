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
package io.gravitee.am.gateway.handler.root.service.user.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.exception.authentication.AccountInactiveException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.validators.EmailValidator;
import io.gravitee.am.service.validators.UserValidator;
import io.reactivex.Observable;
import io.reactivex.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_IDP_PREFIX = "default-idp-";

    @Autowired
    private io.gravitee.am.gateway.handler.common.user.UserService userService;

    @Autowired
    @Qualifier("managementJwtParser")
    private JWTParser jwtParser;

    @Autowired
    private Domain domain;

    @Autowired
    private EmailService emailService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Override
    public Maybe<UserToken> verifyToken(String token) {
        return Maybe.fromCallable(() -> jwtParser.parse(token))
                .flatMap(jwt -> userService.findById(jwt.getSub()).zipWith(clientSource(jwt.getAud()), (user, optionalClient) -> new UserToken(user, optionalClient.orElse(null))));
    }

    @Override
    public Single<RegistrationResponse> register(Client client, User user, io.gravitee.am.identityprovider.api.User principal) {
        // set user idp source
        AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
        final String source = (accountSettings != null && accountSettings.getDefaultIdentityProviderForRegistration() != null) ? accountSettings.getDefaultIdentityProviderForRegistration()
                : (user.getSource() == null ? DEFAULT_IDP_PREFIX + domain.getId() : user.getSource());

        // validate user and then check user uniqueness
        return UserValidator.validate(user)
                .andThen(userService.findByDomainAndUsernameAndSource(domain.getId(), user.getUsername(), source)
                        .isEmpty()
                        .flatMapMaybe(isEmpty -> {
                            if (!isEmpty) {
                                return Maybe.error(new UserAlreadyExistsException(user.getUsername()));
                            }

                            // check if user provider exists
                            return identityProviderManager.getUserProvider(source);
                        })
                        .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(source)))
                        .flatMapSingle(userProvider -> userProvider.create(convert(user)))
                        .flatMap(idpUser -> {
                            // AM 'users' collection is not made for authentication (but only management stuff)
                            // clear password
                            user.setPassword(null);
                            // set external id
                            user.setExternalId(idpUser.getId());
                            // set source
                            user.setSource(source);
                            // set domain
                            user.setReferenceType(ReferenceType.DOMAIN);
                            user.setReferenceId(domain.getId());
                            // internal user
                            user.setInternal(true);
                            // additional information
                            extractAdditionalInformation(user, idpUser.getAdditionalInformation());
                            // set date information
                            user.setCreatedAt(new Date());
                            user.setUpdatedAt(user.getCreatedAt());
                            if (accountSettings != null && accountSettings.isAutoLoginAfterRegistration()) {
                                user.setLoggedAt(new Date());
                                user.setLoginsCount(1l);
                            }
                            return userService.create(user);
                        })
                        .flatMap(userService::enhance)
                        .map(user1 -> new RegistrationResponse(user1, accountSettings != null ? accountSettings.getRedirectUriAfterRegistration() : null, accountSettings != null && accountSettings.isAutoLoginAfterRegistration()))
                        .doOnSuccess(registrationResponse -> {
                            // reload principal
                            final User user1 = registrationResponse.getUser();
                            io.gravitee.am.identityprovider.api.User principal1 = reloadPrincipal(principal, user1);
                            auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(client).principal(principal1).type(EventType.USER_REGISTERED));
                        })
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.USER_REGISTERED).throwable(throwable))));
    }

    @Override
    public Single<RegistrationResponse> confirmRegistration(Client client, User user, io.gravitee.am.identityprovider.api.User
            principal) {
        // user has completed his account, add it to the idp
        return identityProviderManager.getUserProvider(user.getSource())
                .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                // update the idp user
                .flatMapSingle(userProvider -> {
                    return userProvider.findByUsername(user.getUsername())
                            .switchIfEmpty(Maybe.error(new UserNotFoundException(user.getUsername())))
                            .flatMapSingle(idpUser -> userProvider.update(idpUser.getId(), convert(user)))
                            .onErrorResumeNext(ex -> {
                                if (ex instanceof UserNotFoundException) {
                                    // idp user not found, create its account
                                    return userProvider.create(convert(user));
                                }
                                return Single.error(ex);
                            });
                })
                .flatMap(idpUser -> {
                    // update 'users' collection for management and audit purpose
                    user.setPassword(null);
                    user.setRegistrationCompleted(true);
                    user.setEnabled(true);
                    user.setExternalId(idpUser.getId());
                    user.setUpdatedAt(new Date());
                    // additional information
                    extractAdditionalInformation(user, idpUser.getAdditionalInformation());
                    // set login information
                    AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                    if (accountSettings != null && accountSettings.isAutoLoginAfterRegistration()) {
                        user.setLoggedAt(new Date());
                        user.setLoginsCount(1l);
                    }
                    return userService.update(user);
                })
                .flatMap(userService::enhance)
                .map(user1 -> {
                    AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                    return new RegistrationResponse(user1, accountSettings != null ? accountSettings.getRedirectUriAfterRegistration() : null, accountSettings != null ? accountSettings.isAutoLoginAfterRegistration() : false);
                })
                .doOnSuccess(response -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.REGISTRATION_CONFIRMATION)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.REGISTRATION_CONFIRMATION).throwable(throwable)));

    }

    @Override
    public Single<ResetPasswordResponse> resetPassword(Client client, User user, io.gravitee.am.identityprovider.api.User principal) {
        // if user registration is not completed and force registration option is disabled throw invalid account exception
        if (user.isInactive() && !forceUserRegistration(domain, client)) {
            return Single.error(new AccountInactiveException("User needs to complete the activation process"));
        }

        // only idp manage password, find user idp and update its password
        return identityProviderManager.getUserProvider(user.getSource())
                .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                // update the idp user
                .flatMapSingle(userProvider -> {
                    return userProvider.findByUsername(user.getUsername())
                            .switchIfEmpty(Maybe.error(new UserNotFoundException(user.getUsername())))
                            .flatMapSingle(idpUser -> {
                                // set password
                                ((DefaultUser) idpUser).setCredentials(user.getPassword());
                                return userProvider.update(idpUser.getId(), idpUser);
                            })
                            .onErrorResumeNext(ex -> {
                                if (ex instanceof UserNotFoundException) {
                                    // idp user not found, create its account
                                    return userProvider.create(convert(user));
                                }
                                return Single.error(ex);
                            });
                })
                // update the user in the AM repository
                .flatMap(idpUser -> {
                    // update 'users' collection for management and audit purpose
                    // if user was in pre-registration mode, end the registration process
                    if (user.isPreRegistration()) {
                        user.setRegistrationCompleted(true);
                        user.setEnabled(true);
                    }
                    user.setAccountNonLocked(true);
                    user.setAccountLockedAt(null);
                    user.setAccountLockedUntil(null);
                    user.setPassword(null);
                    user.setExternalId(idpUser.getId());
                    user.setLastPasswordReset(new Date());
                    user.setUpdatedAt(new Date());
                    // additional information
                    extractAdditionalInformation(user, idpUser.getAdditionalInformation());
                    // set login information
                    AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                    if (accountSettings != null && accountSettings.isAutoLoginAfterResetPassword()) {
                        user.setLoggedAt(new Date());
                        user.setLoginsCount(user.getLoginsCount() + 1);
                    }
                    return userService.update(user);
                })
                // reset login attempts in case of reset password action
                .flatMap(user1 -> {
                    LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                            .domain(user1.getReferenceId())
                            .client(user1.getClient())
                            .username(user1.getUsername())
                            .build();
                    return loginAttemptService.reset(criteria).andThen(Single.just(user1));
                })
                .flatMap(userService::enhance)
                .map(user1 -> {
                    AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                    return new ResetPasswordResponse(user1, accountSettings != null ? accountSettings.getRedirectUriAfterResetPassword() : null, accountSettings != null ? accountSettings.isAutoLoginAfterResetPassword() : false);
                })
                .doOnSuccess(response -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.USER_PASSWORD_RESET)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.USER_PASSWORD_RESET).throwable(throwable)));
    }

    @Override
    public Completable forgotPassword(String email, Client client, io.gravitee.am.identityprovider.api.User principal) {

        if (!EmailValidator.isValid(email)) {
            return Completable.error(new EmailFormatInvalidException(email));
        }

        return userService.findByDomainAndEmail(domain.getId(), email, true)
                .flatMap(users -> {
                    Optional<User> optionalUser = users
                            .stream()
                            .filter(user -> user.getEmail() != null && email.toLowerCase().equals(user.getEmail().toLowerCase()))
                            .findFirst();
                    if (optionalUser.isPresent()) {
                        User user = optionalUser.get();
                        // check if user can update its password according to its identity provider type
                        return identityProviderManager.getUserProvider(user.getSource())
                                .switchIfEmpty(Single.error(new UserInvalidException("User [ " + user.getUsername() + " ] cannot be updated because its identity provider does not support user provisioning")))
                                .map(__ -> {
                                    // if user registration is not completed and force registration option is disabled throw invalid account exception
                                    if (user.isInactive() && !forceUserRegistration(domain, client)) {
                                        throw new AccountInactiveException("User [ " + user.getUsername() + " ]needs to complete the activation process");
                                    }
                                    return user;
                                });
                    }

                    // if user has no email or email is unknown
                    // fallback to registered user providers if user has never been authenticated
                    if (client.getIdentities() == null || client.getIdentities().isEmpty()) {
                        return Single.error(new UserNotFoundException(email));
                    }

                    return Observable.fromIterable(client.getIdentities())
                            .flatMapMaybe(authProvider -> {
                                return identityProviderManager.getUserProvider(authProvider)
                                        .flatMap(userProvider -> {
                                            return userProvider.findByEmail(email)
                                                    .map(user -> Optional.of(user))
                                                    .defaultIfEmpty(Optional.empty())
                                                    .onErrorReturnItem(Optional.empty());
                                        })
                                        .defaultIfEmpty(Optional.empty());
                            })
                            .takeUntil(optional -> {
                                return optional.isPresent();
                            })
                            .lastOrError()
                            .flatMap(optional -> {
                                io.gravitee.am.identityprovider.api.User idpUser = optional.get();
                                User newUser = new User();
                                newUser.setId(RandomString.generate());
                                newUser.setExternalId(idpUser.getId());
                                newUser.setUsername(idpUser.getUsername());
                                newUser.setInternal(true);
                                newUser.setEmail(idpUser.getEmail());
                                newUser.setFirstName(idpUser.getFirstName());
                                newUser.setLastName(idpUser.getLastName());
                                newUser.setAdditionalInformation(idpUser.getAdditionalInformation());
                                newUser.setCreatedAt(new Date());
                                newUser.setUpdatedAt(newUser.getCreatedAt());
                                return userService.create(newUser);
                            })
                            .onErrorResumeNext(Single.error(new UserNotFoundException(email)));
                })
                .doOnSuccess(user -> new Thread(() -> emailService.send(Template.RESET_PASSWORD, user, client)).start())
                .doOnSuccess(user1 -> {
                    // reload principal
                    io.gravitee.am.identityprovider.api.User principal1 = reloadPrincipal(principal, user1);
                    auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(client).principal(principal1).type(EventType.FORGOT_PASSWORD_REQUESTED));
                })
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(client).principal(principal).type(EventType.FORGOT_PASSWORD_REQUESTED).throwable(throwable)))
                .toCompletable();
    }

    @Override
    public Single<User> addFactor(String userId, EnrolledFactor enrolledFactor) {
        return userService.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapSingle(user -> {
                    List<EnrolledFactor> enrolledFactors = user.getFactors();
                    if (enrolledFactors == null || enrolledFactors.isEmpty()) {
                        enrolledFactors = Collections.singletonList(enrolledFactor);
                    } else {
                        enrolledFactors.add(enrolledFactor);
                    }
                    user.setFactors(enrolledFactors);
                    return userService.update(user);
                });
    }

    private MaybeSource<Optional<Client>> clientSource(String audience) {
        if (audience == null) {
            return Maybe.just(Optional.empty());
        }

        return clientSyncService.findById(audience)
                .map(client -> Optional.of(client))
                .defaultIfEmpty(Optional.empty());
    }

    private boolean forceUserRegistration(Domain domain, Client client) {
        AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
        return accountSettings != null && accountSettings.isCompleteRegistrationWhenResetPassword();
    }

    private io.gravitee.am.identityprovider.api.User reloadPrincipal(io.gravitee.am.identityprovider.api.User principal, User user) {
        io.gravitee.am.identityprovider.api.User principal1 = new DefaultUser(user.getUsername());
        ((DefaultUser) principal1).setId(user.getId());
        ((DefaultUser) principal1).setAdditionalInformation(principal != null && principal.getAdditionalInformation() != null ? new HashMap<>(principal.getAdditionalInformation()) : new HashMap<>());
        principal1.getAdditionalInformation()
                .put(StandardClaims.NAME,
                        user.getDisplayName() != null ? user.getDisplayName() :
                                (user.getFirstName() != null ? user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : "") : user.getUsername()));
        return principal1;
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

    private void extractAdditionalInformation(User user, Map<String, Object> additionalInformation) {
        if (additionalInformation != null) {
            Map<String, Object> extraInformation = new HashMap<>(additionalInformation);
            if (user.getLoggedAt() != null) {
                extraInformation.put(io.gravitee.am.common.oidc.idtoken.Claims.auth_time, user.getLoggedAt().getTime() / 1000);
            }
            extraInformation.put(StandardClaims.SUB, user.getId());
            extraInformation.put(StandardClaims.PREFERRED_USERNAME, user.getUsername());
            if (user.getAdditionalInformation() != null) {
                user.getAdditionalInformation().putAll(extraInformation);
            } else {
                user.setAdditionalInformation(extraInformation);
            }
        }
    }

}
