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
package io.gravitee.am.gateway.handler.oidc;

import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwe.JWEService;
import io.gravitee.am.gateway.handler.common.jwk.JWKService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.oauth2.OAuth2Provider;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.ExtensionGrantManager;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oidc.resources.endpoint.*;
import io.gravitee.am.gateway.handler.oidc.resources.handler.DynamicClientAccessHandler;
import io.gravitee.am.gateway.handler.oidc.resources.handler.DynamicClientAccessTokenHandler;
import io.gravitee.am.gateway.handler.oidc.resources.handler.DynamicClientRegistrationHandler;
import io.gravitee.am.gateway.handler.oidc.resources.handler.DynamicClientRegistrationTemplateHandler;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.service.AbstractService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static io.gravitee.am.common.oauth2.Parameters.CLIENT_ID;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class OIDCProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Router router;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private OpenIDDiscoveryService discoveryService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private JWKService jwkService;

    @Autowired
    private JWEService jweService;

    @Autowired
    private DynamicClientRegistrationService dcrService;

    @Autowired
    private Domain domain;

    @Autowired
    private OAuth2AuthProvider oAuth2AuthProvider;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ExtensionGrantManager extensionGrantManager;

    @Autowired
    private ScopeManager scopeManager;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // OpenId Connect Protocol relies on OAuth 2.0 Protocol
        // Start OAuth 2.0 provider first
        startOAuth2Protocol();

        // Start OpenID Connect provider
        startOpenIDConnectProtocol();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        extensionGrantManager.stop();
        scopeManager.stop();
    }

    @Override
    public String path() {
        return "/oidc";
    }

    private void startOAuth2Protocol() throws Exception {
        OAuth2Provider oAuth2Provider = applicationContext.getBean(OAuth2Provider.class);
        oAuth2Provider.start();
    }

    private void startOpenIDConnectProtocol() {
        // Create the OpenID Connect router
        final Router oidcRouter = Router.router(vertx);

        // OpenID Provider Configuration Information Endpoint
        Handler<RoutingContext> openIDProviderConfigurationEndpoint = new ProviderConfigurationEndpoint();
        ((ProviderConfigurationEndpoint) openIDProviderConfigurationEndpoint).setDiscoveryService(discoveryService);
        oidcRouter.route("/.well-known/openid-configuration").handler(corsHandler);
        oidcRouter
                .route(HttpMethod.GET, "/.well-known/openid-configuration")
                .handler(openIDProviderConfigurationEndpoint);

        // UserInfo Endpoint
        OAuth2AuthHandler userInfoAuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider, Scope.OPENID.getKey());
        userInfoAuthHandler.extractToken(true);
        userInfoAuthHandler.extractClient(true);
        userInfoAuthHandler.forceEndUserToken(true);

        Handler<RoutingContext> userInfoEndpoint = new UserInfoEndpoint(userService, roleService, groupService, jwtService, jweService, discoveryService);
        oidcRouter.route("/userinfo").handler(corsHandler);
        oidcRouter
                .route(HttpMethod.GET, "/userinfo")
                .handler(userInfoAuthHandler)
                .handler(userInfoEndpoint);
        oidcRouter
                .route(HttpMethod.POST, "/userinfo")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(userInfoAuthHandler)
                .handler(userInfoEndpoint);

        // OpenID Provider JWK Set
        Handler<RoutingContext> openIDProviderJWKSetEndpoint = new ProviderJWKSetEndpoint(jwkService);
        oidcRouter.route("/.well-known/jwks.json").handler(corsHandler);
        oidcRouter
                .route(HttpMethod.GET, "/.well-known/jwks.json")
                .handler(openIDProviderJWKSetEndpoint);

        // Dynamic Client Registration templates
        DynamicClientRegistrationTemplateHandler dynamicClientRegistrationTemplateHandler = new DynamicClientRegistrationTemplateHandler(domain);
        DynamicClientRegistrationTemplateEndpoint dynamicClientRegistrationTemplateEndpoint = new DynamicClientRegistrationTemplateEndpoint(clientSyncService);
        oidcRouter
                .route(HttpMethod.GET, "/register_templates")
                .handler(dynamicClientRegistrationTemplateHandler)
                .handler(dynamicClientRegistrationTemplateEndpoint);

        // Dynamic Client Registration
        OAuth2AuthHandler dynamicClientRegistrationAuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider, Scope.DCR_ADMIN.getKey());
        dynamicClientRegistrationAuthHandler.extractToken(true);
        dynamicClientRegistrationAuthHandler.extractClient(true);
        dynamicClientRegistrationAuthHandler.forceClientToken(true);

        DynamicClientRegistrationHandler dynamicClientRegistrationHandler = new DynamicClientRegistrationHandler(domain, dynamicClientRegistrationAuthHandler);
        DynamicClientRegistrationEndpoint dynamicClientRegistrationEndpoint = new DynamicClientRegistrationEndpoint(dcrService, clientSyncService);
        oidcRouter
                .route(HttpMethod.POST, "/register")
                .consumes(MediaType.APPLICATION_JSON)
                .handler(dynamicClientRegistrationHandler)
                .handler(dynamicClientRegistrationEndpoint);

        // Dynamic Client Configuration
        OAuth2AuthHandler dynamicClientAccessAuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider, Scope.DCR_ADMIN.getKey());
        dynamicClientAccessAuthHandler.extractRawToken(true);
        dynamicClientAccessAuthHandler.extractToken(true);
        dynamicClientAccessAuthHandler.extractClient(true);
        dynamicClientAccessAuthHandler.forceClientToken(true);
        dynamicClientAccessAuthHandler.selfResource(true, CLIENT_ID, Scope.DCR.getKey());
        dynamicClientAccessAuthHandler.offlineVerification(true);

        DynamicClientAccessHandler dynamicClientAccessHandler = new DynamicClientAccessHandler(domain);
        DynamicClientAccessTokenHandler dynamicClientAccessTokenHandler = new DynamicClientAccessTokenHandler();
        DynamicClientAccessEndpoint dynamicClientAccessEndpoint = new DynamicClientAccessEndpoint(dcrService, clientSyncService);
        oidcRouter
                .route(HttpMethod.GET, "/register/:"+CLIENT_ID)
                .handler(dynamicClientAccessHandler)
                .handler(dynamicClientAccessAuthHandler)
                .handler(dynamicClientAccessTokenHandler)
                .handler(dynamicClientAccessEndpoint::read);
        oidcRouter
                .route(HttpMethod.PATCH, "/register/:"+CLIENT_ID)
                .consumes(MediaType.APPLICATION_JSON)
                .handler(dynamicClientAccessHandler)
                .handler(dynamicClientAccessAuthHandler)
                .handler(dynamicClientAccessTokenHandler)
                .handler(dynamicClientAccessEndpoint::patch);
        oidcRouter
                .route(HttpMethod.PUT, "/register/:"+CLIENT_ID)
                .consumes(MediaType.APPLICATION_JSON)
                .handler(dynamicClientAccessHandler)
                .handler(dynamicClientAccessAuthHandler)
                .handler(dynamicClientAccessTokenHandler)
                .handler(dynamicClientAccessEndpoint::update);
        oidcRouter
                .route(HttpMethod.DELETE, "/register/:"+CLIENT_ID)
                .handler(dynamicClientAccessHandler)
                .handler(dynamicClientAccessAuthHandler)
                .handler(dynamicClientAccessTokenHandler)
                .handler(dynamicClientAccessEndpoint::delete);
        oidcRouter
                .route(HttpMethod.POST, "/register/:"+CLIENT_ID+"/renew_secret")
                .handler(dynamicClientAccessHandler)
                .handler(dynamicClientAccessAuthHandler)
                .handler(dynamicClientAccessTokenHandler)
                .handler(dynamicClientAccessEndpoint::renewClientSecret);

        // error handler
        errorHandler(oidcRouter);

        router.mountSubRouter(path(), oidcRouter);
    }

    private void errorHandler(Router router) {
        router.route().failureHandler(new ExceptionHandler());
    }
}
