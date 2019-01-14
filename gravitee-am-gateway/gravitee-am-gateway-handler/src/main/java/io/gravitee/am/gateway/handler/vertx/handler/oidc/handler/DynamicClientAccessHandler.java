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
package io.gravitee.am.gateway.handler.vertx.handler.oidc.handler;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.exception.JwtException;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.gateway.handler.oauth2.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oidc.exception.ClientRegistrationForbiddenException;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants.CLIENT_ID;

/**
 * This endpoint aim to access to client-id generated through the dynamic client registration protocol.
 * See <a href="https://openid.net/specs/openid-connect-registration-1_0.html">Openid Connect Dynamic Client Registration</a>
 * See <a href="https://tools.ietf.org/html/rfc7591"> OAuth 2.0 Dynamic Client Registration Protocol</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DynamicClientAccessHandler extends AbstractProtectedHandler{

    private ClientSyncService clientSyncService;
    private JwtService jwtService;
    private Domain domain;

    public DynamicClientAccessHandler(ClientSyncService clientSyncService, JwtService jwtService, Domain domain) {
        this.clientSyncService = clientSyncService;
        this.jwtService = jwtService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        //1st check if dynamic client registration is enabled.
        if(!domain.isDynamicClientRegistrationEnabled()) {
            context.fail(new ClientRegistrationForbiddenException());
            return;
        }

        //Else check if client is authenticated and if client_id in path parameter is matching with the token.
        this.extractAccessTokenFromRequest(context)
                .flatMap(extractedBearer -> this.validateAccessToken(extractedBearer))
                .flatMap(token -> this.isRequestPathClientIdMatching(token,context.request().getParam(CLIENT_ID)))
                .subscribe(
                        token -> {
                            context.put(AccessToken.ACCESS_TOKEN, token);
                            context.put("domain",domain.getId());
                            context.next();
                        },
                        error -> context.fail(error)
                );
    }


    /**
     * @param accessToken RoutingContext
     * @return AccessToken
     */
    private Maybe<Token> validateAccessToken(String accessToken) {
        return jwtService.decode(accessToken)
                .flatMapMaybe(jwt -> clientSyncService.findByClientId(jwt.getAud()))
                .switchIfEmpty(Maybe.error(new InvalidTokenException("Invalid or unknown client for this token")))
                .flatMap(client ->
                        this.decodeAndVerify(accessToken, client)
                                .flatMap(token -> {
                                    if(!token.getSubject().equals(client.getClientId())) {
                                        //Token for application must contain clientId as subject
                                        return Maybe.error(new InvalidTokenException("The access token was not issued for a Client"));
                                    }
                                    if(token.getExpireAt().before(new Date())) {
                                        return Maybe.error(new InvalidTokenException("The access token expired"));
                                    }

                                    boolean isAdmin = token.getScope() != null && Arrays.asList(token.getScope().split("\\s+")).contains(Scope.DCR_ADMIN.getKey());
                                    boolean isAllowed = token.getScope() != null && Arrays.asList(token.getScope().split("\\s+")).contains(Scope.DCR.getKey());

                                    if(!isAdmin && !isAllowed) {
                                        return Maybe.error(new ClientRegistrationForbiddenException());
                                    }
                                    if(!isAdmin && !accessToken.equals(client.getRegistrationAccessToken())) {
                                        return Maybe.error(new ClientRegistrationForbiddenException("Non matching registration_access_token"));
                                    }

                                    return Maybe.just(token);
                                })
                );
    }

    //Not using TokenService because we do not need to retrieve token from repository.
    private Maybe<Token> decodeAndVerify(String accessToken, Client client) {
        return jwtService.decodeAndVerify(accessToken, client)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof JwtException) {
                        return Single.error(new InvalidTokenException(ex.getMessage(), ex));
                    }
                    return Single.error(ex);
                })
                .flatMapMaybe(jwt -> {
                    AccessToken token = new AccessToken(accessToken);
                    token.setClientId(jwt.getAud());
                    token.setSubject(jwt.getSub());
                    token.setScope(jwt.getScope());
                    token.setCreatedAt(new Date(jwt.getIat() * 1000l));
                    token.setExpireAt(new Date(jwt.getExp() * 1000l));
                    token.setExpiresIn(token.getExpireAt() != null ? Long.valueOf((token.getExpireAt().getTime() - System.currentTimeMillis()) / 1000L).intValue() : 0);

                    // set add additional information (currently only claims parameter)
                    if (jwt.getClaimsRequestParameter() != null) {
                        token.setAdditionalInformation(Collections.singletonMap(Claims.claims, jwt.getClaimsRequestParameter()));
                    }
                    return Maybe.just(token);
                });
    }

    private Maybe<Token> isRequestPathClientIdMatching(Token accessToken, String clientIdPathParameter) {
        if(!accessToken.getSubject().equals(clientIdPathParameter)) {
            return Maybe.error(new ClientRegistrationForbiddenException("Not allowed to access to : "+clientIdPathParameter));
        }
        return Maybe.just(accessToken);
    }
}
