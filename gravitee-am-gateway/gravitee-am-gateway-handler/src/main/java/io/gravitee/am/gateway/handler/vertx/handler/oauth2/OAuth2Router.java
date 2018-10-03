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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2;

import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.revocation.RevocationTokenService;
import io.gravitee.am.gateway.handler.oauth2.scope.ScopeService;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.flow.Flow;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientBasicAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientCredentialsAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.RedirectAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.provider.ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.ErrorHandlerEndpoint;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization.AuthorizationApprovalEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization.AuthorizationEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization.AuthorizationRequestParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization.UserApprovalEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.introspection.CheckTokenEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.introspection.IntrospectionEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.revocation.RevocationTokenEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.token.TokenEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.token.TokenRequestParseHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.AuthHandler;
import io.vertx.reactivex.ext.web.handler.ChainAuthHandler;
import io.vertx.reactivex.ext.web.templ.ThymeleafTemplateEngine;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2Router {

    @Autowired
    private TokenGranter tokenGranter;

    @Autowired
    private Flow flow;

    @Autowired
    private ClientService clientService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private IntrospectionService introspectionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private RevocationTokenService revocationTokenService;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Autowired
    private ThymeleafTemplateEngine thymeleafTemplateEngine;

    @Autowired
    private Domain domain;

    @Autowired
    private Vertx vertx;

    public Router route(AuthProvider userAuthProvider) {
        // Create the OAuth 2.0 router
        final Router router = Router.router(vertx);

        // create authentication handlers
        final AuthProvider clientAuthProvider = new AuthProvider(new ClientAuthenticationProvider(clientService));

        final AuthHandler clientAuthHandler = ChainAuthHandler.create()
                .append(ClientCredentialsAuthHandler.create(clientAuthProvider.getDelegate()))
                .append(ClientBasicAuthHandler.create(clientAuthProvider.getDelegate()));

        final AuthHandler userAuthHandler = RedirectAuthHandler.create(
                userAuthProvider.getDelegate(), '/' + domain.getPath() + "/login");

        // Bind OAuth2 endpoints
        // Authorization endpoint
        AuthorizationRequestParseHandler authorizationRequestParseHandler = AuthorizationRequestParseHandler.create(domain, openIDDiscoveryService);
        Handler<RoutingContext> authorizeEndpoint = new AuthorizationEndpointHandler(flow, domain);
        Handler<RoutingContext> authorizeApprovalEndpoint = new AuthorizationApprovalEndpointHandler(approvalService);
        Handler<RoutingContext> userApprovalEndpoint = new UserApprovalEndpointHandler(clientService, scopeService, thymeleafTemplateEngine);
        // Token endpoint
        Handler<RoutingContext> tokenEndpoint = new TokenEndpointHandler(tokenGranter);
        Handler<RoutingContext> tokenRequestParseHandler = new TokenRequestParseHandler();

        // Check_token is provided only for backward compatibility and must be remove in the future
        Handler<RoutingContext> checkTokenEndpoint = new CheckTokenEndpointHandler();
        ((CheckTokenEndpointHandler) checkTokenEndpoint).setTokenService(tokenService);

        Handler<RoutingContext> introspectionEndpoint = new IntrospectionEndpointHandler();
        ((IntrospectionEndpointHandler) introspectionEndpoint).setIntrospectionService(introspectionService);

        // Revocation token endpoint
        Handler<RoutingContext> revocationTokenEndpoint = new RevocationTokenEndpointHandler(revocationTokenService);

        // declare oauth2 routes
        router.route(HttpMethod.GET,"/authorize")
                .handler(authorizationRequestParseHandler)
                .handler(userAuthHandler)
                .handler(authorizeEndpoint);
        router.route(HttpMethod.POST, "/authorize")
                .handler(userAuthHandler)
                .handler(authorizeApprovalEndpoint);
        router.route(HttpMethod.POST, "/token")
                .handler(tokenRequestParseHandler)
                .handler(clientAuthHandler)
                .handler(tokenEndpoint);
        router.route(HttpMethod.POST, "/check_token")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(checkTokenEndpoint);
        router.route(HttpMethod.POST, "/introspect")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(introspectionEndpoint);
        router.route(HttpMethod.POST, "/revoke")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(revocationTokenEndpoint);
        router.route(HttpMethod.GET, "/confirm_access")
                .handler(userApprovalEndpoint);
        router.route(HttpMethod.GET, "/error")
                .handler(new ErrorHandlerEndpoint(thymeleafTemplateEngine));

        return router;
    }
}
