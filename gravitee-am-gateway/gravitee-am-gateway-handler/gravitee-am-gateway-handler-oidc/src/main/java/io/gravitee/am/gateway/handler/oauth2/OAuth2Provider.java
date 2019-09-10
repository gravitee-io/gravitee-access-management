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
package io.gravitee.am.gateway.handler.oauth2;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.RedirectAuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.endpoint.ErrorEndpoint;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.PolicyChainHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAssertionAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientBasicAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientCredentialsAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.impl.ClientChainAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientAssertionAuthenticationProvider;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.AuthorizationEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.AuthorizationFailureEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.approval.UserApprovalEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.approval.UserApprovalSubmissionEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.approval.UserApprovalProcessHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.introspection.IntrospectionEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.revocation.RevocationTokenEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.token.TokenEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseClientHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseParametersHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseRequiredParametersHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.approval.UserApprovalFailureHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.approval.UserApprovalRequestParseHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.token.TokenRequestParseHandler;
import io.gravitee.am.gateway.handler.oauth2.service.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oauth2.service.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.service.revocation.RevocationTokenService;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeService;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.flow.Flow;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.service.AbstractService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2Provider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Provider.class);

    @Autowired
    private Domain domain;

    @Autowired
    private Router router;

    @Autowired
    private Vertx vertx;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private ClientAssertionService clientAssertionService;

    @Autowired
    private Flow flow;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private TokenGranter tokenGranter;

    @Autowired
    private IntrospectionService introspectionService;

    @Autowired
    private RevocationTokenService revocationTokenService;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Autowired
    private ThymeleafTemplateEngine thymeleafTemplateEngine;

    @Autowired
    private UserAuthProvider userAuthProvider;

    @Autowired
    private SessionHandler sessionHandler;

    @Autowired
    private CookieHandler cookieHandler;

    @Autowired
    private CSRFHandler csrfHandler;

    @Autowired
    private PolicyChainHandler policyChainHandler;

    @Autowired
    private TokenManager tokenManager;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // init services
        initServices();

        // init web router
        initRouter();
    }

    private void initServices() {
        try {
            tokenManager.start();
        } catch (Exception e) {
            logger.error("An error occurs while starting oauth 2.0 services", e);
        }
    }

    private void initRouter() {
        // Create the OAuth 2.0 router
        final Router oauth2Router = Router.router(vertx);

        // client auth handler
        final AuthProvider clientAuthProvider = new ClientAuthenticationProvider(clientSyncService);
        final AuthProvider clientAssertionAuthProvider = new ClientAssertionAuthenticationProvider(clientAssertionService);

        final AuthHandler clientAuthHandler = ChainAuthHandler.newInstance(new ClientChainAuthHandler())
                .append(ClientCredentialsAuthHandler.create(clientAuthProvider))
                .append(ClientAssertionAuthHandler.create(clientAssertionAuthProvider))
                .append(ClientBasicAuthHandler.create(clientAuthProvider));

        // user auth handler
        final AuthHandler userAuthHandler = RedirectAuthHandler.create(userAuthProvider, domain);

        // Bind OAuth2 endpoints

        // Authorization endpoint
        Handler<RoutingContext> authorizationRequestParseRequiredParametersHandler = new AuthorizationRequestParseRequiredParametersHandler(openIDDiscoveryService);
        Handler<RoutingContext> authorizationRequestParseClientHandler = new AuthorizationRequestParseClientHandler(clientSyncService);
        Handler<RoutingContext> authorizationRequestParseParametersHandler = new AuthorizationRequestParseParametersHandler(domain);
        Handler<RoutingContext> authorizeEndpoint = new AuthorizationEndpoint(flow, domain);
        Handler<RoutingContext> authorizeFailureEndpoint = new AuthorizationFailureEndpoint(domain);
        Handler<RoutingContext> userApprovalRequestParseHandler = new UserApprovalRequestParseHandler(clientSyncService);
        Handler<RoutingContext> userApprovalProcessHandler = new UserApprovalProcessHandler(approvalService, domain);
        Handler<RoutingContext> userApprovalEndpoint = new UserApprovalEndpoint(scopeService, thymeleafTemplateEngine);
        Handler<RoutingContext> userApprovalSubmissionEndpoint = new UserApprovalSubmissionEndpoint(domain);
        Handler<RoutingContext> userApprovalFailureHandler = new UserApprovalFailureHandler(domain);

        // Token endpoint
        Handler<RoutingContext> tokenEndpoint = new TokenEndpoint(tokenGranter);
        Handler<RoutingContext> tokenRequestParseHandler = new TokenRequestParseHandler();

        // Check_token is provided only for backward compatibility and must be remove in the future
        Handler<RoutingContext> introspectionEndpoint = new IntrospectionEndpoint();
        ((IntrospectionEndpoint) introspectionEndpoint).setIntrospectionService(introspectionService);

        // Revocation token endpoint
        Handler<RoutingContext> revocationTokenEndpoint = new RevocationTokenEndpoint(revocationTokenService);

        // static handler
        staticHandler(oauth2Router);

        // session cookie handler
        sessionAndCookieHandler(oauth2Router);

        // CSRF handler
        csrfHandler(oauth2Router);

        // declare oauth2 routes
        oauth2Router.route(HttpMethod.GET,"/authorize")
                .handler(authorizationRequestParseRequiredParametersHandler)
                .handler(authorizationRequestParseClientHandler)
                .handler(authorizationRequestParseParametersHandler)
                .handler(userAuthHandler)
                .handler(authorizeEndpoint)
                .failureHandler(authorizeFailureEndpoint);
        oauth2Router.route(HttpMethod.POST, "/authorize")
                .handler(userAuthHandler)
                .handler(userApprovalRequestParseHandler)
                .handler(userApprovalProcessHandler)
                .handler(policyChainHandler.create(ExtensionPoint.POST_CONSENT))
                .handler(userApprovalSubmissionEndpoint)
                .failureHandler(userApprovalFailureHandler)
                .failureHandler(authorizeFailureEndpoint);
        oauth2Router.route(HttpMethod.POST, "/token")
                .handler(tokenRequestParseHandler)
                .handler(clientAuthHandler)
                .handler(tokenEndpoint);
        oauth2Router.route(HttpMethod.POST, "/introspect")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(introspectionEndpoint);
        oauth2Router.route(HttpMethod.POST, "/revoke")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(revocationTokenEndpoint);
        oauth2Router.route(HttpMethod.GET, "/confirm_access")
                .handler(userAuthHandler)
                .handler(userApprovalRequestParseHandler)
                .handler(policyChainHandler.create(ExtensionPoint.PRE_CONSENT))
                .handler(userApprovalEndpoint)
                .failureHandler(userApprovalFailureHandler);
        oauth2Router.route(HttpMethod.GET, "/error")
                .handler(new ErrorEndpoint(domain.getId(), thymeleafTemplateEngine, clientSyncService));

        // error handler
        errorHandler(oauth2Router);

        // mount OAuth 2.0 router
        router.mountSubRouter(path(), oauth2Router);
    }

    @Override
    public String path() {
        return "/oauth";
    }

    private void staticHandler(Router router) {
        router.route().handler(StaticHandler.create());
    }

    private void sessionAndCookieHandler(Router router) {
        // OAuth 2.0 Authorization endpoint
        router
                .route("/authorize")
                .handler(cookieHandler)
                .handler(sessionHandler);
        router
                .route("/confirm_access")
                .handler(cookieHandler)
                .handler(sessionHandler);
    }

    private void csrfHandler(Router router) {
        router.route("/confirm_access").handler(csrfHandler);
    }

    private void errorHandler(Router router) {
        router.route().failureHandler(new ExceptionHandler());
    }

}
