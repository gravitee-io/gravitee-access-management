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

import io.gravitee.am.gateway.handler.oauth2.resources.request.AuthorizationRequestFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestResolveHandler implements Handler<RoutingContext> {

    private final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();
    private final AuthorizationRequestResolver authorizationRequestResolver = new AuthorizationRequestResolver();
    private static final String CLIENT_CONTEXT_KEY = "client";

    @Override
    public void handle(RoutingContext routingContext) {
        // get client
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);

        // get user
        final io.gravitee.am.model.User endUser = routingContext.user() != null ?
                ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser() : null;

        // create authorization request
        final AuthorizationRequest authorizationRequest = resolveInitialAuthorizeRequest(routingContext);

        // compute authorization request
        computeAuthorizationRequest(authorizationRequest, client, endUser, h -> {
            if (h.failed()) {
                routingContext.fail(h.cause());
                return;
            }
            // prepare context for the next handlers
            routingContext.session().put(OAuth2Constants.AUTHORIZATION_REQUEST, authorizationRequest);
            // continue
            routingContext.next();
        });
    }

    private void computeAuthorizationRequest(AuthorizationRequest authorizationRequest, Client client, User endUser, Handler<AsyncResult> handler) {
        authorizationRequestResolver.resolve(authorizationRequest, client, endUser)
                .subscribe(
                        __ -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private AuthorizationRequest resolveInitialAuthorizeRequest(RoutingContext routingContext) {
        AuthorizationRequest authorizationRequest = routingContext.session().get(OAuth2Constants.AUTHORIZATION_REQUEST);
        // we have the authorization request in session if we come from the approval user page
        if (authorizationRequest != null) {
            return authorizationRequest;
        }
        // if none, we have the required request parameters to re-create the authorize request
        return authorizationRequestFactory.create(routingContext.request());
    }
}
