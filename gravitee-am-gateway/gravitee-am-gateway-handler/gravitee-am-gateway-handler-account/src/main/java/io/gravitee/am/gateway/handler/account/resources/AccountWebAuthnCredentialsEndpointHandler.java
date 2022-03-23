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
package io.gravitee.am.gateway.handler.account.resources;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_REDIRECT_URI;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountWebAuthnCredentialsEndpointHandler {

    private AccountService accountService;
    private JWTBuilder jwtBuilder;

    public AccountWebAuthnCredentialsEndpointHandler(AccountService accountService, JWTBuilder jwtBuilder) {
        this.accountService = accountService;
        this.jwtBuilder = jwtBuilder;
    }

    /**
     * Get enrolled WebAuthn credentials for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void listEnrolledWebAuthnCredentials(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        accountService.getWebAuthnCredentials(user)
                        .subscribe(
                                enrolledCredentials -> AccountResponseHandler.handleDefaultResponse(routingContext, enrolledCredentials),
                                error -> routingContext.fail(error)
                        );
    }

    /**
     * Get enrolled WebAuthn credential detail for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void getEnrolledWebAuthnCredential(RoutingContext routingContext) {
        final String credentialId = routingContext.request().getParam("credentialId");

        accountService.getWebAuthnCredential(credentialId)
                .subscribe(
                        credential -> AccountResponseHandler.handleDefaultResponse(routingContext, credential),
                        error -> routingContext.fail(error)
                );
    }

    /**
     * Delete enrolled WebAuthn credential detail for the current user
     * @param routingContext the routingContext holding the current user
     */
    public void deleteWebAuthnCredential(RoutingContext routingContext) {
        final String id = routingContext.request().getParam("credentialId");

        accountService.removeWebAuthnCredential(id)
                .subscribe(
                        () -> AccountResponseHandler.handleNoBodyResponse(routingContext),
                        error -> routingContext.fail(error)
                );
    }

    /**
     * Create a JWT token with the redirect uri found in the request
     * @param routingContext he routingContext holding the current user
     */
    public void createToken(RoutingContext routingContext) {
        final String token = "token";
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final JsonObject requestBody = routingContext.getBodyAsJson();

        final long iatValue = System.currentTimeMillis() / 1000;
        final long expValue = iatValue + TimeUnit.MINUTES.toMillis(2) / 1000;

        final Map<String, Object> claims = Map.of(
                Claims.sub, user.getId(),
                Claims.aud, client.getId(),
                Claims.iat, iatValue,
                Claims.exp, expValue,
                WEBAUTHN_REDIRECT_URI, requestBody.getString(WEBAUTHN_REDIRECT_URI)
        );

        AccountResponseHandler.handleDefaultResponse(routingContext, Map.of(token, jwtBuilder.sign(new JWT(claims))));
    }
}
