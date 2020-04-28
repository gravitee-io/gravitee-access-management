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
package io.gravitee.am.gateway.handler.oidc.service.flow.hybrid;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.service.response.HybridResponse;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.service.flow.AbstractFlow;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;

import java.util.Arrays;
import java.util.List;

/**
 * When using the Hybrid Flow, some tokens are returned from the Authorization Endpoint and others are returned from the Token Endpoint.
 *
 * The mechanisms for returning tokens in the Hybrid Flow are specified in OAuth 2.0 Multiple Response Type Encoding Practices [OAuth.Responses].
 *
 * The Hybrid Flow follows the following steps:
 *
 *  . Client prepares an Authentication Request containing the desired request parameters.
 *  . Client sends the request to the Authorization Server.
 *  . Authorization Server Authenticates the End-User.
 *  . Authorization Server obtains End-User Consent/Authorization.
 *  . Authorization Server sends the End-User back to the Client with an Authorization Code and, depending on the Response Type, one or more additional parameters.
 *  . Client requests a response using the Authorization Code at the Token Endpoint.
 *  . Client receives a response that contains an ID Token and Access Token in the response body.
 *  . Client validates the ID Token and retrieves the End-User's Subject Identifier.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#HybridFlowAuth">3.3. Authentication using the Hybrid Flow</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HybridFlow extends AbstractFlow {

    private final static List<String> RESPONSE_TYPES = Arrays.asList(ResponseType.CODE_TOKEN, ResponseType.CODE_ID_TOKEN, ResponseType.CODE_ID_TOKEN_TOKEN);
    private AuthorizationCodeService authorizationCodeService;
    private TokenService tokenService;
    private IDTokenService idTokenService;

    public HybridFlow(AuthorizationCodeService authorizationCodeService,
                      TokenService tokenService,
                      IDTokenService idTokenService) {
        super(RESPONSE_TYPES);
        this.authorizationCodeService = authorizationCodeService;
        this.tokenService = tokenService;
        this.idTokenService = idTokenService;
    }

    @Override
    protected Single<AuthorizationResponse> prepareResponse(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        // Authorization Code is always returned when using the Hybrid Flow.
        return authorizationCodeService.create(authorizationRequest, endUser)
                .flatMap(code -> {
                    // prepare response
                    HybridResponse hybridResponse = new HybridResponse();
                    hybridResponse.setRedirectUri(authorizationRequest.getRedirectUri());
                    hybridResponse.setState(authorizationRequest.getState());
                    hybridResponse.setCode(code.getCode());
                    OAuth2Request oAuth2Request = authorizationRequest.createOAuth2Request();
                    oAuth2Request.setGrantType(GrantType.HYBRID);
                    oAuth2Request.setSubject(endUser.getId());
                    oAuth2Request.getContext().put(Claims.c_hash, code.getCode());
                    switch (authorizationRequest.getResponseType()) {
                        // code id_token response type MUST include both an Authorization Code and an id_token
                        case ResponseType.CODE_ID_TOKEN:
                            return idTokenService.create(oAuth2Request, client, endUser)
                                    .map(idToken -> {
                                        hybridResponse.setIdToken(idToken);
                                        return hybridResponse;
                                    });
                        // others Hybrid Flow response type MUST include at least an Access Token, an Access Token Type and optionally an ID Token
                        default:
                            return tokenService.create(oAuth2Request, client, endUser)
                                    .map(accessToken -> {
                                        hybridResponse.setAccessToken(accessToken);
                                        return hybridResponse;
                                    });
                    }
                });
    }
}
