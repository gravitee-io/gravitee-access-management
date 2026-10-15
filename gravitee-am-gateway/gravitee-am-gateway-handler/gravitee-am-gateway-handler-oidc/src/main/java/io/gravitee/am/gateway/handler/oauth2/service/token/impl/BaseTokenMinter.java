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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2RequestParams;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenMinter;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

abstract class BaseTokenMinter implements TokenMinter {

    protected record MintedToken(Token token, AuditBuilder<?> successAudit) { }

    @Autowired
    ExecutionContextFactory executionContextFactory;

    @Autowired
    AuditService auditService;

    protected abstract Single<MintedToken> mint(OAuth2Request oAuth2Request,
                                                Client client,
                                                User endUser,
                                                ExecutionContext executionContext);

    @Override
    public Single<Token> mint(OAuth2Request oAuth2Request, Client client, User endUser) {
        return Single.fromCallable(() -> createExecutionContext(oAuth2Request, client, endUser))
                .concatMap(ctx -> mint(oAuth2Request, client, endUser, ctx))
                .doOnSuccess(minted -> auditService.report(minted.successAudit()))
                .map(MintedToken::token)
                .doOnError(error -> reportError(error, oAuth2Request, client, endUser));
    }

    protected void reportError(Throwable error, OAuth2Request oAuth2Request, Client client, User endUser){
        auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .tokenActor(client)
                .withParams(() -> OAuth2RequestParams.of(oAuth2Request))
                .tokenTarget(endUser)
                .throwable(error));
    }

    private ExecutionContext createExecutionContext(OAuth2Request request, Client client, User user) {
        ExecutionContext simpleExecutionContext = new SimpleExecutionContext(request, null);
        ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);

        // put authorization request in context
        // AM-3137 => clean OAuth 2.0 request
        OAuth2Request safeRequest = new OAuth2Request(request);
        safeRequest.setExecutionContext(null);
        safeRequest.setHttpResponse(null);
        if (request.getResponseType() != null && !request.getResponseType().isEmpty()) {
            executionContext.setAttribute("authorizationRequest", safeRequest);
        } else {
            executionContext.setAttribute("tokenRequest", safeRequest);
        }
        // put auth flow policy context attributes in context
        Object authFlowAttributes = request.getContext().get(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY);
        if (authFlowAttributes != null) {
            executionContext.setAttribute(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY, authFlowAttributes);
            request.getContext().remove(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY);
        }
        // put oauth2 request execution context attributes in context
        executionContext.getAttributes().putAll(request.getExecutionContext());
        executionContext.getAttributes().putAll(request.collectAdditionalContextAttributes());
        executionContext.setAttribute(ConstantKeys.CLIENT_CONTEXT_KEY, new ClientProperties(client));
        if (user != null) {
            executionContext.setAttribute(ConstantKeys.USER_CONTEXT_KEY, new UserProperties(user, true));
        }

        return executionContext;
    }
}
