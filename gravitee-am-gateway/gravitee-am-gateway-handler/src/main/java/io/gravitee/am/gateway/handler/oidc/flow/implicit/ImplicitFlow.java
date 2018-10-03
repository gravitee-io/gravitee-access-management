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

import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.response.ImplicitResponse;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.flow.AbstractFlow;
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

    public ImplicitFlow(AuthorizationRequestResolver authorizationRequestResolver, ClientService clientService, ApprovalService approvalService, TokenService tokenService) {
        super(RESPONSE_TYPES);
        setAuthorizationRequestResolver(authorizationRequestResolver);
        setClientService(clientService);
        setApprovalService(approvalService);
        this.tokenService = tokenService;
    }

    @Override
    protected Single<AuthorizationResponse> prepareResponse(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        return tokenService.create(authorizationRequest.createOAuth2Request(), client, endUser)
                .map(accessToken -> {
                    ImplicitResponse response = new ImplicitResponse();
                    response.setRedirectUri(authorizationRequest.getRedirectUri());
                    response.setAccessToken(accessToken);
                    response.setState(authorizationRequest.getState());
                    return response;
                });
    }
}
