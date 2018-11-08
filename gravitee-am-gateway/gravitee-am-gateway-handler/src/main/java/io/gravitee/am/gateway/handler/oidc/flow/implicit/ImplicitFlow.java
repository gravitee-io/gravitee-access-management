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
package io.gravitee.am.gateway.handler.oidc.flow.implicit;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.response.IDTokenResponse;
import io.gravitee.am.gateway.handler.oauth2.response.ImplicitResponse;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.flow.AbstractFlow;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.reactivex.Single;

import java.util.Arrays;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ImplicitFlow extends AbstractFlow {

    private static final List<String> RESPONSE_TYPES = Arrays.asList(ResponseType.TOKEN, io.gravitee.am.common.oidc.ResponseType.ID_TOKEN, io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN);
    private TokenService tokenService;
    private IDTokenService idTokenService;

    public ImplicitFlow(AuthorizationRequestResolver authorizationRequestResolver,
                        ApprovalService approvalService,
                        TokenService tokenService,
                        IDTokenService idTokenService) {
        super(RESPONSE_TYPES);
        setAuthorizationRequestResolver(authorizationRequestResolver);
        setApprovalService(approvalService);
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
