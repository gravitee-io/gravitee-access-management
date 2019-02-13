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

import io.gravitee.am.common.email.Email;
import io.gravitee.am.common.email.EmailBuilder;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.jsonwebtoken.JwtBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
    private UserRepository userRepository;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtBuilder jwtBuilder;

    @Autowired
    private EmailManager emailManager;

    @Override
    public Single<Page<User>> search(String domain, String query, int limit) {
        return userService.search(domain, query, limit);
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
    public Single<User> create(String domain, NewUser newUser) {
        return Single.just(newUser.isPreRegistration())
                .flatMap(isPreRegistration -> {
                    // set source (currently default idp)
                    newUser.setSource(DEFAULT_IDP_PREFIX + domain);
                    newUser.setInternal(true);
                    if (isPreRegistration) {
                        // in pre registration mode an email will be sent to the user to complete his account
                        // and user will only be stored as 'readonly' account
                        newUser.setPassword(null);
                        newUser.setRegistrationCompleted(false);
                        newUser.setEnabled(false);
                        return userService.create(domain, newUser)
                                .doOnSuccess(user -> new Thread(() -> completeUserRegistration(user)).start());
                    } else {
                        newUser.setRegistrationCompleted(true);
                        newUser.setEnabled(true);
                        newUser.setDomain(domain);
                        // store user in its identity provider
                        return identityProviderManager.getUserProvider(newUser.getSource())
                                .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(newUser.getSource())))
                                .flatMapSingle(userProvider -> userProvider.create(convert(newUser))
                                        .onErrorResumeNext(ex -> {
                                            if (ex instanceof UserAlreadyExistsException) {
                                                return userProvider.findByUsername(newUser.getUsername()).toSingle();
                                            } else {
                                                return Single.error(ex);
                                            }
                                        })
                                )
                                .flatMap(idpUser -> {
                                    // AM 'users' collection is not made for authentication (but only management stuff)
                                    // clear password
                                    newUser.setPassword(null);
                                    // set external id
                                    newUser.setExternalId(idpUser.getId());
                                    return userService.create(domain, newUser);
                                });
                    }
                });
    }

    @Override
    public Single<User> update(String domain, String id, UpdateUser updateUser) {
        return userService.findById(id)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(id)))
                .flatMapSingle(user -> identityProviderManager.getUserProvider(user.getSource())
                        .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                        .flatMapSingle(userProvider ->  userProvider.update(user.getExternalId(), convert(user.getUsername(), updateUser)))
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
                        }));
    }

    @Override
    public Completable delete(String userId) {
        return userService.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapCompletable(user -> identityProviderManager.getUserProvider(user.getSource())
                        .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                        .flatMapCompletable(userProvider -> userProvider.delete(user.getExternalId()))
                        .andThen(userRepository.delete(userId))
                        .onErrorResumeNext(ex -> {
                            if (ex instanceof UserNotFoundException) {
                                // idp user does not exist, only remove AM user
                                return userRepository.delete(userId);
                            }
                            return Completable.error(ex);
                        }));
    }

    @Override
    public Completable resetPassword(String domain, String userId, String password) {
        return userService.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapSingle(user -> identityProviderManager.getUserProvider(user.getSource())
                        .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                        .flatMapSingle(userProvider -> {
                            user.setPassword(password);
                            return userProvider.update(user.getExternalId(), convert(user))
                                    .onErrorResumeNext(ex -> {
                                        if (ex instanceof UserNotFoundException) {
                                            // idp user not found, create its account
                                            return userProvider.create(convert(user));
                                        }
                                        return Single.error(ex);
                                    });
                        })
                        .flatMap(idpUser -> {
                            if (user.isPreRegistration()) {
                                user.setRegistrationCompleted(true);
                            }
                            return userRepository.update(user);
                        })).toCompletable();
    }

    @Override
    public Completable sendRegistrationConfirmation(String userId) {
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
                .toSingle()
                .toCompletable();
    }

    private void completeUserRegistration(User user) {
        final String templateName = getTemplateName(user);
        io.gravitee.am.model.Email email = emailManager.getEmail(templateName, registrationSubject, expireAfter);
        Email email1 = convert(user, email, "/confirmRegistration", "registrationUrl");
        emailService.send(email1);
    }

    private Email convert(User user, io.gravitee.am.model.Email email, String redirectUri, String redirectUriName) {
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
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.iat, new Date().getTime() / 1000);
        claims.put(Claims.exp, new Date(System.currentTimeMillis() + (expiresAfter * 1000)).getTime() / 1000);
        claims.put(Claims.sub, user.getId());
        if (user.getClient() != null) {
            claims.put(Claims.aud, user.getClient());
        }
        claims.put(StandardClaims.EMAIL, user.getEmail());
        claims.put(StandardClaims.GIVEN_NAME, user.getFirstName());
        claims.put(StandardClaims.FAMILY_NAME, user.getLastName());

        final String token = jwtBuilder.setClaims(claims).compact();

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

    private String getTemplateName(User user) {
        return Template.REGISTRATION_CONFIRMATION.template()
                + EmailManager.TEMPLATE_NAME_SEPARATOR
                + user.getDomain()
                + ((user.getClient() != null) ? EmailManager.TEMPLATE_NAME_SEPARATOR +  user.getClient() : "");
    }

    private io.gravitee.am.identityprovider.api.User convert(NewUser newUser) {
        DefaultUser user = new DefaultUser(newUser.getUsername());
        user.setCredentials(newUser.getPassword());

        Map<String, Object> additionalInformation = new HashMap<>();
        if (newUser.getFirstName() != null) {
            additionalInformation.put(StandardClaims.GIVEN_NAME, newUser.getFirstName());
        }
        if (newUser.getLastName() != null) {
            additionalInformation.put(StandardClaims.FAMILY_NAME, newUser.getLastName());
        }
        if (newUser.getEmail() != null) {
            additionalInformation.put(StandardClaims.EMAIL, newUser.getEmail());
        }
        if (newUser.getAdditionalInformation() != null) {
            newUser.getAdditionalInformation().forEach((k, v) -> additionalInformation.putIfAbsent(k, v));
        }
        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    private io.gravitee.am.identityprovider.api.User convert(String username, UpdateUser updateUser) {
        // update additional information
        DefaultUser user = new DefaultUser(username);
        Map<String, Object> additionalInformation = new HashMap<>();
        if (updateUser.getFirstName() != null) {
            additionalInformation.put(StandardClaims.GIVEN_NAME, updateUser.getFirstName());
        }
        if (updateUser.getLastName() != null) {
            additionalInformation.put(StandardClaims.FAMILY_NAME, updateUser.getLastName());
        }
        if (updateUser.getEmail() != null) {
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
