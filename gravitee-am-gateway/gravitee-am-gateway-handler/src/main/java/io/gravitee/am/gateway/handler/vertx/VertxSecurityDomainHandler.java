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

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientBasicAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientCredentialsAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.provider.ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.auth.provider.UserAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.endpoint.AuthorizeEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.endpoint.LoginEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.endpoint.TokenEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.model.Domain;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxSecurityDomainHandler {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private TokenGranter tokenGranter;

    @Autowired
    private ClientService clientService;

    public Router oauth2() {
        // Create the security domain router
        Router router = Router.router(vertx);

        // create authentication handlers
        final AuthProvider clientAuthProvider = new AuthProvider(new ClientAuthenticationProvider(clientService));
        final AuthProvider userAuthProvider = new AuthProvider(new UserAuthenticationProvider());

        final AuthHandler clientAuthHandler = ChainAuthHandler.create()
                .append(ClientCredentialsAuthHandler.create(clientAuthProvider.getDelegate()))
                .append(ClientBasicAuthHandler.create(clientAuthProvider.getDelegate()));
        final AuthHandler userAuthHandler = RedirectAuthHandler.create(userAuthProvider, contextPath() + "/login");

        // create web handlers
        setupCoreWebHandlers(router);

        // bind login endpoints
        router.get("/login").handler(new LoginEndpointHandler(domain));
        router.post("/login").handler(FormLoginHandler.create(userAuthProvider));

        // bind OAuth2 endpoints
        Handler<RoutingContext> authorizeEndpoint = new AuthorizeEndpointHandler();
        Handler<RoutingContext> tokenEndpoint = new TokenEndpointHandler();
        ((TokenEndpointHandler) tokenEndpoint).setTokenGranter(tokenGranter);

        router.route(HttpMethod.POST, "/oauth/authorize").handler(userAuthHandler).handler(authorizeEndpoint);
        router.route(HttpMethod.GET,"/oauth/authorize").handler(userAuthHandler).handler(authorizeEndpoint);
        router.route(HttpMethod.POST, "/oauth/token").handler(clientAuthHandler).handler(tokenEndpoint);

        // bind failure handler
        router.route().failureHandler(new ExceptionHandler());

        return router;
    }

    public String contextPath() {
        return "/" + domain.getPath();
    }

    private void setupCoreWebHandlers(Router router) {
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().handler(StaticHandler.create());
        //router.route().handler(UserSessionHandler.create(authProvider));
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }
}
