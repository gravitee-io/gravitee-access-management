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
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTBuilder;
import io.gravitee.am.gateway.handler.common.jwt.JWTParser;
import io.gravitee.am.gateway.handler.email.EmailManager;
import io.gravitee.am.gateway.handler.email.EmailService;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
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
    private UserRepository userRepository;

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

    @Override
    public Maybe<User> findById(String id) {
        return userRepository.findById(id);
    }

    @Override
    public Maybe<UserToken> verifyToken(String token) {
        return Maybe.fromCallable(() -> jwtParser.parse(token))
                .flatMap(jwt -> userRepository.findById(jwt.getSub()).zipWith(clientSource(jwt.getAud()), (user, optionalClient) -> new UserToken(user, optionalClient.orElse(null))));
    }

    @Override
    public Single<User> register(User user, io.gravitee.am.identityprovider.api.User principal) {
        // set user idp source
        final String source = user.getSource() == null ? DEFAULT_IDP_PREFIX + domain.getId() : user.getSource();

        // check user uniqueness
        return userRepository.findByDomainAndUsernameAndSource(domain.getId(), user.getUsername(), source)
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
                            // set date information
                            user.setCreatedAt(new Date());
                            user.setUpdatedAt(user.getCreatedAt());
                            return userRepository.create(user);
                        })
                        .doOnSuccess(user1 -> {
                            // reload principal
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
    public Completable confirmRegistration(User user, io.gravitee.am.identityprovider.api.User principal) {
        // user has completed his account, add it to the idp
        return identityProviderManager.getUserProvider(user.getSource())
                .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                .flatMapSingle(userProvider -> userProvider.create(convert(user)))
                .flatMap(idpUser -> {
                    // update 'users' collection for management and audit purpose
                    user.setPassword(null);
                    user.setRegistrationCompleted(true);
                    user.setEnabled(true);
                    user.setExternalId(idpUser.getId());
                    user.setUpdatedAt(new Date());
                    return userRepository.update(user);
                })
                .toCompletable()
                .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.REGISTRATION_CONFIRMATION)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.REGISTRATION_CONFIRMATION).throwable(throwable)));

    }

    @Override
    public Completable resetPassword(User user, io.gravitee.am.identityprovider.api.User principal) {
        // only idp manage password, find user idp and update its password
        return identityProviderManager.getUserProvider(user.getSource())
                .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                .flatMapSingle(userProvider -> userProvider.update(user.getExternalId(), convert(user)))
                .flatMap(idpUser -> {
                    // update 'users' collection for management and audit purpose
                    user.setPassword(null);
                    user.setExternalId(idpUser.getId());
                    user.setUpdatedAt(new Date());
                    return userRepository.update(user);
                })
                .toCompletable()
                .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.USER_PASSWORD_RESET)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).domain(domain.getId()).client(user.getClient()).principal(principal).type(EventType.USER_PASSWORD_RESET).throwable(throwable)));
    }

    @Override
    public Completable forgotPassword(String email, Client client, io.gravitee.am.identityprovider.api.User principal) {
        return userRepository.findByDomainAndEmail(domain.getId(), email)
                .map(users -> users.stream().filter(user -> user.isInternal()).findFirst())
                .flatMapMaybe(optionalUser -> optionalUser.isPresent() ? Maybe.just(optionalUser.get()) : Maybe.empty())
                .switchIfEmpty(Maybe.error(new UserNotFoundException(email)))
                .toSingle()
                .doOnSuccess(user -> new Thread(() -> completeForgotPassword(user, client)).start())
                .doOnSuccess(user1 -> {
                    // reload principal
                    io.gravitee.am.identityprovider.api.User principal1 = new DefaultUser(user1.getUsername());
                    ((DefaultUser) principal1).setId(user1.getId());
                    ((DefaultUser) principal1).setAdditionalInformation(principal.getAdditionalInformation());
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

        String redirectUrl = entryPoint + "/" + user.getDomain() + redirectUri + "?token=" + token;

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

    private io.gravitee.am.identityprovider.api.User convert(User user) {
        DefaultUser idpUser = new DefaultUser(user.getUsername());
        idpUser.setCredentials(user.getPassword());

        Map<String, Object> additionalInformation = new HashMap<>();
        if (user.getFirstName() != null) {
            additionalInformation.put(StandardClaims.GIVEN_NAME, user.getFirstName());
        }
        if (user.getLastName() != null) {
            additionalInformation.put(StandardClaims.FAMILY_NAME, user.getLastName());
        }
        if (user.getEmail() != null) {
            additionalInformation.put(StandardClaims.EMAIL, user.getEmail());
        }
        if (user.getAdditionalInformation() != null) {
            user.getAdditionalInformation().forEach((k, v) -> additionalInformation.putIfAbsent(k, v));
        }
        idpUser.setAdditionalInformation(additionalInformation);
        return idpUser;
    }

}
