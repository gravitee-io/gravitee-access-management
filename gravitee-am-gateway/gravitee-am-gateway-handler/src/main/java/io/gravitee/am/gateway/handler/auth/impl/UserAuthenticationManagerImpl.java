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
package io.gravitee.am.gateway.handler.auth.impl;

import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.service.RoleService;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.gravitee.am.service.exception.authentication.InternalAuthenticationServiceException;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationManagerImpl implements UserAuthenticationManager {

    private final Logger logger = LoggerFactory.getLogger(UserAuthenticationManagerImpl.class);

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

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
                            } else {
                                logger.error("An error occurs during user authentication", lastException);
                                return Single.error(new InternalAuthenticationServiceException("Unable to validate credentials. The user account you are trying to access may be experiencing a problem.", lastException));
                            }
                        } else {
                            return Single.error(new BadCredentialsException("No user found for registered providers"));
                        }
                    } else {
                        return userService.findOrCreate(user);
                    }
                });
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
                        .map(idpUser -> {
                            // update roles
                            // TODO should we need to update others information from the idp user ?
                            user.setRoles(idpUser.getRoles());
                            return user;
                        })
                        .defaultIfEmpty(user))
                        .flatMap(user -> enhanceUserWithRoles(user));
    }

    private Maybe<UserAuthentication> authenticate0(Client client, Authentication authentication, String authProvider) {
        return identityProviderManager.get(authProvider)
                .switchIfEmpty(Maybe.error(new BadCredentialsException("Unable to load authentication provider " + authProvider + ", an error occurred during the initialization stage")))
                .flatMap(authenticationProvider -> {
                    logger.debug("Authentication attempt using identity provider {} ({})", authenticationProvider, authenticationProvider.getClass().getName());
                    return authenticationProvider.loadUserByUsername(authentication)
                            .switchIfEmpty(Maybe.error(new BadCredentialsException("Unable to authenticate user : " + authentication.getPrincipal() + " authentication provider has returned empty value")));
                })
                .map(user -> {
                    logger.debug("Successfully Authenticated: " + authentication + " with provider authentication provider " + authProvider);
                    Map<String, Object> additionalInformation = user.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(user.getAdditionalInformation());
                    additionalInformation.put("source", authProvider);
                    additionalInformation.put(OAuth2Constants.CLIENT_ID, client.getClientId());
                    ((DefaultUser ) user).setAdditonalInformation(additionalInformation);
                    return new UserAuthentication(user, null);
                })
                .onErrorResumeNext(error -> {
                    logger.debug("Unable to authenticate [{}] with authentication provider [{}]", authentication, authProvider, error);
                    return Maybe.just(new UserAuthentication(null, error));
                });
    }

    private Maybe<User> enhanceUserWithRoles(User user) {
        List<String> userRoles = user.getRoles();
        if (userRoles != null && !userRoles.isEmpty()) {
            return roleService.findByIdIn(userRoles)
                    .map(roles -> {
                        user.setRolesPermissions(roles);
                        return user;
                    }).toMaybe();
        }
        return Maybe.just(user);
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
