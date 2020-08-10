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
import io.gravitee.am.common.email.Email;
import io.gravitee.am.common.email.EmailBuilder;
import io.gravitee.am.common.exception.authentication.AccountInactiveException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.certificate.jwt.JWTBuilder;
import io.gravitee.am.gateway.certificate.jwt.JWTParser;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.email.EmailManager;
import io.gravitee.am.gateway.handler.email.EmailService;
import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_IDP_PREFIX = "default-idp-";

    @Value("${gateway.url:http://localhost:8092}")
    private String gatewayUrl;

    @Value("${user.resetPassword.email.subject:Please reset your password}")
    private String resetPasswordSubject;

    @Value("${user.resetPassword.token.expire-after:86400}")
    private Integer expireAfter;

    @Autowired
    private io.gravitee.am.gateway.handler.common.user.UserService userService;

    @Autowired
    private JWTParser jwtParser;

    @Autowired
    private JWTBuilder jwtBuilder;

    @Autowired
    private Domain domain;

    @Autowired
    private EmailService emailService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private EmailManager emailManager;

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
        AccountSettings accountSettings = getAccountSettings(domain, client);
        final String source = accountSettings.getDefaultIdentityProviderForRegistration() != null
                ? accountSettings.getDefaultIdentityProviderForRegistration()
                : (user.getSource() == null ? DEFAULT_IDP_PREFIX + domain.getId() : user.getSource());

        // check user uniqueness
        return userService.findByDomainAndUsernameAndSource(domain.getId(), user.getUsername(), source)
                .isEmpty()
                .map(isEmpty -> {
                    if (!isEmpty) {
                        throw new UserAlreadyExistsException(user.getUsername());
                    }
                    return true;
                })
                // check if user provider exists
                .flatMap(irrelevant -> identityProviderManager.getUserProvider(source)
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
                            user.setDomain(domain.getId());
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
                        .map(user1 -> new RegistrationResponse(user1, accountSettings != null ? accountSettings.getRedirectUriAfterRegistration() : null, accountSettings != null ? accountSettings.isAutoLoginAfterRegistration() : false))
                        .doOnSuccess(registrationResponse -> {
                            // reload principal
                            final User user1 = registrationResponse.getUser();
                            io.gravitee.am.identityprovider.api.User principal1 = new DefaultUser(user1.getUsername());
                            ((DefaultUser) principal1).setId(user1.getId());
                            ((DefaultUser) principal1).setAdditionalInformation(principal.getAdditionalInformation());
                            principal1.getAdditionalInformation()
                                    .put(StandardClaims.NAME,
                                            user1.getDisplayName() != null ? user1.getDisplayName() :
                                                    (user1.getFirstName() != null ? user1.getFirstName() + (user1.getLastName() != null ? " " + user1.getLastName() : "") : user1.getUsername()));
                            auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal1).type(EventType.USER_REGISTERED));
                        })
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.USER_REGISTERED).throwable(throwable)))
                );
    }

    @Override
    public Single<RegistrationResponse> confirmRegistration(Client client, User user, io.gravitee.am.identityprovider.api.User principal) {
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
                    AccountSettings accountSettings = getAccountSettings(domain, client);
                    if (accountSettings != null && accountSettings.isAutoLoginAfterRegistration()) {
                        user.setLoggedAt(new Date());
                        user.setLoginsCount(1l);
                    }
                    return userService.update(user);
                })
                .flatMap(userService::enhance)
                .map(user1 -> {
                    AccountSettings accountSettings = getAccountSettings(domain, client);
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
                    user.setPassword(null);
                    user.setExternalId(idpUser.getId());
                    user.setUpdatedAt(new Date());
                    // additional information
                    extractAdditionalInformation(user, idpUser.getAdditionalInformation());
                    // set login information
                    AccountSettings accountSettings = getAccountSettings(domain, client);
                    if (accountSettings != null && accountSettings.isAutoLoginAfterResetPassword()) {
                        user.setLoggedAt(new Date());
                        user.setLoginsCount(user.getLoginsCount() + 1);
                    }
                    return userService.update(user);
                })
                // reset login attempts in case of reset password action
                .flatMap(user1 -> {
                    LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                            .domain(user1.getDomain())
                            .client(user1.getClient())
                            .username(user1.getUsername())
                            .build();
                    return loginAttemptService.reset(criteria).andThen(Single.just(user1));
                })
                .flatMap(userService::enhance)
                .map(user1 -> {
                    AccountSettings accountSettings = getAccountSettings(domain, client);
                    return new ResetPasswordResponse(user1, accountSettings != null ? accountSettings.getRedirectUriAfterResetPassword() : null, accountSettings != null ? accountSettings.isAutoLoginAfterResetPassword() : false);
                })
                .doOnSuccess(response -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.USER_PASSWORD_RESET)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.USER_PASSWORD_RESET).throwable(throwable)));
    }

    @Override
    public Completable forgotPassword(String email, Client client, io.gravitee.am.identityprovider.api.User principal) {
        return userService.findByDomainAndEmail(domain.getId(), email, false)
                .flatMap(users -> {
                    Optional<User> optionalUser = users
                            .stream()
                            .filter(user -> user.getEmail() != null && email.toLowerCase().equals(user.getEmail().toLowerCase()))
                            .findFirst();
                    // if user has no email or email is unknown throw user not found exception
                    if (!optionalUser.isPresent()) {
                        return Single.error(new UserNotFoundException(email));
                    }

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

                })
                .doOnSuccess(user -> new Thread(() -> completeForgotPassword(user, client)).start())
                .doOnSuccess(user1 -> {
                    // reload principal
                    io.gravitee.am.identityprovider.api.User principal1 = new DefaultUser(user1.getUsername());
                    ((DefaultUser) principal1).setId(user1.getId());
                    ((DefaultUser) principal1).setAdditionalInformation(principal != null && principal.getAdditionalInformation() != null ? new HashMap<>(principal.getAdditionalInformation()) : new HashMap<>());
                    principal1.getAdditionalInformation()
                            .put(StandardClaims.NAME,
                                    user1.getDisplayName() != null ? user1.getDisplayName() :
                                            (user1.getFirstName() != null ? user1.getFirstName() + (user1.getLastName() != null ? " " + user1.getLastName() : "") : user1.getUsername()));
                    auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(client).principal(principal1).type(EventType.FORGOT_PASSWORD_REQUESTED));
                })
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(client).principal(principal).type(EventType.FORGOT_PASSWORD_REQUESTED).throwable(throwable)))
                .toCompletable();
    }

    private void completeForgotPassword(User user, Client client) {
        io.gravitee.am.model.Email email = emailManager.getEmail(getTemplateName(client), resetPasswordSubject, expireAfter);
        Email email1 = convert(user, client, email, "/resetPassword", "resetPasswordUrl");
        emailService.send(email1, user, client);
    }

    private Email convert(User user, Client client, io.gravitee.am.model.Email email, String redirectUri, String redirectUriName) {
        Map<String, Object> params = prepareEmail(user, client, email.getExpiresAfter(), redirectUri, redirectUriName);
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

    private Map<String, Object> prepareEmail(User user, Client client, int expiresAfter, String redirectUri, String redirectUriName) {
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.iat, new Date().getTime() / 1000);
        claims.put(Claims.exp, new Date(System.currentTimeMillis() + (expiresAfter * 1000)).getTime() / 1000);
        claims.put(Claims.sub, user.getId());
        claims.put(Claims.aud, client.getId());
        claims.put(StandardClaims.EMAIL, user.getEmail());
        claims.put(StandardClaims.GIVEN_NAME, user.getFirstName());
        claims.put(StandardClaims.FAMILY_NAME, user.getLastName());

        String token = jwtBuilder.sign(new JWT(claims));

        String entryPoint = gatewayUrl;
        if (entryPoint != null && entryPoint.endsWith("/")) {
            entryPoint = entryPoint.substring(0, entryPoint.length() - 1);
        }

        // building the redirectUrl
        StringBuilder sb = new StringBuilder();
        sb
                .append(entryPoint)
                .append("/")
                .append(user.getDomain())
                .append(redirectUri)
                .append("?token=")
                .append(token);
        if (client != null) {
            sb
                    .append("&client_id=")
                    .append(client.getClientId());
        }
        String redirectUrl = sb.toString();
        Map<String, Object> params = new HashMap<>();
        params.put("user", user);
        params.put(redirectUriName, redirectUrl);
        params.put("token", token);
        params.put("expireAfterSeconds", expiresAfter);

        return params;
    }

    private String getTemplateName(Client client) {
        return Template.RESET_PASSWORD.template()
                + ((client != null) ? EmailManager.TEMPLATE_NAME_SEPARATOR +  client.getId() : "");
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
        AccountSettings accountSettings = getAccountSettings(domain, client);
        return accountSettings != null && accountSettings.isCompleteRegistrationWhenResetPassword();
    }

    private AccountSettings getAccountSettings(Domain domain, Client client) {
        // if client has no account config return domain config
        if (client != null) {
            if (client.getAccountSettings() == null) {
                return domain.getAccountSettings();
            }

            // if client configuration is not inherited return the client config
            if (!client.getAccountSettings().isInherited()) {
                return client.getAccountSettings();
            }
        }

        // return domain config
        return domain.getAccountSettings();
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
