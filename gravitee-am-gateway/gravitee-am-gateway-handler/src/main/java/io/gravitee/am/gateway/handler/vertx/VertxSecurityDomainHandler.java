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
import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.openid.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientBasicAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientCredentialsAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.FormLoginHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.RedirectAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.provider.ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.auth.provider.UserAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.endpoint.*;
import io.gravitee.am.gateway.handler.vertx.endpoint.introspection.IntrospectionEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.AuthorizationRequestParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.openid.OpenIDProviderConfigurationEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.MediaType;
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

    @Autowired
    private TokenService tokenService;

    @Autowired
    private IntrospectionService introspectionService;

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private AuthorizationCodeService authorizationCodeService;

    private OpenIDDiscoveryService discoveryService;

    public Router oauth2() {
        // Create the security domain router
        Router router = Router.router(vertx);

        // create authentication handlers
        final AuthProvider clientAuthProvider = new AuthProvider(new ClientAuthenticationProvider(clientService));
        final AuthProvider userAuthProvider = new AuthProvider(new UserAuthenticationProvider(userAuthenticationManager));

        final AuthHandler clientAuthHandler = ChainAuthHandler.create()
                .append(ClientCredentialsAuthHandler.create(clientAuthProvider.getDelegate()))
                .append(ClientBasicAuthHandler.create(clientAuthProvider.getDelegate()));
        final AuthHandler userAuthHandler = RedirectAuthHandler.create(userAuthProvider.getDelegate(), contextPath() + "/login");

        // create web handlers
        setupCoreWebHandlers(router, userAuthProvider);

        // create other handlers
        final AuthorizationRequestParseHandler authorizationRequestParseHandler = AuthorizationRequestParseHandler.create();

        // bind login endpoints
        router.get("/login").handler(new LoginEndpointHandler(domain));
        router.post("/login").handler(FormLoginHandler.create(userAuthProvider.getDelegate()));

        // Bind OAuth2 endpoints
        Handler<RoutingContext> authorizeEndpoint = new AuthorizeEndpointHandler(clientService, approvalService, authorizationCodeService, tokenGranter);
        Handler<RoutingContext> tokenEndpoint = new TokenEndpointHandler(tokenGranter);
        Handler<RoutingContext> userApprovalEndpoint = new UserApprovalEndpointHandler();

        // Check_token is provided only for backward compatibility and must be remove in the future
        Handler<RoutingContext> checkTokenEndpoint = new CheckTokenEndpointHandler();
        ((CheckTokenEndpointHandler) checkTokenEndpoint).setTokenService(tokenService);

        Handler<RoutingContext> introspectionEndpoint = new IntrospectionEndpointHandler();
        ((IntrospectionEndpointHandler) introspectionEndpoint).setIntrospectionService(introspectionService);

        router.route(HttpMethod.POST, "/oauth/authorize")
                .handler(authorizationRequestParseHandler)
                .handler(userAuthHandler)
                .handler(authorizeEndpoint);
        router.route(HttpMethod.GET,"/oauth/authorize")
                .handler(authorizationRequestParseHandler)
                .handler(userAuthHandler)
                .handler(authorizeEndpoint);
        router.route(HttpMethod.POST, "/oauth/token")
                .handler(clientAuthHandler)
                .handler(tokenEndpoint);
        router.route(HttpMethod.POST, "/oauth/check_token")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(checkTokenEndpoint);
        router.route(HttpMethod.POST, "/oauth/introspect")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(introspectionEndpoint);
        router.route(HttpMethod.GET, "/oauth/confirm_access")
                .handler(userApprovalEndpoint);

        // OpenID endpoints
        Handler<RoutingContext> openIDProviderConfigurationEndpoint = new OpenIDProviderConfigurationEndpoint();
        ((OpenIDProviderConfigurationEndpoint) openIDProviderConfigurationEndpoint).setDiscoveryService(discoveryService);
        router.route(HttpMethod.GET, "/.well-known/openid-configuration").handler(openIDProviderConfigurationEndpoint);

        // bind failure handler
        router.route().failureHandler(new ExceptionHandler());

        return router;
    }

    public String contextPath() {
        return "/" + domain.getPath();
    }

    private void setupCoreWebHandlers(Router router, AuthProvider authProvider) {
        router.route().handler(CookieHandler.create());
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().handler(UserSessionHandler.create(authProvider));
        router.route().handler(BodyHandler.create());
        router.route().handler(StaticHandler.create());
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }
}
