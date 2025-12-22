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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.api.AbstractProtocolProvider;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.gateway.handler.common.service.DeviceGatewayService;
import io.gravitee.am.gateway.handler.common.service.UserActivityGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.web.endpoint.ErrorEndpoint;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.AuthenticationFlowContextHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.AuthenticationFlowHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.CSPHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.PolicyChainHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.SSOSessionHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.XFrameHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.XSSHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSessionHandler;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.AuthorizationEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.consent.UserConsentEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.consent.UserConsentPostEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.introspection.IntrospectionEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.par.PushedAuthorizationRequestEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.revocation.RevocationTokenEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.token.TokenEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestEndUserConsentHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestFailureHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestMFAPromptHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseClientHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseIdTokenHintHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseParametersHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseProviderConfigurationHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseRequestObjectHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseRequiredParametersHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestResolveHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent.UserConsentFailureHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent.UserConsentPrepareContextHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent.UserConsentProcessHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.risk.RiskAssessmentHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.token.TokenRequestParseHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.validation.AuthorizationRequestResourceValidationHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.validation.TokenRequestResourceValidationHandler;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceValidationService;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.revocation.OAuthRevocationTokenService;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.flow.Flow;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.gateway.handler.root.handler.LoggerJsonMessageTokenHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.LocaleHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.common.RedirectUriValidationHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.common.ReturnUrlValidationHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.CSRFHandler;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import io.vertx.rxjava3.ext.web.handler.StaticHandler;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;

import static io.gravitee.am.gateway.handler.root.handler.LoggerJsonMessageTokenHandler.PROPERTY_REQUEST_JSON_LOGGER_ENABLED;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2Provider extends AbstractProtocolProvider {


    @Autowired
    private Domain domain;

    @Autowired
    private Router router;

    @Autowired
    private Vertx vertx;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private ProtectedResourceSyncService protectedResourceSyncService;

    @Autowired
    private ResourceValidationService resourceValidationService;

    @Autowired
    private ClientAssertionService clientAssertionService;

    @Autowired
    private Flow flow;

    @Autowired
    private ExecutionContextFactory executionContextFactory;

    @Autowired
    private UserConsentService userConsentService;

    @Autowired
    private TokenGranter tokenGranter;

    @Autowired
    private IntrospectionService introspectionService;

    @Autowired
    private OAuthRevocationTokenService revocationTokenService;

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
    private CSPHandler cspHandler;

    @Autowired
    private XFrameHandler xframeHandler;

    @Autowired
    private XSSHandler xssHandler;

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

    @Autowired
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Autowired
    private IDTokenService idTokenService;

    @Autowired
    private Environment environment;

    @Autowired
    private PushedAuthorizationRequestService parService;

    @Autowired
    private ScopeManager scopeManager;

    @Autowired
    private ProtectedResourceManager protectedResourceManager;

    @Autowired
    private DeviceGatewayService deviceService;

    @Autowired
    private UserActivityGatewayService userActivityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("gwMessageResolver")
    private GraviteeMessageResolver messageResolver;

    @Autowired
    private SecretService secretService;

    @Autowired
    private AuditService auditService;

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
        final String certificateHeader = environment.getProperty(ConstantKeys.HTTP_SSL_CERTIFICATE_HEADER);
        final Handler<RoutingContext> clientAuthHandler = ClientAuthHandler.create(clientSyncService, clientAssertionService, jwkService, domain, secretService, certificateHeader, auditService, protectedResourceSyncService);

        // static handler
        staticHandler(oauth2Router);

        // register Logger handler first to have Response log
        // whatever the processing result
        loggerHandler(oauth2Router);

        // session cookie handler
        sessionAndCookieHandler(oauth2Router);

        // CSRF handler
        csrfHandler(oauth2Router);

        // CSP Handler
        cspHandler(oauth2Router);

        xFrameHandler(oauth2Router);

        xssHandler(oauth2Router);

        AuthenticationFlowContextHandler authenticationFlowContextHandler = new AuthenticationFlowContextHandler(authenticationFlowContextService, environment);
        Handler<RoutingContext> redirectUriValidationHandler = new RedirectUriValidationHandler(domain);
        ReturnUrlValidationHandler returnUrlValidationHandler = new ReturnUrlValidationHandler(domain);
        Handler<RoutingContext> localeHandler = new LocaleHandler(messageResolver);

        // Authorization endpoint
        oauth2Router.route(HttpMethod.OPTIONS, "/authorize")
                .handler(corsHandler);
        oauth2Router.route(HttpMethod.GET, "/authorize")
                .handler(corsHandler)
                .handler(new AuthorizationRequestParseProviderConfigurationHandler(openIDDiscoveryService))
                .handler(new AuthorizationRequestParseRequiredParametersHandler())
                .handler(new AuthorizationRequestParseClientHandler(clientSyncService))
                .handler(authenticationFlowContextHandler)
                .handler(new AuthorizationRequestParseRequestObjectHandler(requestObjectService, domain, parService, authenticationFlowContextService))
                .handler(new AuthorizationRequestParseIdTokenHintHandler(idTokenService))
                .handler(new AuthorizationRequestParseParametersHandler(domain))
                .handler(redirectUriValidationHandler)
                .handler(returnUrlValidationHandler)
                .handler(new RiskAssessmentHandler(deviceService, userActivityService, vertx.eventBus(), objectMapper, domain))
                .handler(authenticationFlowHandler.create())
                .handler(new AuthorizationRequestResolveHandler(domain, scopeManager, protectedResourceManager, executionContextFactory))
                .handler(new AuthorizationRequestResourceValidationHandler(resourceValidationService))
                .handler(new AuthorizationRequestEndUserConsentHandler(userConsentService))
                .handler(new AuthorizationRequestMFAPromptHandler())
                .handler(new AuthorizationEndpoint(flow, thymeleafTemplateEngine, parService))
                .failureHandler(new AuthorizationRequestFailureHandler(openIDDiscoveryService, jwtService, jweService, environment));

        // Authorization consent endpoint
        Handler<RoutingContext> userConsentPrepareContextHandler = new UserConsentPrepareContextHandler();
        oauth2Router.route(HttpMethod.GET, "/consent")
                .handler(new AuthorizationRequestParseClientHandler(clientSyncService))
                .handler(new AuthorizationRequestParseProviderConfigurationHandler(openIDDiscoveryService))
                .handler(authenticationFlowContextHandler)
                .handler(new AuthorizationRequestParseRequestObjectHandler(requestObjectService, domain, parService, authenticationFlowContextService))
                .handler(new AuthorizationRequestResolveHandler(domain, scopeManager, protectedResourceManager, executionContextFactory))
                .handler(redirectUriValidationHandler)
                .handler(returnUrlValidationHandler)
                .handler(userConsentPrepareContextHandler)
                .handler(policyChainHandler.create(ExtensionPoint.PRE_CONSENT))
                .handler(localeHandler)
                .handler(new UserConsentEndpoint(userConsentService, thymeleafTemplateEngine, domain));
        oauth2Router.route(HttpMethod.POST, "/consent")
                .handler(new AuthorizationRequestParseClientHandler(clientSyncService))
                .handler(new AuthorizationRequestParseProviderConfigurationHandler(openIDDiscoveryService))
                .handler(authenticationFlowContextHandler)
                .handler(new AuthorizationRequestParseRequestObjectHandler(requestObjectService, domain, parService, authenticationFlowContextService))
                .handler(new AuthorizationRequestResolveHandler(domain, scopeManager, protectedResourceManager, executionContextFactory))
                .handler(redirectUriValidationHandler)
                .handler(returnUrlValidationHandler)
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
                .handler(new TokenRequestResourceValidationHandler(resourceValidationService))
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
                .handler(new ErrorEndpoint(domain, thymeleafTemplateEngine, clientSyncService, jwtService));

        // Pushed Authorization Request
        oauth2Router.route(HttpMethod.POST, "/par")
                .handler(clientAuthHandler)
                .handler(new PushedAuthorizationRequestEndpoint(parService));

        oauth2Router.route("/par")
                .handler(new PushedAuthorizationRequestEndpoint.MethodNotAllowedHandler());

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

    private void cspHandler(Router router) {
        router.route("/consent").handler(cspHandler);
    }

    private void xFrameHandler(Router router) {
        router.route("/consent").handler(xframeHandler);
    }
    private void xssHandler(Router router) {
        router.route("/consent").handler(xssHandler);
    }

    private void errorHandler(Router router) {
        router.route().failureHandler(new ExceptionHandler());
    }

    private void loggerHandler(Router router) {
        if (environment.getProperty(PROPERTY_REQUEST_JSON_LOGGER_ENABLED, Boolean.class, false)) {
            router.route()
                    .handler(new LoggerJsonMessageTokenHandler(environment));
        }
    }

}
