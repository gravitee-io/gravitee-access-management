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
package io.gravitee.am.gateway.handler.oidc.service.flow.authorizationcode;

import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationCodeResponse;
import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oidc.service.flow.AbstractFlow;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;

import java.util.Collections;
import java.util.List;

/**
 * When using the Authorization Code Flow, all tokens are returned from the Token Endpoint.
 *
 * The Authorization Code Flow returns an Authorization Code to the Client, which can then exchange it for an ID Token and an Access Token directly.
 *
 * This provides the benefit of not exposing any tokens to the User Agent and possibly other malicious applications with access to the User Agent.
 *
 * The Authorization Server can also authenticate the Client before exchanging the Authorization Code for an Access Token.
 *
 * The Authorization Code flow is suitable for Clients that can securely maintain a Client Secret between themselves and the Authorization Server.
 *
 * The Authorization Code Flow goes through the following steps.
 *
 *  . Client prepares an Authentication Request containing the desired request parameters.
 *  . Client sends the request to the Authorization Server.
 *  . Authorization Server Authenticates the End-User.
 *  . Authorization Server obtains End-User Consent/Authorization.
 *  . Authorization Server sends the End-User back to the Client with an Authorization Code.
 *  . Client requests a response using the Authorization Code at the Token Endpoint.
 *  . Client receives a response that contains an ID Token and Access Token in the response body.
 *  . Client validates the ID token and retrieves the End-User's Subject Identifier.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowAuth>3.1. Authentication using the Authorization Code Flow</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationCodeFlow extends AbstractFlow {

    private final static List<String> RESPONSE_TYPES = Collections.singletonList(ResponseType.CODE);
    private AuthorizationCodeService authorizationCodeService;

    public AuthorizationCodeFlow(AuthorizationCodeService authorizationCodeService) {
        super(RESPONSE_TYPES);
        this.authorizationCodeService = authorizationCodeService;
    }

    @Override
    protected Single<AuthorizationResponse> prepareResponse(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        return authorizationCodeService.create(authorizationRequest, endUser)
                .map(code -> {
                    AuthorizationCodeResponse response = new AuthorizationCodeResponse();
                    response.setRedirectUri(authorizationRequest.getRedirectUri());
                    response.setCode(code.getCode());
                    response.setState(authorizationRequest.getState());
                    return response;
                });
    }
}
