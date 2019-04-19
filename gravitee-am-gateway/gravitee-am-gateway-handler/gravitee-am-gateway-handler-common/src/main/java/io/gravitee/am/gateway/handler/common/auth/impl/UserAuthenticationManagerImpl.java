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
package io.gravitee.am.gateway.handler.common.auth.impl;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.authentication.AccountDisabledException;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.gravitee.am.service.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.service.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.AuthenticationAuditBuilder;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationManagerImpl implements UserAuthenticationManager {

    private final Logger logger = LoggerFactory.getLogger(UserAuthenticationManagerImpl.class);
    private static final String GROUP_MAPPER_PREFIX = "group";
    private static final String SOURCE_FIELD = "source";
    private static final String GROUP_MAPPING_ATTRIBUTE = "_RESERVED_AM_GROUP_MAPPING_";

    @Autowired
    private UserService userService;

    @Autowired
    private Domain domain;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private AuditService auditService;

    @Override
    public Single<User> authenticate(Client client, Authentication authentication) {
        logger.debug("Trying to authenticate [{}]", authentication);

        // Get identity providers associated to a client
        // For each idp, try to authenticate a user
        // Try to authenticate while the user can not be authenticated
        // If user can't be authenticated, send an exception
        if (client.getIdentities() == null || client.getIdentities().isEmpty()) {
            logger.error("No identity provider found for client : " + client.getClientId());
            return Single.error(new BadCredentialsException("No identity provider found for client : " + client.getClientId()));
        }

        return Observable.fromIterable(client.getIdentities())
                .flatMapMaybe(authProvider -> authenticate0(client, authentication, authProvider))
                .takeUntil(userAuthentication -> userAuthentication.getUser() != null)
                .lastOrError()
                .flatMap(userAuthentication -> {
                    io.gravitee.am.identityprovider.api.User user = userAuthentication.getUser();
                    if (user == null) {
                        Throwable lastException = userAuthentication.getLastException();
                        if (lastException != null) {
                            if (lastException instanceof BadCredentialsException) {
                                return Single.error(new BadCredentialsException("The credentials you entered are invalid", lastException));
                            } else if (lastException instanceof UsernameNotFoundException) {
                                return Single.error(new InternalAuthenticationServiceException("Invalid or unknown user"));
                            } else {
                                logger.error("An error occurs during user authentication", lastException);
                                return Single.error(new InternalAuthenticationServiceException("Unable to validate credentials. The user account you are trying to access may be experiencing a problem.", lastException));
                            }
                        } else {
                            return Single.error(new BadCredentialsException("No user found for registered providers"));
                        }
                    } else {
                        return loadUser(user);
                    }
                })
                .map(user -> {
                    // check user account status
                    if (!user.isEnabled()) {
                        throw new AccountDisabledException("Account is disabled for user " + user.getUsername());
                    }
                    return user;
                })
                .doOnSuccess(user -> auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class).principal(authentication).domain(domain.getId()).client(client).user(user)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class).principal(authentication).domain(domain.getId()).client(client).throwable(throwable)));
    }

    @Override
    public Maybe<User> loadUserByUsername(String subject) {
        // use to find a pre-authenticated user
        // The user should be present in gravitee repository and should be retrieved from the user last identity provider
        return userService
                .findById(subject)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(subject)))
                .flatMap(user -> identityProviderManager.get(user.getSource())
                        .flatMap(authenticationProvider -> authenticationProvider.loadUserByUsername(user.getUsername()))
                        .flatMap(idpUser -> {
                            // enhance idp user with required information
                            Map<String, Object> additionalInformation = idpUser.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(idpUser.getAdditionalInformation());
                            additionalInformation.put("source", user.getSource());
                            additionalInformation.put(Parameters.CLIENT_ID, user.getClient());
                            ((DefaultUser) idpUser).setAdditionalInformation(additionalInformation);
                            return loadUser(idpUser).toMaybe();
                        })
                        .switchIfEmpty(Maybe.just(user).flatMap(user1 -> enhanceUserWithRoles(user1).toMaybe())));
    }

    @Override
    public Single<User> loadUser(io.gravitee.am.identityprovider.api.User user) {
        // use to load an authenticated user
        return enhanceUserWithGroups(user)
                .flatMap(user1 -> userService.findOrCreate(domain.getId(), user1))
                .flatMap(this::enhanceUserWithRoles);
    }

    private Maybe<UserAuthentication> authenticate0(Client client, Authentication authentication, String authProvider) {
        return identityProviderManager.get(authProvider)
                .switchIfEmpty(Maybe.error(new BadCredentialsException("Unable to load authentication provider " + authProvider + ", an error occurred during the initialization stage")))
                .flatMap(authenticationProvider -> {
                    logger.debug("Authentication attempt using identity provider {} ({})", authenticationProvider, authenticationProvider.getClass().getName());
                    return authenticationProvider.loadUserByUsername(authentication)
                            .switchIfEmpty(Maybe.error(new UsernameNotFoundException((String) authentication.getPrincipal())));
                })
                .map(user -> {
                    logger.debug("Successfully Authenticated: " + authentication.getPrincipal() + " with provider authentication provider " + authProvider);
                    Map<String, Object> additionalInformation = user.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(user.getAdditionalInformation());
                    additionalInformation.put("source", authProvider);
                    additionalInformation.put(Parameters.CLIENT_ID, client.getId());
                    ((DefaultUser ) user).setAdditionalInformation(additionalInformation);
                    return new UserAuthentication(user, null);
                })
                .onErrorResumeNext(error -> {
                    logger.debug("Unable to authenticate [{}] with authentication provider [{}]", authentication.getPrincipal(), authProvider, error);
                    return Maybe.just(new UserAuthentication(null, error));
                });
    }

    private Single<User> enhanceUserWithRoles(User user) {
        List<String> userRoles = user.getRoles();
        if (userRoles != null && !userRoles.isEmpty()) {
            return roleService.findByIdIn(userRoles)
                    .map(roles -> {
                        user.setRolesPermissions(roles);
                        return user;
                    });
        }
        return Single.just(user);
    }

    private Single<io.gravitee.am.identityprovider.api.User> enhanceUserWithGroups(io.gravitee.am.identityprovider.api.User user) {
        // retrieve groups from user identity provider role mapper
        final String source = user.getAdditionalInformation() == null ? null : (String) user.getAdditionalInformation().get(SOURCE_FIELD);
        if (source != null) {
            return identityProviderManager.getIdentityProvider(source)
                    .map(identityProvider -> {
                        Map<String, String[]> roleMapper = identityProvider.getRoleMapper();
                        if (roleMapper != null) {
                            // "groups" role mapping value starts with group=
                            // retrieve only roles/groups mapping
                            Map<String, List<String>> groupMapping = roleMapper
                                    .entrySet()
                                    .stream()
                                    .collect(
                                            Collectors.toMap(
                                                    entry -> entry.getKey(),
                                                    entry -> Arrays.asList(entry.getValue())
                                                            .stream()
                                                            .map(mapping -> mapping.split("=", 2))
                                                            .filter(mapping -> GROUP_MAPPER_PREFIX.equals(mapping[0]))
                                                            .map(mapping -> mapping[1])
                                                            .collect(Collectors.toList())
                                            ));

                            user.getAdditionalInformation().put(GROUP_MAPPING_ATTRIBUTE, groupMapping);
                        }
                        return user;
                    })
                    .defaultIfEmpty(user)
                    .toSingle();
        } else {
            return Single.just(user);
        }
    }

    private class UserAuthentication {
        private io.gravitee.am.identityprovider.api.User user;
        private Throwable lastException;

        public UserAuthentication() {
        }

        public UserAuthentication(io.gravitee.am.identityprovider.api.User user, Throwable lastException) {
            this.user = user;
            this.lastException = lastException;
        }

        public io.gravitee.am.identityprovider.api.User getUser() {
            return user;
        }

        public Throwable getLastException() {
            return lastException;
        }
    }
}
