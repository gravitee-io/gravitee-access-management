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
package io.gravitee.am.gateway.handler.vertx.auth.provider;

import io.gravitee.am.gateway.handler.auth.EndUserAuthentication;
import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2ClientAuthenticationProvider implements AuthProvider {

    private final Logger logger = LoggerFactory.getLogger(OAuth2ClientAuthenticationProvider.class);
    private final static String USERNAME_PARAMETER = "username";
    private final static String PASSWORD_PARAMETER = "password";
    private static final String PROVIDER_PARAMETER = "provider";
    private IdentityProviderManager identityProviderManager;
    private UserService userService;

    public OAuth2ClientAuthenticationProvider() {
    }

    public OAuth2ClientAuthenticationProvider(IdentityProviderManager identityProviderManager, UserService userService) {
        this.identityProviderManager = identityProviderManager;
        this.userService = userService;
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        final String authProvider = authInfo.getString(PROVIDER_PARAMETER);
        final String username = authInfo.getString(USERNAME_PARAMETER);
        final String password = authInfo.getString(PASSWORD_PARAMETER);

        logger.debug("Authentication attempt using identity provider {}", authProvider);

        identityProviderManager
                .get(authInfo.getString(PROVIDER_PARAMETER))
                .flatMap(authenticationProvider -> {
                    EndUserAuthentication endUserAuthentication = new EndUserAuthentication(username, password);
                    endUserAuthentication.setAdditionalInformation(Collections.singletonMap(OAuth2Constants.REDIRECT_URI, authInfo.getString(OAuth2Constants.REDIRECT_URI)));
                    return authenticationProvider.loadUserByUsername(endUserAuthentication)
                            .switchIfEmpty(Maybe.error(new BadCredentialsException("Unable to authenticate oauth2 provider, authentication provider has returned empty value")));
                })
                .flatMapSingle(user -> {
                    // set source and client for the current authenticated end-user
                    Map<String, Object> additionalInformation = user.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(user.getAdditionalInformation());
                    additionalInformation.put("source", authInfo.getString(PROVIDER_PARAMETER));
                    additionalInformation.put(OAuth2Constants.CLIENT_ID, authInfo.getString(OAuth2Constants.CLIENT_ID));
                    ((DefaultUser) user).setAdditonalInformation(additionalInformation);
                    return userService.findOrCreate(user);
                })
                .subscribe(user -> resultHandler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.vertx.auth.user.User(user))), error -> {
                    logger.error("Unable to authenticate oauth2 provider", error);
                    resultHandler.handle(Future.failedFuture(error));
                });

    }
}
