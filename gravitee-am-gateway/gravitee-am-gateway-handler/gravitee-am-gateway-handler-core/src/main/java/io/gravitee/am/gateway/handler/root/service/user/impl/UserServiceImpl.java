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
import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.service.LoginAttemptGatewayService;
import io.gravitee.am.gateway.handler.common.service.RevokeTokenGatewayService;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserRegistrationIdpResolver;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.ForgotPasswordParameters;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.DummyRequest;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.EmailFormatInvalidException;
import io.gravitee.am.service.exception.EnforceUserIdentityException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserAlreadyVerifiedException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.LogoutAuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.validators.email.EmailValidator;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeSource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.Predicate;
import io.vertx.rxjava3.core.MultiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ID_TOKEN;
import static io.gravitee.am.model.Template.REGISTRATION_VERIFY;
import static io.gravitee.am.model.Template.RESET_PASSWORD;
import static io.reactivex.rxjava3.core.Completable.complete;
import static io.reactivex.rxjava3.core.Completable.error;
import static io.reactivex.rxjava3.core.Completable.fromRunnable;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Map.entry;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImpl implements UserService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private UserGatewayService userService;

    @Autowired
    @Qualifier("managementJwtParser")
    private JWTParser jwtParser;

    @Autowired
    private DomainReadService domainService;

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
    private LoginAttemptGatewayService loginAttemptService;

    @Autowired
    private CredentialGatewayService credentialService;

    @Autowired
    private UserValidator userValidator;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private RevokeTokenGatewayService tokenService;

    @Autowired
    private EmailValidator emailValidator;

    @Autowired
    private PasswordHistoryService passwordHistoryService;

    @Autowired
    private PasswordPolicyManager passwordPolicyManager;

    @Autowired
    private SubjectManager subjectManager;

    @Override
    public Maybe<UserToken> verifyToken(String token) {
        return Maybe.fromCallable(() -> jwtParser.parse(token))
                // no need to use the SubjectManager here to retrieve the user as verifyToken is used to check
                // tokens generated by AM for internal action (reset password, register confirmation...)
                .flatMap(jwt -> userService.findById(jwt.getSub())
                        .zipWith(clientSource(jwt.getAud()),
                                (user, optionalClient) -> new UserToken(user, optionalClient.orElse(null), jwt)));
    }

    @Override
    public Maybe<UserToken> confirmVerifyRegistration(String token) {
        return verifyToken(token).flatMap(userToken -> {
            if (!userToken.getUser().isInactive()) {
                final String username = userToken.getUser().getUsername();
                return Maybe.error(new UserAlreadyVerifiedException("The user [" + username + "] has already been verified!"));
            }
            userToken.getUser().setRegistrationCompleted(true);
            userToken.getUser().setEnabled(true);
            return userService.update(userToken.getUser()).flatMapMaybe(__ -> Maybe.just(userToken));
        }).doOnSuccess(userToken -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                .type(EventType.REGISTRATION_VERIFY_ACCOUNT)
                .reference(Reference.domain(domain.getId()))
                .client(userToken.getClient())
                .user(userToken.getUser()))
        ).doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                .type(EventType.REGISTRATION_VERIFY_ACCOUNT)
                .reference(Reference.domain(domain.getId()))
                .throwable(throwable))
        );
    }

    @Override
    public Single<UserToken> extractSessionFromIdToken(String idToken) {
        // The OP SHOULD accept ID Tokens when the RP identified by the ID Token's aud claim and/or sid claim has a current session
        // or had a recent session at the OP, even when the exp time has passed.
        return jwtService.decode(idToken, ID_TOKEN)
                .flatMap(jwt -> {
                    return clientSyncService.findByClientId(jwt.getAud())
                            .switchIfEmpty(Single.error(() -> new ClientNotFoundException(jwt.getAud())))
                            .flatMap(client -> {
                                return jwtService.decodeAndVerify(idToken, client, ID_TOKEN)
                                        .onErrorResumeNext(ex -> (ex instanceof ExpiredJWTException) ? Single.just(jwt) : Single.error(ex))
                                        .flatMap(jwt1 -> {
                                            return subjectManager.findUserBySub(jwt1)
                                                    .switchIfEmpty(Single.error(() -> new UserNotFoundException(jwt.getSub())))
                                                    .map(user -> {
                                                        if (!user.getReferenceId().equals(domain.getId())) {
                                                            throw new UserNotFoundException(jwt.getSub());
                                                        }
                                                        return new UserToken(user, client, jwt);
                                                    });
                                        });
                            });
                });
    }

    @Override
    public Single<RegistrationResponse> register(Client client, User user, io.gravitee.am.identityprovider.api.User principal, MultiMap queryParams) {
        // set user idp source
        var accountSettings = AccountSettings.getInstance(client, domain);
        final String source = UserRegistrationIdpResolver.getRegistrationIdpForUser(domain, client, user);
        final var rawPassword = user.getPassword();
        // validate user and then check user uniqueness
        return userValidator.validate(user)
                .andThen(userService.findByUsernameAndSource(user.getUsername(), source).isEmpty()
                        .flatMapMaybe(checkUserPresence(user, source))
                        .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(source)))
                        .flatMap(userProvider -> userProvider.create(convert(user)))
                        .flatMap(idpUser -> registerUser(user, accountSettings, source, idpUser, queryParams))
                        .flatMap(amUser -> sendVerifyAccountEmail(client, amUser, accountSettings, queryParams))
                        .flatMap(amUser -> createPasswordHistory(client, amUser, rawPassword, principal))
                        .flatMap(userService::enhance)
                        .map(enhancedUser -> buildRegistrationResponse(accountSettings, enhancedUser))
                        .doOnSuccess(registrationResponse -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                                .reference(Reference.domain(domain.getId()))
                                .client(client)
                                .principal(reloadPrincipal(principal, registrationResponse.getUser()))
                                .type(EventType.USER_REGISTERED))
                        )
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                                .reference(Reference.domain(domain.getId()))
                                .client(user.getClient())
                                .principal(principal)
                                .type(EventType.USER_REGISTERED)
                                .throwable(throwable))));
    }

    private static RegistrationResponse buildRegistrationResponse(Optional<AccountSettings> accountSettings, User user) {
        var noSendVerifyRegistration = accountSettings.filter(not(AccountSettings::isSendVerifyRegistrationAccountEmail));
        final boolean isAutoLogin = noSendVerifyRegistration.map(AccountSettings::isAutoLoginAfterRegistration).orElse(false);
        final String redirectUri = noSendVerifyRegistration.map(AccountSettings::getRedirectUriAfterRegistration).orElse(null);
        return new RegistrationResponse(user, redirectUri, isAutoLogin);
    }

    private Single<User> registerUser(User user, Optional<AccountSettings> accountSettings, String source, io.gravitee.am.identityprovider.api.User idpUser, MultiMap queryParams) {
        // AM 'users' collection is not made for authentication (but only management stuff)
        var now = new Date();
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
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        accountSettings.ifPresent(settings -> {
            if (settings.isAutoLoginAfterRegistration() && !settings.isSendVerifyRegistrationAccountEmail()) {
                user.setLoggedAt(now);
                user.setLoginsCount(1L);
            }
            if (settings.isSendVerifyRegistrationAccountEmail()) {
                user.setPreRegistration(true);
                user.setRegistrationCompleted(false);
                user.setEnabled(false);
                user.setRegistrationUserUri(domainService.buildUrl(domain, REGISTRATION_VERIFY.redirectUri(), queryParams));
            }
        });
        user.setLastPasswordReset(now);
        return userService.create(user);
    }

    private Function<Boolean, MaybeSource<? extends UserProvider>> checkUserPresence(User user, String source) {
        return isEmpty -> {
            if (!isEmpty) {
                return Maybe.error(new UserAlreadyExistsException(user.getUsername()));
            }

            // check if user provider exists
            return identityProviderManager.getUserProvider(source);
        };
    }

    private @NonNull Single<User> sendVerifyAccountEmail(Client client, User amUser, Optional<AccountSettings> accountSettings, MultiMap queryParams) {
        accountSettings.filter(AccountSettings::isSendVerifyRegistrationAccountEmail).ifPresent(sendEmail ->
                fromRunnable(() -> emailService.send(REGISTRATION_VERIFY, amUser, client, queryParams)).subscribe()
        );
        return Single.just(amUser);
    }

    @Override
    public Single<RegistrationResponse> confirmRegistration(Client client, User user, io.gravitee.am.identityprovider.api.User
            principal) {
        final var rawPassword = user.getPassword();
        // user has completed his account, add it to the idp
        return identityProviderManager.getUserProvider(user.getSource())
                .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(user.getSource())))
                // update the idp user
                .flatMap(userProvider -> userProvider.findByUsername(user.getUsername())
                        .switchIfEmpty(Single.error(() -> new UserNotFoundException(user.getUsername())))
                        .flatMap(idpUser -> userProvider.update(idpUser.getId(), convert(user)))
                        .onErrorResumeNext(ex -> {
                            if (ex instanceof UserNotFoundException) {
                                // idp user not found, create its account
                                return userProvider.create(convert(user));
                            }
                            return Single.error(ex);
                        }))
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
                .flatMap(amUser -> createPasswordHistory(client, amUser, rawPassword, principal))
                .flatMap(userService::enhance)
                .map(user1 -> {
                    AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                    return new RegistrationResponse(user1, accountSettings != null ? accountSettings.getRedirectUriAfterRegistration() : null, accountSettings != null ? accountSettings.isAutoLoginAfterRegistration() : false);
                })
                .doOnSuccess(response -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .client(user.getClient())
                        .principal(principal)
                        .type(EventType.REGISTRATION_CONFIRMATION)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .client(user.getClient())
                        .principal(principal)
                        .type(EventType.REGISTRATION_CONFIRMATION)
                        .throwable(throwable)));

    }

    @Override
    public Completable checkPassword(User user, String password, io.gravitee.am.identityprovider.api.User principal) {
        return identityProviderManager.getUserProvider(user.getSource())
                .switchIfEmpty(Maybe.error(() -> new UserProviderNotFoundException(user.getSource())))
                .flatMap(userProvider -> identityProviderManager.get(user.getSource())
                        .flatMap(provider -> provider.loadUserByUsername(new EndUserAuthentication(user.getUsername(), password, new SimpleAuthenticationContext(new DummyRequest())))))
                .ignoreElement();
    }

    @SuppressWarnings({"ReactiveStreamsUnusedPublisher", "ResultOfMethodCallIgnored"})
    @Override
    public Single<ResetPasswordResponse> resetPassword(Client client, User user, io.gravitee.am.identityprovider.api.User principal) {
        // get account settings
        final AccountSettings accountSettings = AccountSettings.getInstance(domain, client);

        // if user registration is not completed and force registration option is disabled throw invalid account exception
        if (TRUE.equals(user.isInactive()) && !forceUserRegistration(accountSettings)) {
            return Single.error(new AccountInactiveException("User needs to complete the activation process"));
        }

        // only idp manage password, find user idp and update its password
        return identityProviderManager.getUserProvider(user.getSource())
                .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(user.getSource())))
                // update the idp user
                .flatMap(userProvider -> {
                    // retrieve its technical from the idp and then update the password
                    // we can't rely on the external_id since the value can be different from IdP user ID
                    // see https://github.com/gravitee-io/issues/issues/8407
                    return userProvider.findByUsername(user.getUsername())
                            .switchIfEmpty(Single.error(() -> new UserNotFoundException(user.getUsername())))
                            .flatMap(idpUser -> passwordHistoryService
                                    .addPasswordToHistory(domain, user, user.getPassword(), principal, getPasswordPolicy(client, identityProviderManager.getIdentityProvider(user.getSource())))
                                    .switchIfEmpty(Single.just(new PasswordHistory()))
                                    .flatMap(passwordHistory -> userProvider.updatePassword(idpUser, user.getPassword())))
                            .onErrorResumeNext(ex -> {
                                if (ex instanceof UserNotFoundException && forceUserRegistration(accountSettings)) {
                                    // idp user not found, create its account, only if force registration is enabled
                                    return userProvider.create(convert(user));
                                }
                                return Single.error(ex);
                            });
                })
                // update the user in the AM repository
                .flatMap(idpUser -> {
                    // update 'users' collection for management and audit purpose
                    // if user was in pre-registration mode, end the registration process
                    if (TRUE.equals(user.isPreRegistration())) {
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
                    user.setForceResetPassword(FALSE);
                    // additional information
                    extractAdditionalInformation(user, idpUser.getAdditionalInformation());
                    // set login information
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
                            .client(client.getId())
                            .username(user1.getUsername())
                            .build();
                    return loginAttemptService.reset(domain, criteria).andThen(Single.just(user1));
                })
                // delete passwordless devices
                .flatMap(user1 -> {
                    if (accountSettings != null && accountSettings.isDeletePasswordlessDevicesAfterResetPassword()) {
                        return credentialService.deleteByUserId(domain, user1.getId())
                                .andThen(Single.just(user1));
                    }
                    return Single.just(user1);
                })
                .flatMap(user1 -> {
                    if (accountSettings != null && accountSettings.isResetPasswordInvalidateTokens()) {
                        return tokenService.deleteByUser(user1)
                                .toSingleDefault(user1)
                                .onErrorResumeNext(err -> {
                                    logger.warn("Tokens not invalidated for user {} due to : {}", user1.getId(), err.getMessage());
                                    return Single.just(user1);
                                });
                    }
                    return Single.just(user1);
                })
                .flatMap(userService::enhance)
                .map(user1 -> new ResetPasswordResponse(user1, accountSettings != null ? accountSettings.getRedirectUriAfterResetPassword() : null, accountSettings != null ? accountSettings.isAutoLoginAfterResetPassword() : false))
                .doOnSuccess(response -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .client(client)
                        .principal(principal)
                        .type(EventType.USER_PASSWORD_RESET)
                        .user(user)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .client(client)
                        .principal(principal)
                        .type(EventType.USER_PASSWORD_RESET)
                        .user(user)
                        .throwable(throwable)));
    }

    @Override
    public Completable forgotPassword(ForgotPasswordParameters params, Client client, io.gravitee.am.identityprovider.api.User principal) {

        final String email = params.getEmail();
        if (email != null && !emailValidator.validate(email)) {
            return error(new EmailFormatInvalidException(email));
        }

        if (client.getIdentityProviders() == null || client.getIdentityProviders().isEmpty()) {
            return Completable.error(new UserNotFoundException(email));
        }

        return userService.findByCriteria(params.buildCriteria())
                .flatMap(users -> {
                    List<User> foundUsers = narrowUsersForForgotPassword(client, users);

                    // If multiple results, check if ConfirmIdentity isn't required before returning the first User.
                    if (foundUsers.size() == 1 || (foundUsers.size() > 1 && !params.isConfirmIdentityEnabled())) {
                        final User user = foundUsers.get(0);

                        // check if user can update its password according to its identity provider type
                        return identityProviderManager.getUserProvider(user.getSource())
                                .switchIfEmpty(Single.error(() -> new UserInvalidException("User [ " + user.getUsername() + " ] cannot be updated because its identity provider does not support user provisioning")))
                                .flatMap(userProvider -> {
                                    // if user registration is not completed and force registration option is disabled throw invalid account exception
                                    AccountSettings accountSettings = AccountSettings.getInstance(domain, client);

                                    if (user.isInactive() && !forceUserRegistration(accountSettings)) {
                                        return Single.error(new AccountInactiveException("User [ " + user.getUsername() + " ] needs to complete the activation process"));
                                    }

                                    if (!user.isEnabled() && !user.isInactive()) {
                                        return Single.error(new AccountInactiveException("User [ " + user.getUsername() + " ] is disabled."));
                                    }

                                    // fetch latest information from the identity provider and return the user
                                    return userProvider.findByUsername(user.getUsername())
                                            .map(Optional::ofNullable)
                                            .defaultIfEmpty(Optional.empty())
                                            .flatMap(optUser -> {
                                                if (optUser.isEmpty()) {
                                                    return Single.just(user);
                                                }
                                                return userService.update(enhanceUser(user, optUser.get()));
                                            });
                                });
                    }

                    if (foundUsers.size() > 1) {
                        return Single.error(new EnforceUserIdentityException());
                    }
                    // if user has no email or email is unknown
                    // fallback to registered user providers if user has never been authenticated

                    if (isNullOrEmpty(params.getEmail()) & StringUtils.isEmpty(params.getUsername())) {
                        // no user found using criteria. email & username are missing, unable to search the user through UserProvider
                        return Single.error(new UserNotFoundException(email));
                    }

                    // Single field search using email or username with IdP linked to the clientApp
                    // email used in priority for backward compatibility
                    return Observable.fromIterable(client.getIdentityProviders())
                            .flatMapSingle(authProvider -> identityProviderManager.getUserProvider(authProvider.getIdentity())
                                    .flatMapSingle(userProvider -> {
                                        final String username = params.getUsername();

                                        // search by username, email or both
                                        final Maybe<io.gravitee.am.identityprovider.api.User> findQuery = (!isNullOrEmpty(username) && !isNullOrEmpty(email)) ?
                                                findByUsernameAndEmail(userProvider, params) :
                                                (isNullOrEmpty(email) ? userProvider.findByUsername(username) : userProvider.findByEmail(email));

                                        return findQuery
                                                .map(user -> Optional.of(new UserAuthentication(user, authProvider.getIdentity())))
                                                .defaultIfEmpty(Optional.empty())
                                                .onErrorReturnItem(Optional.empty());
                                    })
                                    .defaultIfEmpty(Optional.empty()))
                            .takeUntil((Predicate<? super Optional<UserAuthentication>>) Optional::isPresent)
                            .lastOrError()
                            .flatMap(optional -> {
                                // be sure to not duplicate an existing user
                                if (optional.isEmpty()) {
                                    return Single.error(new UserNotFoundException());
                                }
                                final UserAuthentication idpUser = optional.get();
                                return userService.findByUsernameAndSource(idpUser.getUser().getUsername(), idpUser.getSource())
                                        .switchIfEmpty(Maybe.defer(() -> userService.findByExternalIdAndSource(idpUser.getUser().getId(), idpUser.getSource())))
                                        .map(Optional::ofNullable)
                                        .defaultIfEmpty(Optional.empty())
                                        .flatMap(optEndUser -> {
                                            if (optEndUser.isEmpty()) {
                                                return userService.create(convert(idpUser.getUser(), idpUser.getSource()));
                                            }
                                            return userService.update(enhanceUser(optEndUser.get(), idpUser.getUser()));
                                        });
                            })
                            .onErrorResumeNext(exception -> Single.error(new UserNotFoundException(email != null ? email : params.getUsername())));
                })
                .doOnSuccess(user -> new Thread(() -> emailService.send(RESET_PASSWORD, user, client)).start())
                .doOnSuccess(user1 -> {
                    // reload principal
                    io.gravitee.am.identityprovider.api.User principal1 = reloadPrincipal(principal, user1);
                    auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                            .reference(Reference.domain(domain.getId()))
                            .client(client)
                            .principal(principal1)
                            .type(EventType.FORGOT_PASSWORD_REQUESTED));
                })
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .client(client)
                        .principal(principal)
                        .type(EventType.FORGOT_PASSWORD_REQUESTED)
                        .throwable(throwable)))
                .ignoreElement();
    }

    private List<User> narrowUsersForForgotPassword(Client client, List<User> users) {
        List<User> foundUsers = new ArrayList<>(users);

        // filter by identity provider
        if (client.getIdentityProviders() != null && !client.getIdentityProviders().isEmpty()) {
            foundUsers = users.stream()
                    .flatMap(u -> client.getIdentityProviders().stream().map(appIdp -> entry(u, appIdp.getIdentity())))
                    .filter(entry -> {
                        var user = entry.getKey();
                        var identity = entry.getValue();
                        return Objects.equals(user.getSource(), identity);
                    }).map(Entry::getKey).collect(toList());
        }

        if (foundUsers.size() > 1) {
            // try to filter by latest application used
            List<User> filteredSourceUsers = users
                    .stream()
                    .filter(u -> u.getClient() == null || client.getId().equals(u.getClient()))
                    .collect(toList());

            if (!filteredSourceUsers.isEmpty()) {
                foundUsers = new ArrayList<>(filteredSourceUsers);
            }
        }
        return foundUsers;
    }

    /**
     * Find user by username and by email, if both search methods return a user with an inconsistent user id,
     * then the Maybe returned by this method will be empty.
     *
     * @param userProvider
     * @param params
     * @return
     */
    private static Maybe<io.gravitee.am.identityprovider.api.User> findByUsernameAndEmail(UserProvider userProvider, ForgotPasswordParameters params) {
        return Maybe.zip(userProvider.findByUsername(params.getUsername()), userProvider.findByEmail(params.getEmail()), (byUsername, byEmail) -> Map.of(byEmail.getId().equals(byUsername.getId()), byEmail))
                .filter(tuple -> tuple.containsKey(Boolean.TRUE))
                .map(tuple -> tuple.get(Boolean.TRUE));
    }

    @Override
    public Completable logout(User user, boolean invalidateTokens, io.gravitee.am.identityprovider.api.User principal) {
        return userService.findById(user.getId())
                .flatMapCompletable(user1 -> {
                    user1.setLastLogoutAt(new Date());
                    user1.setUpdatedAt(new Date());
                    return userService.update(user1)
                            .flatMapCompletable(user2 -> {
                                if (invalidateTokens) {
                                    return tokenService.deleteByUser(user2);
                                }
                                return complete();
                            });
                })
                .doOnComplete(() -> auditService.report(AuditBuilder.builder(LogoutAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .client(user.getClient())
                        .user(user)
                        .type(EventType.USER_LOGOUT)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(LogoutAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .client(user.getClient())
                        .user(user)
                        .type(EventType.USER_LOGOUT)
                        .throwable(throwable)));
    }

    @Override
    public Single<User> upsertFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal) {
        return userService.upsertFactor(userId, enrolledFactor, principal);
    }

    @Override
    public Completable setMfaEnrollmentSkippedTime(Client client, User user) {
        if (nonNull(user)) {
            Date now = new Date();
            long skipTime = ofNullable(getMfaEnrollmentSettings(client).getSkipTimeSeconds()).orElse(ConstantKeys.DEFAULT_ENROLLMENT_SKIP_TIME_SECONDS) * 1000L;
            if (isNull(user.getMfaEnrollmentSkippedAt()) || (user.getMfaEnrollmentSkippedAt().getTime() + skipTime) < now.getTime()) {
                user.setMfaEnrollmentSkippedAt(now);
                return userService.update(user).ignoreElement();
            }
        }
        return complete();
    }

    private EnrollSettings getMfaEnrollmentSettings(Client client) {
        return ofNullable(client)
                .map(Client::getMfaSettings)
                .map(MFASettings::getEnroll)
                .orElse(new EnrollSettings());
    }

    private Maybe<Optional<Client>> clientSource(String audience) {
        if (audience == null) {
            return Maybe.just(Optional.empty());
        }

        return clientSyncService.findById(audience)
                .switchIfEmpty(clientSyncService.findByClientId(audience))
                .map(Optional::of)
                .switchIfEmpty(Maybe.just(Optional.empty()));
    }

    private boolean forceUserRegistration(AccountSettings accountSettings) {
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
            user.getAdditionalInformation().forEach(additionalInformation::putIfAbsent);
        }
        idpUser.setAdditionalInformation(additionalInformation);
        return idpUser;
    }

    private User convert(io.gravitee.am.identityprovider.api.User idpUser, String source) {
        User newUser = new User();
        newUser.setId(RandomString.generate());
        newUser.setExternalId(idpUser.getId());
        newUser.setUsername(idpUser.getUsername());
        newUser.setInternal(true);
        newUser.setEmail(idpUser.getEmail());
        newUser.setFirstName(idpUser.getFirstName());
        newUser.setLastName(idpUser.getLastName());
        newUser.setAdditionalInformation(idpUser.getAdditionalInformation());
        newUser.setReferenceType(ReferenceType.DOMAIN);
        newUser.setReferenceId(domain.getId());
        newUser.setSource(source);
        return newUser;
    }

    private void extractAdditionalInformation(User user, Map<String, Object> additionalInformation) {
        if (additionalInformation != null) {
            Map<String, Object> extraInformation = new HashMap<>(additionalInformation);
            if (user.getLoggedAt() != null) {
                extraInformation.put(io.gravitee.am.common.oidc.idtoken.Claims.AUTH_TIME, user.getLoggedAt().getTime() / 1000);
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

    private User enhanceUser(User user, io.gravitee.am.identityprovider.api.User idpUser) {
        if (idpUser.getEmail() != null) {
            user.setEmail(idpUser.getEmail());
        }
        if (idpUser.getFirstName() != null) {
            user.setFirstName(idpUser.getFirstName());
        }
        if (idpUser.getLastName() != null) {
            user.setLastName(idpUser.getLastName());
        }
        if (idpUser.getAdditionalInformation() != null) {
            Map<String, Object> additionalInformation = user.getAdditionalInformation() != null ? new HashMap<>(user.getAdditionalInformation()) : new HashMap<>();
            additionalInformation.putAll(idpUser.getAdditionalInformation());
            user.setAdditionalInformation(additionalInformation);
        }
        return user;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Single<User> createPasswordHistory(Client client, User user, String rawPassword, io.gravitee.am.identityprovider.api.User principal) {
        final var provider = identityProviderManager.getIdentityProvider(user.getSource());
        passwordHistoryService
                .addPasswordToHistory(domain, user, rawPassword, principal, getPasswordPolicy(client, provider))
                .subscribe(passwordHistory -> logger.debug("Created password history for user {}", user.getUsername()),
                        throwable -> logger.debug("Failed to create password history", throwable));
        return Single.just(user);
    }

    private PasswordPolicy getPasswordPolicy(Client client, IdentityProvider provider) {
        return passwordPolicyManager.getPolicy(client, provider).orElse(null);
    }

    private static final class UserAuthentication {

        private final io.gravitee.am.identityprovider.api.User user;
        private final String source;

        public UserAuthentication(io.gravitee.am.identityprovider.api.User user, String source) {
            this.user = user;
            this.source = source;
        }

        public io.gravitee.am.identityprovider.api.User getUser() {
            return user;
        }

        public String getSource() {
            return source;
        }

    }
}
