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
package io.gravitee.am.gateway.handler.oidc.service.flow.implicit;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.service.response.IDTokenResponse;
import io.gravitee.am.gateway.handler.oauth2.service.response.ImplicitResponse;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.service.flow.AbstractFlow;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;

import java.util.Arrays;
import java.util.List;

/**
 * When using the Implicit Flow, all tokens are returned from the Authorization Endpoint; the Token Endpoint is not used.
 *
 * The Implicit Flow is mainly used by Clients implemented in a browser using a scripting language.
 *
 * The Access Token and ID Token are returned directly to the Client, which may expose them to the End-User and applications that have access to the End-User's User Agent.
 *
 * The Authorization Server does not perform Client Authentication.
 *
 * The Implicit Flow follows the following steps:
 *
 *  . Client prepares an Authentication Request containing the desired request parameters.
 *  . Client sends the request to the Authorization Server.
 *  . Authorization Server Authenticates the End-User.
 *  . Authorization Server obtains End-User Consent/Authorization.
 *  . Authorization Server sends the End-User back to the Client with an ID Token and, if requested, an Access Token.
 *  . Client validates the ID token and retrieves the End-User's Subject Identifier.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#ImplicitFlowAuth">3.2. Authentication using the Implicit Flow</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ImplicitFlow extends AbstractFlow {

    private static final List<String> RESPONSE_TYPES = Arrays.asList(ResponseType.TOKEN, io.gravitee.am.common.oidc.ResponseType.ID_TOKEN, io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN);
    private TokenService tokenService;
    private IDTokenService idTokenService;

    public ImplicitFlow(TokenService tokenService,
                        IDTokenService idTokenService) {
        super(RESPONSE_TYPES);
        this.tokenService = tokenService;
        this.idTokenService = idTokenService;
    }

    @Override
    protected Single<AuthorizationResponse> prepareResponse(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        OAuth2Request oAuth2Request = authorizationRequest.createOAuth2Request();
        oAuth2Request.setGrantType(GrantType.IMPLICIT);
        oAuth2Request.setSupportRefreshToken(false);
        oAuth2Request.setSubject(endUser.getId());
        if (io.gravitee.am.common.oidc.ResponseType.ID_TOKEN.equals(authorizationRequest.getResponseType())) {
            return idTokenService.create(oAuth2Request, client, endUser)
                    .map(idToken -> {
                        IDTokenResponse response = new IDTokenResponse();
                        response.setRedirectUri(authorizationRequest.getRedirectUri());
                        response.setIdToken(idToken);
                        response.setState(authorizationRequest.getState());
                        return response;
                    });
        } else {
            return tokenService.create(oAuth2Request, client, endUser)
                    .map(accessToken -> {
                        ImplicitResponse response = new ImplicitResponse();
                        response.setRedirectUri(authorizationRequest.getRedirectUri());
                        response.setAccessToken(accessToken);
                        response.setState(authorizationRequest.getState());
                        return response;
                    });
        }
    }
}
