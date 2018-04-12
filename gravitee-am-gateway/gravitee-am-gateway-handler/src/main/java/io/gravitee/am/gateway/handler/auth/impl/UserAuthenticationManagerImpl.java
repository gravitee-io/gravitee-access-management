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
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.User;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationManagerImpl implements UserAuthenticationManager {

    private final Logger logger = LoggerFactory.getLogger(UserAuthenticationManagerImpl.class);

    @Autowired
    private ClientService clientService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Override
    public Single<User> authenticate(String clientId, Authentication authentication) {
        logger.debug("Trying to authenticate [{}]", authentication);

        // TODO: look for a way to send a BadCredentialsException instead of a NoSuchElementException
        // lastOrError() always throw a NoSuchElementException without a way to switch for an other exception type
        // Get identity providers associated to a client
        // For each idp, try to authenticate a user
        // Try to authenticate while the user can not be authenticated
        // If user can't be authenticated, send an exception
        return clientService.findByClientId(clientId)
                .flatMapObservable(client -> Observable.fromIterable(client.getIdentities()))
                .flatMapMaybe(authProvider -> identityProviderManager.get(authProvider))
                .flatMapMaybe(authenticationProvider -> authenticationProvider.loadUserByUsername(authentication))
                .lastOrError()
                .flatMap(user -> {
                    if (user == null) {
                        return Single.error(new BadCredentialsException());
                    }
                    return Single.just(user);
                });

    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    public void setIdentityProviderManager(IdentityProviderManager identityProviderManager) {
        this.identityProviderManager = identityProviderManager;
    }
}
