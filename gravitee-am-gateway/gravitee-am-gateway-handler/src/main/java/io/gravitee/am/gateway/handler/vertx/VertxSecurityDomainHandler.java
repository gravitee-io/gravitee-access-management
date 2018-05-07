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

import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.vertx.auth.handler.FormLoginHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.OAuth2ClientAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.provider.OAuth2ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.auth.provider.UserAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.login.LoginCallbackEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.login.LoginEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.OAuth2Router;
import io.gravitee.am.gateway.handler.vertx.oidc.OIDCRouter;
import io.gravitee.am.model.Domain;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import io.vertx.reactivex.ext.web.templ.ThymeleafTemplateEngine;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxSecurityDomainHandler {

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private ClientService clientService;

    @Autowired
    private ThymeleafTemplateEngine thymeleafTemplateEngine;

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private OIDCRouter oidcRouter;

    @Autowired
    private OAuth2Router oauth2Router;

    // TODO both auth handlers and session are created here and inside oauth2 router
    public Router create() {
        // Create the security domain router
        final Router router = Router.router(vertx);

        // create web handlers
        StaticHandler staticHandler = StaticHandler.create();
        router.route()
                .handler(BodyHandler.create())
                .handler(staticHandler);
        router.route("/oauth/*")
                .handler(staticHandler);

        // create authentication handlers
        final AuthProvider userAuthProvider = new AuthProvider(new UserAuthenticationProvider(userAuthenticationManager));
        final AuthProvider identityProviderAuthProvider = new AuthProvider(new OAuth2ClientAuthenticationProvider(identityProviderManager));

        // set session handler for login and login call back
        CookieHandler cookieHandler = CookieHandler.create();
        SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
        UserSessionHandler userSessionHandler = UserSessionHandler.create(userAuthProvider);

        // Login endpoints
        router.route("/login")
                .handler(cookieHandler)
                .handler(sessionHandler)
                .handler(userSessionHandler);
        router
                .route("/login/callback")
                .handler(cookieHandler)
                .handler(sessionHandler)
                .handler(userSessionHandler);

        router.get("/login").handler(new LoginEndpointHandler(thymeleafTemplateEngine, domain, clientService, identityProviderManager));
        router.post("/login").handler(FormLoginHandler.create(userAuthProvider.getDelegate()));

        router.get("/login/callback")
                .handler(OAuth2ClientAuthHandler.create(identityProviderAuthProvider.getDelegate(), identityProviderManager))
                .handler(new LoginCallbackEndpointHandler());


        oidcRouter.route(router);
        oauth2Router.route(router);

        // bind failure handler
        router.route().failureHandler(new ExceptionHandler());

        return router;
    }

    public String contextPath() {
        return '/' + domain.getPath();
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }
}
