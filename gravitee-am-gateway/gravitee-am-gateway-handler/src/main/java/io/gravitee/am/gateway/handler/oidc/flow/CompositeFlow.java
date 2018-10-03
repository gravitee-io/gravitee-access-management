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
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedResponseTypeException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.flow.authorizationcode.AuthorizationCodeFlow;
import io.gravitee.am.gateway.handler.oidc.flow.hybrid.HybridFlow;
import io.gravitee.am.gateway.handler.oidc.flow.implicit.ImplicitFlow;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenService;
import io.gravitee.am.model.User;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CompositeFlow implements Flow, InitializingBean  {

    private List<Flow> flows = new ArrayList<>();
    private final AuthorizationRequestResolver authorizationRequestResolver = new AuthorizationRequestResolver();

    @Autowired
    private ClientService clientService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private AuthorizationCodeService authorizationCodeService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private IDTokenService idTokenService;

    @Override
    public boolean handle(String responseType) {
        return false;
    }

    @Override
    public Single<AuthorizationResponse> run(AuthorizationRequest authorizationRequest, User endUser) {
        return Observable
                .fromIterable(flows)
                .filter(flow -> flow.handle(authorizationRequest.getResponseType()))
                .switchIfEmpty(Observable.error(new UnsupportedResponseTypeException("Unsupported response type: " + authorizationRequest.getResponseType())))
                .flatMapSingle(flow -> flow.run(authorizationRequest, endUser)).singleOrError();
    }

    @Override
    public void afterPropertiesSet() {
        addFlow(new AuthorizationCodeFlow(authorizationRequestResolver, clientService, approvalService, authorizationCodeService));
        addFlow(new ImplicitFlow(authorizationRequestResolver, clientService, approvalService, tokenService));
        addFlow(new HybridFlow(authorizationRequestResolver, clientService, approvalService, authorizationCodeService, tokenService, idTokenService));
    }

    private void addFlow(Flow flow) {
        Objects.requireNonNull(flow);
        flows.add(flow);
    }

}
