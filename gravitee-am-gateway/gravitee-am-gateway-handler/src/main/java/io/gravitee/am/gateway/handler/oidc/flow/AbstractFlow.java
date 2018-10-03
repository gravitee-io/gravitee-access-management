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
package io.gravitee.am.gateway.handler.oidc.flow;

import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationResponse;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractFlow implements Flow {

    private final List<String> responseTypes;
    private AuthorizationRequestResolver authorizationRequestResolver;
    private ClientService clientService;
    private ApprovalService approvalService;

    public AbstractFlow(final List<String> responseTypes) {
        Objects.requireNonNull(responseTypes);
        this.responseTypes = responseTypes;
    }

    @Override
    public boolean handle(String responseType) {
        return responseTypes.contains(responseType);
    }

    @Override
    public Single<AuthorizationResponse> run(AuthorizationRequest authorizationRequest, User endUser) {
        return resolveClient(authorizationRequest)
                .flatMap(client -> handleRequest(authorizationRequest, client, endUser));
    }

    protected abstract Single<AuthorizationResponse> prepareResponse(AuthorizationRequest authorizationRequest, Client client, User endUser);

    private Single<Client> resolveClient(AuthorizationRequest authorizationRequest) {
        final String clientId = authorizationRequest.getClientId();
        return clientService.findByClientId(clientId)
                .switchIfEmpty(Maybe.error(new InvalidRequestException("No client with id : " + clientId)))
                .toSingle();
    }

    private Single<AuthorizationResponse> handleRequest(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        return prepareRequest(authorizationRequest, client, endUser)
                .flatMap(authorizationRequest1 -> obtainEndUserConsent(authorizationRequest1, client, endUser))
                .flatMap(authorizationRequest1 -> prepareResponse(authorizationRequest1, client, endUser));
    }

    private Single<AuthorizationRequest> prepareRequest(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        return authorizationRequestResolver.resolve(authorizationRequest, client, endUser);
    }

    private Single<AuthorizationRequest> obtainEndUserConsent(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        return approvalService.checkApproval(authorizationRequest, client, endUser.getUsername());
    }

    public AuthorizationRequestResolver getAuthorizationRequestResolver() {
        return authorizationRequestResolver;
    }

    public void setAuthorizationRequestResolver(AuthorizationRequestResolver authorizationRequestResolver) {
        this.authorizationRequestResolver = authorizationRequestResolver;
    }

    public ClientService getClientService() {
        return clientService;
    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }


    public ApprovalService getApprovalService() {
        return approvalService;
    }

    public void setApprovalService(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }
}
