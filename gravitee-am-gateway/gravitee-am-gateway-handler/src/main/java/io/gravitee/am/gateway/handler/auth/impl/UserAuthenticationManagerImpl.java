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
import io.gravitee.am.gateway.handler.auth.exception.BadCredentialsException;
import io.gravitee.am.gateway.handler.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationManagerImpl implements UserAuthenticationManager {

    private final Logger logger = LoggerFactory.getLogger(UserAuthenticationManagerImpl.class);

    @Autowired
    private ClientService clientService;

    @Autowired
    private UserService userService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Override
    public Single<User> authenticate(String clientId, Authentication authentication) {
        logger.debug("Trying to authenticate [{}]", authentication);

        // Get identity providers associated to a client
        // For each idp, try to authenticate a user
        // Try to authenticate while the user can not be authenticated
        // If user can't be authenticated, send an exception
        return clientService.findByClientId(clientId)
                .switchIfEmpty(Maybe.error(new BadCredentialsException("No client found for authentication " + authentication.getPrincipal())))
                .flatMapObservable(client -> {
                    if (client.getIdentities() == null || client.getIdentities().isEmpty()) {
                        return Observable.error(new BadCredentialsException("No identity provider found for client : " + clientId));
                    } else {
                        return Observable.fromIterable(client.getIdentities());
                    }
                })
                .flatMapMaybe(authProvider -> identityProviderManager.get(authProvider)
                        .switchIfEmpty(Maybe.error(new BadCredentialsException("Unable to load authentication provider " + authProvider + ", an error occurred during the initialization stage")))
                        .flatMap(authenticationProvider -> authenticationProvider.loadUserByUsername(authentication))
                        .switchIfEmpty(Maybe.error(new BadCredentialsException("Unable to authenticate user : " + authentication.getPrincipal())))
                        .map(user -> {
                            Map<String, Object> additionalInformation =
                                    user.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(user.getAdditionalInformation());
                            additionalInformation.put("source", authProvider);
                            additionalInformation.put(OAuth2Constants.CLIENT_ID, clientId);
                            ((DefaultUser ) user).setAdditonalInformation(additionalInformation);
                            return new UserAuthentication(user, null);
                        })
                        .onErrorResumeNext(error -> {
                            return Maybe.just(new UserAuthentication(null, error));
                        }))
                .takeUntil(userAuthentication -> userAuthentication.getUser() != null)
                .lastOrError()
                .flatMap(userAuthentication -> {
                    io.gravitee.am.identityprovider.api.User user = userAuthentication.getUser();
                    if (user == null) {
                        Throwable lastException = userAuthentication.getLastException();
                        if (lastException != null) {
                            return Single.error(lastException);
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
                        .defaultIfEmpty(user));
    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    public void setIdentityProviderManager(IdentityProviderManager identityProviderManager) {
        this.identityProviderManager = identityProviderManager;
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
