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
package io.gravitee.am.gateway.handler.oidc.flow.authorizationcode;

import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationCodeResponse;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oidc.flow.AbstractFlow;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.reactivex.Single;

import java.util.Collections;
import java.util.List;

/**
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowAuth>3.1. Authentication using the Authorization Code Flow</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationCodeFlow extends AbstractFlow {

    private final static List<String> RESPONSE_TYPES = Collections.singletonList(ResponseType.CODE);
    private AuthorizationCodeService authorizationCodeService;

    public AuthorizationCodeFlow(AuthorizationRequestResolver authorizationRequestResolver, ClientService clientService, ApprovalService approvalService, AuthorizationCodeService authorizationCodeService) {
        super(RESPONSE_TYPES);
        setAuthorizationRequestResolver(authorizationRequestResolver);
        setClientService(clientService);
        setApprovalService(approvalService);
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
