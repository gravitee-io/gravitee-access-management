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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.http.NoOpResponse;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.utils.RoutingContextUtils;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.resources.request.AuthorizationRequestFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.common.utils.ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class AuthorizationRequestResolveHandler implements Handler<RoutingContext> {

    private final Domain domain;
    private final ExecutionContextFactory executionContextFactory;
    private final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();
    private final AuthorizationRequestResolver authorizationRequestResolver;

    public AuthorizationRequestResolveHandler(Domain domain,
                                              ScopeManager scopeManager,
                                              ProtectedResourceManager protectedResourceManager,
                                              ExecutionContextFactory executionContextFactory) {
        this.domain = domain;
        this.executionContextFactory = executionContextFactory;
        this.authorizationRequestResolver = new AuthorizationRequestResolver(scopeManager, protectedResourceManager);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // create authorization request
        final AuthorizationRequest authorizationRequest = resolveInitialAuthorizeRequest(routingContext);

        // compute authorization request
        computeAuthorizationRequest(authorizationRequest, routingContext, h -> {
            if (h.failed()) {
                routingContext.fail(h.cause());
                return;
            }
            // prepare context for the next handlers
            routingContext.put(AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
            // continue
            routingContext.next();
        });
    }

    private void computeAuthorizationRequest(AuthorizationRequest authorizationRequest,
                                             RoutingContext routingContext,
                                             Handler<AsyncResult> handler) {
        // get client
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);

        // get user
        final io.gravitee.am.model.User endUser = routingContext.user() != null ?
                ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser() : null;

        authorizationRequestResolver.resolve(authorizationRequest, client, endUser)
                .flatMap(req -> {
                    if(domain.isRedirectUriExpressionLanguageEnabled()){
                        ExecutionContext executionContext = prepareExecutionContext(routingContext);
                        return authorizationRequestResolver.evaluateELQueryParams(req, client, executionContext);
                    } else {
                        return Single.just(req);
                    }
                })
                .subscribe(
                        __ -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private AuthorizationRequest resolveInitialAuthorizeRequest(RoutingContext routingContext) {
        AuthorizationRequest authorizationRequest = routingContext.get(AUTHORIZATION_REQUEST_CONTEXT_KEY);
        // we have the authorization request in session if we come from the approval user page
        if (authorizationRequest != null) {
            return authorizationRequest;
        }
        // if none, we have the required request parameters to re-create the authorize request
        return authorizationRequestFactory.create(routingContext);
    }

    private ExecutionContext prepareExecutionContext(RoutingContext routingContext) {
        try {
            io.vertx.core.http.HttpServerRequest request = routingContext.request().getDelegate();
            Request serverRequest = new VertxHttpServerRequest(request);
            ExecutionContext simpleExecutionContext = new SimpleExecutionContext(serverRequest, new NoOpResponse());
            ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);
            executionContext.getAttributes().putAll(RoutingContextUtils.getEvaluableAttributes(routingContext));

            return executionContext;
        } catch (Exception ex) {
            log.error("Execution context creation failed", ex);
            return new SimpleExecutionContext(null, null);
        }
    }
}
