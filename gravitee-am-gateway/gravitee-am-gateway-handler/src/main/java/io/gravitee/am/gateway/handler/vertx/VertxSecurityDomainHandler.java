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
package io.gravitee.am.gateway.handler.vertx;

import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientBasicAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientCredentialsAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.provider.ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.endpoint.AuthorizeEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.endpoint.TokenEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxSecurityDomainHandler {

    @Autowired
    private Vertx vertx;

    public Router oauth2(Router router) {
        // Create the handlers

        final AuthProvider clientAuthProvider = new ClientAuthenticationProvider();
        final AuthHandler clientAuthHandler = ChainAuthHandler.create()
                .append(ClientCredentialsAuthHandler.create(clientAuthProvider))
                .append(ClientBasicAuthHandler.create(clientAuthProvider));

        setupCoreWebHandlers(router);

//        final AuthHandler authHandler = RedirectAuthHandler.create(authProvider, loginURL);

        // auth protected paths
//        router.route("/oauth/authorize").handler(authHandler);

        Handler<RoutingContext> authorizeEndpoint = new AuthorizeEndpointHandler();
        Handler<RoutingContext> tokenEndpoint = new TokenEndpointHandler();

        /*
        router.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable throwable) {
                System.out.println(throwable);
            }
        });
        */

        router.route().failureHandler(new ExceptionHandler());

        // Bind OAuth2 endpoints
        router.route(HttpMethod.POST, "/oauth/authorize").handler(authorizeEndpoint);
        router.route(HttpMethod.GET,"/oauth/authorize").handler(authorizeEndpoint);

        router.route(HttpMethod.POST, "/oauth/token").handler(clientAuthHandler).handler(tokenEndpoint);

//        router.route("/oauth/tokeninfo").handler(authorizer::tokenInfo);

        return router;
    }

    private void setupCoreWebHandlers(Router router) {
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        //router.route().handler(UserSessionHandler.create(authProvider));
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }
}
