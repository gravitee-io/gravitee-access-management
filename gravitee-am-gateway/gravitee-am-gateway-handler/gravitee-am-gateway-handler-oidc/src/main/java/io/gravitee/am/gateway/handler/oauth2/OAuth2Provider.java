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
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.web.endpoint.ErrorEndpoint;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.AuthenticationFlowHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.PolicyChainHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.SSOSessionHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSessionHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.AuthorizationEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.consent.UserConsentEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.consent.UserConsentPostEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.introspection.IntrospectionEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.revocation.RevocationTokenEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.token.TokenEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.*;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent.UserConsentFailureHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent.UserConsentPrepareContextHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent.UserConsentProcessHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.token.TokenRequestParseHandler;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.service.revocation.RevocationTokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenManager;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseRequestObjectHandler;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.flow.Flow;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.service.AbstractService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2Provider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Provider.class);

    @Value("${handlers.request.transaction.header:X-Gravitee-Transaction-Id}")
    private String transactionHeader;

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
    private UserConsentService userConsentService;

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
    private CookieSessionHandler sessionHandler;

    @Autowired
    private SSOSessionHandler ssoSessionHandler;

    @Autowired
    private CSRFHandler csrfHandler;

    @Autowired
    private PolicyChainHandler policyChainHandler;

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private AuthenticationFlowHandler authenticationFlowHandler;

    @Autowired
    private JWKService jwkService;

    @Autowired
    private RequestObjectService requestObjectService;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private JWEService jweService;

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
        final Handler<RoutingContext> clientAuthHandler = ClientAuthHandler.create(clientSyncService, clientAssertionService, jwkService);

        // static handler
        staticHandler(oauth2Router);

        // session cookie handler
        sessionAndCookieHandler(oauth2Router);

        // CSRF handler
        csrfHandler(oauth2Router);

        // Authorization endpoint
        oauth2Router.route(HttpMethod.GET,"/authorize")
                .handler(new AuthorizationRequestTransactionHandler(transactionHeader))
                .handler(new AuthorizationRequestParseProviderConfigurationHandler(openIDDiscoveryService))
                .handler(new AuthorizationRequestParseRequiredParametersHandler())
                .handler(new AuthorizationRequestParseClientHandler(clientSyncService))
                .handler(new AuthorizationRequestParseRequestObjectHandler(requestObjectService))
                .handler(new AuthorizationRequestParseParametersHandler(domain))
                .handler(authenticationFlowHandler.create())
                .handler(new AuthorizationRequestResolveHandler())
                .handler(new AuthorizationRequestEndUserConsentHandler(userConsentService))
                .handler(new AuthorizationEndpoint(flow))
                .failureHandler(new AuthorizationRequestFailureHandler(openIDDiscoveryService, jwtService, jweService));

        // Authorization consent endpoint
        Handler<RoutingContext> userConsentPrepareContextHandler = new UserConsentPrepareContextHandler(clientSyncService);
        oauth2Router.route(HttpMethod.GET, "/consent")
                .handler(new AuthorizationRequestParseClientHandler(clientSyncService))
                .handler(new AuthorizationRequestResolveHandler())
                .handler(userConsentPrepareContextHandler)
                .handler(policyChainHandler.create(ExtensionPoint.PRE_CONSENT))
                .handler(new UserConsentEndpoint(userConsentService, thymeleafTemplateEngine));
        oauth2Router.route(HttpMethod.POST, "/consent")
                .handler(new AuthorizationRequestParseClientHandler(clientSyncService))
                .handler(new AuthorizationRequestResolveHandler())
                .handler(userConsentPrepareContextHandler)
                .handler(new UserConsentProcessHandler(userConsentService, domain))
                .handler(policyChainHandler.create(ExtensionPoint.POST_CONSENT))
                .handler(new UserConsentPostEndpoint());
        oauth2Router.route("/consent")
                .failureHandler(new UserConsentFailureHandler());

        // Token endpoint
        oauth2Router.route(HttpMethod.OPTIONS, "/token")
                .handler(corsHandler);
        oauth2Router.route(HttpMethod.POST, "/token")
                .handler(corsHandler)
                .handler(new TokenRequestParseHandler())
                .handler(clientAuthHandler)
                .handler(new TokenEndpoint(tokenGranter));

        // Introspection endpoint
        oauth2Router.route(HttpMethod.POST, "/introspect")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(new IntrospectionEndpoint(introspectionService));

        // Revocation endpoint
        oauth2Router.route(HttpMethod.OPTIONS, "/revoke")
                .handler(corsHandler);
        oauth2Router.route(HttpMethod.POST, "/revoke")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(corsHandler)
                .handler(clientAuthHandler)
                .handler(new RevocationTokenEndpoint(revocationTokenService));

        // Error endpoint
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
                .handler(sessionHandler)
                .handler(ssoSessionHandler);
        router
                .route("/consent")
                .handler(sessionHandler)
                .handler(ssoSessionHandler);
    }

    private void csrfHandler(Router router) {
        router.route("/consent").handler(csrfHandler);
    }

    private void errorHandler(Router router) {
        router.route().failureHandler(new ExceptionHandler());
    }

}
