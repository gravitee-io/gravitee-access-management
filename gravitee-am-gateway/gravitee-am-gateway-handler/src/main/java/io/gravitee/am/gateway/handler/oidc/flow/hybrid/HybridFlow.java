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
package io.gravitee.am.gateway.handler.oidc.flow.hybrid;

import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.response.HybridResponse;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.flow.AbstractFlow;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.utils.OIDCClaims;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.reactivex.Single;

import java.util.Arrays;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HybridFlow extends AbstractFlow {

    private final static List<String> RESPONSE_TYPES = Arrays.asList(ResponseType.CODE_TOKEN, ResponseType.CODE_ID_TOKEN, ResponseType.CODE_ID_TOKEN_TOKEN);
    private AuthorizationCodeService authorizationCodeService;
    private TokenService tokenService;
    private IDTokenService idTokenService;

    public HybridFlow(AuthorizationRequestResolver authorizationRequestResolver,
                      ClientService clientService,
                      ApprovalService approvalService,
                      AuthorizationCodeService authorizationCodeService,
                      TokenService tokenService,
                      IDTokenService idTokenService) {
        super(RESPONSE_TYPES);
        setAuthorizationRequestResolver(authorizationRequestResolver);
        setClientService(clientService);
        setApprovalService(approvalService);
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
                    oAuth2Request.setSubject(endUser.getId());
                    oAuth2Request.getContext().put(OIDCClaims.c_hash, code.getCode());
                    switch (authorizationRequest.getResponseType()) {
                        // code id_token response type MUST include both an Authorization Code and an id_token
                        case io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN:
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
