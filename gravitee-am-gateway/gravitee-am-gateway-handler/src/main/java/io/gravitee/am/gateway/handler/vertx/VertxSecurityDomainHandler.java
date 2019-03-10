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

import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.vertx.auth.provider.UserAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.OAuth2Router;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization.AuthorizationEndpointFailureHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.OIDCRouter;
import io.gravitee.am.gateway.handler.vertx.handler.root.RootRouter;
import io.gravitee.am.gateway.handler.vertx.handler.scim.SCIMRouter;
import io.gravitee.am.gateway.handler.vertx.handler.scim.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.vertx.handler.session.RxSessionHandler;
import io.gravitee.am.model.Domain;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxSecurityDomainHandler {

    private static final String DEFAULT_SESSION_COOKIE_NAME = "GRAVITEE_IO_AM_SESSION";
    private static final long DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000; // 30 minutes

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private RootRouter rootRouter;

    @Autowired
    private OIDCRouter oidcRouter;

    @Autowired
    private OAuth2Router oauth2Router;

    @Autowired
    private SCIMRouter scimRouter;

    @Autowired
    private Environment environment;

    public Router create() {
        // Create the security domain router
        final Router router = Router.router(vertx);

        // failure handler
        failureHandler(router);

        // user authentication handler
        final AuthProvider userAuthProvider = new AuthProvider(new UserAuthenticationProvider(userAuthenticationManager, clientSyncService));

        // body handler
        router.route().handler(BodyHandler.create());

        // static handler
        staticHandler(router);

        // session cookie handler
        sessionAndCookieHandler(router, userAuthProvider);

        // CSRF handler
        csrfHandler(router);

        // mount root router
        router.mountSubRouter("/", rootRouter.route(userAuthProvider));

        // mount OAuth 2.0 router
        router.mountSubRouter("/oauth", oauth2Router.route(userAuthProvider));

        // mount OpenID Connect router
        router.mountSubRouter("/oidc", oidcRouter.route());

        // mount SCIM router
        if (isSCIMEnabled()) {
            router.mountSubRouter("/scim", scimRouter.route());
        }

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

    private void failureHandler(Router router) {
        Handler<RoutingContext> authorizationEndpointFailureHandler = new AuthorizationEndpointFailureHandler(domain);
        Handler<RoutingContext> rootErrorFailureHandler = new io.gravitee.am.gateway.handler.vertx.handler.root.handler.ErrorHandler("/" + domain.getPath() + "/error");
        router.route("/login").failureHandler(authorizationEndpointFailureHandler);
        router.route("/forgotPassword").failureHandler(rootErrorFailureHandler);
        router.route("/oauth/authorize").failureHandler(authorizationEndpointFailureHandler);
        router.routeWithRegex("/scim/(.*)").failureHandler(new ErrorHandler());
        router.route().failureHandler(new ExceptionHandler());
    }

    private void staticHandler(Router router) {
        StaticHandler staticHandler = StaticHandler.create();
        router.route().handler(staticHandler);
        router.route("/oauth/*").handler(staticHandler);
    }

    private void sessionAndCookieHandler(Router router, AuthProvider userAuthProvider) {
        // cookie handler
        CookieHandler cookieHandler = io.gravitee.am.gateway.handler.vertx.handler.cookie.CookieHandler.create();
        // session handler
        SessionHandler sessionHandler = RxSessionHandler
                .create(LocalSessionStore.create(vertx))
                .setCookieHttpOnlyFlag(true)
                .setSessionCookieName(environment.getProperty("http.cookie.session.name", String.class, DEFAULT_SESSION_COOKIE_NAME))
                .setSessionTimeout(environment.getProperty("http.cookie.session.timeout", Long.class, DEFAULT_SESSION_TIMEOUT))
                .setCookieSecureFlag(environment.getProperty("http.cookie.secure", Boolean.class, false));
        // override session cookie path
        ((RxSessionHandler) sessionHandler).setSessionCookiePath("/" + domain.getPath());
        // user session handler
        UserSessionHandler userSessionHandler = UserSessionHandler.create(userAuthProvider);

        // Login endpoint
        router.route("/login")
                .handler(cookieHandler)
                .handler(sessionHandler)
                .handler(userSessionHandler);
        router
                .route("/login/callback")
                .handler(cookieHandler)
                .handler(sessionHandler)
                .handler(userSessionHandler);

        // Logout endpoint
        router
                .route("/logout")
                .handler(cookieHandler)
                .handler(sessionHandler)
                .handler(userSessionHandler);

        // OAuth 2.0 Authorize endpoint
        router
                .route("/oauth/authorize")
                .handler(cookieHandler)
                .handler(sessionHandler)
                .handler(userSessionHandler);
        router
                .route("/oauth/confirm_access")
                .handler(cookieHandler)
                .handler(sessionHandler)
                .handler(userSessionHandler);

        // Registration confirmation endpoint
        router
                .route("/confirmRegistration")
                .handler(cookieHandler)
                .handler(sessionHandler)
                .handler(userSessionHandler);

        // Reset password endpoint
        router
                .route("/resetPassword")
                .handler(cookieHandler)
                .handler(sessionHandler)
                .handler(userSessionHandler);
    }

    private void csrfHandler(Router router) {
        CSRFHandler csrfHandler = CSRFHandler.create(SecureRandomString.generate());
        // override cookie path
        csrfHandler.setCookiePath("/" + domain.getPath());
        io.gravitee.am.gateway.handler.vertx.handler.CSRFHandler csrfHandler1 = io.gravitee.am.gateway.handler.vertx.handler.CSRFHandler.create();
        router.route("/login").handler(csrfHandler).handler(csrfHandler1);
        router.route("/resetPassword").handler(csrfHandler).handler(csrfHandler1);
        router.route("/confirmRegistration").handler(csrfHandler).handler(csrfHandler1);
        router.route("/oauth/confirm_access").handler(csrfHandler).handler(csrfHandler1);
    }

    private boolean isSCIMEnabled() {
        return domain.getScim() != null && domain.getScim().isEnabled();
    }

}
