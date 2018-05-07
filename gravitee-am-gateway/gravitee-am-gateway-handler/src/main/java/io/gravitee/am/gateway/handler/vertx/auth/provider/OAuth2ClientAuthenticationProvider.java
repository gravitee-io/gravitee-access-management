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
import io.gravitee.am.gateway.handler.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.exception.BadClientCredentialsException;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

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

    public OAuth2ClientAuthenticationProvider() { }

    public OAuth2ClientAuthenticationProvider(IdentityProviderManager identityProviderManager) {
        this.identityProviderManager = identityProviderManager;
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        identityProviderManager
                .get(authInfo.getString(PROVIDER_PARAMETER))
                .flatMap(authenticationProvider -> {
                    String username = authInfo.getString(USERNAME_PARAMETER);
                    String password = authInfo.getString(PASSWORD_PARAMETER);
                    EndUserAuthentication endUserAuthentication = new EndUserAuthentication(username, password);
                    endUserAuthentication.setAdditionalInformation(Collections.singletonMap(OAuth2Constants.REDIRECT_URI, authInfo.getString(OAuth2Constants.REDIRECT_URI)));
                    return authenticationProvider.loadUserByUsername(endUserAuthentication);
                })
                .subscribe(user -> resultHandler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.vertx.auth.user.User(convert(user)))), error -> {
                    logger.error("Failed to authenticate oauth2 provider", error);
                    resultHandler.handle(Future.failedFuture(error));
                }, () -> resultHandler.handle(Future.failedFuture(new BadClientCredentialsException())));

    }

    private io.gravitee.am.model.User convert(io.gravitee.am.identityprovider.api.User user) {
        io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
        endUser.setUsername(user.getUsername());
        endUser.setAdditionalInformation(user.getAdditionalInformation());
        endUser.setRoles(user.getRoles());
        return endUser;
    }
}
