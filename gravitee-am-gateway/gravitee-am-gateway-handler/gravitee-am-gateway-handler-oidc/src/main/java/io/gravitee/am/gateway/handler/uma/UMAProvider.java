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
package io.gravitee.am.gateway.handler.uma;

import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.uma.resources.endpoint.PermissionEndpoint;
import io.gravitee.am.gateway.handler.uma.resources.endpoint.ProviderConfigurationEndpoint;
import io.gravitee.am.gateway.handler.uma.resources.endpoint.ResourceAccessPoliciesEndpoint;
import io.gravitee.am.gateway.handler.uma.resources.endpoint.ResourceRegistrationEndpoint;
import io.gravitee.am.gateway.handler.uma.resources.handler.MethodNotSupportedHandler;
import io.gravitee.am.gateway.handler.uma.resources.handler.UMAProtectionApiAccessHandler;
import io.gravitee.am.gateway.handler.uma.resources.handler.UmaExceptionHandler;
import io.gravitee.am.gateway.handler.uma.service.discovery.UMADiscoveryService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.PermissionTicketService;
import io.gravitee.am.service.ResourceService;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.service.AbstractService;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.gateway.handler.uma.constants.UMAConstants.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UMAProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider{

    @Autowired
    private Vertx vertx;

    @Autowired
    private Router router;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private Domain domain;

    @Autowired
    private OAuth2AuthProvider oAuth2AuthProvider;

    @Autowired
    private UMADiscoveryService discoveryService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private PermissionTicketService permissionTicketService;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if(domain.getUma()!=null && domain.getUma().isEnabled()) {
            // Init web router
            initRouter();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    public String path() {
        return UMA_PATH;
    }

    private void initRouter() {
        final Router umaRouter = Router.router(vertx);

        // UMA Provider configuration information endpoint
        Handler<RoutingContext> umaProviderConfigurationEndpoint = new ProviderConfigurationEndpoint(discoveryService);
        umaRouter.route(WELL_KNOWN_PATH).handler(corsHandler);
        umaRouter
                .get(WELL_KNOWN_PATH)
                .handler(umaProviderConfigurationEndpoint);

        // User-Managed Access (UMA) 2.0 resources Auth handler
        OAuth2AuthHandler umaProtectionApiResourcesAuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider, Scope.UMA.getKey());
        umaProtectionApiResourcesAuthHandler.extractToken(true);
        umaProtectionApiResourcesAuthHandler.extractClient(true);
        umaProtectionApiResourcesAuthHandler.forceEndUserToken(true);//It must be a resource owner

        // User-Managed Access (UMA) 2.0 permissions Auth handler
        OAuth2AuthHandler umaProtectionApiPermissionsAuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider, Scope.UMA.getKey());
        umaProtectionApiPermissionsAuthHandler.extractToken(true);
        umaProtectionApiPermissionsAuthHandler.extractClient(true);
        umaProtectionApiPermissionsAuthHandler.forceClientToken(true);//It must be a client (client_credentials)

        // UMA resources Protection API Access Handler
        UMAProtectionApiAccessHandler umaProtectionApiResourcesAccessHandler = new UMAProtectionApiAccessHandler(domain, umaProtectionApiResourcesAuthHandler);

        // UMA permissions Protection API Access Handler
        UMAProtectionApiAccessHandler umaProtectionApiPermissionsAccessHandler = new UMAProtectionApiAccessHandler(domain, umaProtectionApiPermissionsAuthHandler);

        // Resource Registration endpoint
        ResourceRegistrationEndpoint resourceRegistrationEndpoint = new ResourceRegistrationEndpoint(domain, resourceService);
        umaRouter.route(RESOURCE_REGISTRATION_PATH).handler(corsHandler);

        umaRouter
                .get(RESOURCE_REGISTRATION_PATH)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceRegistrationEndpoint);
        umaRouter
                .post(RESOURCE_REGISTRATION_PATH)
                .consumes(MediaType.APPLICATION_JSON)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceRegistrationEndpoint::create);
        umaRouter
                .get(RESOURCE_REGISTRATION_PATH+"/:"+RESOURCE_ID)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceRegistrationEndpoint::get);
        umaRouter
                .put(RESOURCE_REGISTRATION_PATH+"/:"+RESOURCE_ID)
                .consumes(MediaType.APPLICATION_JSON)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceRegistrationEndpoint::update);
        umaRouter
                .delete(RESOURCE_REGISTRATION_PATH+"/:"+RESOURCE_ID)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceRegistrationEndpoint::delete);

        // Resource Access Policies endpoint
        ResourceAccessPoliciesEndpoint resourceAccessPoliciesEndpoint = new ResourceAccessPoliciesEndpoint(domain, resourceService);
        umaRouter
                .get(RESOURCE_REGISTRATION_PATH+"/:"+RESOURCE_ID+RESOURCE_ACCESS_POLICIES_PATH)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceAccessPoliciesEndpoint::list);
        umaRouter
                .post(RESOURCE_REGISTRATION_PATH+"/:"+RESOURCE_ID+RESOURCE_ACCESS_POLICIES_PATH)
                .consumes(MediaType.APPLICATION_JSON)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceAccessPoliciesEndpoint::create);
        umaRouter
                .get(RESOURCE_REGISTRATION_PATH+"/:"+RESOURCE_ID+RESOURCE_ACCESS_POLICIES_PATH+"/:"+POLICY_ID)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceAccessPoliciesEndpoint::get);
        umaRouter
                .put(RESOURCE_REGISTRATION_PATH+"/:"+RESOURCE_ID+RESOURCE_ACCESS_POLICIES_PATH+"/:"+POLICY_ID)
                .consumes(MediaType.APPLICATION_JSON)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceAccessPoliciesEndpoint::update);
        umaRouter
                .delete(RESOURCE_REGISTRATION_PATH+"/:"+RESOURCE_ID+RESOURCE_ACCESS_POLICIES_PATH+"/:"+POLICY_ID)
                .handler(umaProtectionApiResourcesAccessHandler)
                .handler(resourceAccessPoliciesEndpoint::delete);

        // Permission endpoint Access Handler
        PermissionEndpoint permissionEndpoint = new PermissionEndpoint(domain, permissionTicketService);
        umaRouter
                .post(PERMISSION_PATH)
                .consumes(MediaType.APPLICATION_JSON)
                .handler(umaProtectionApiPermissionsAccessHandler)
                .handler(permissionEndpoint);

        umaRouter
                .get(CLAIMS_INTERACTION_PATH)
                .handler(context -> context.response().setStatusCode(HttpStatusCode.NOT_IMPLEMENTED_501).end("Not Implemented Yet"));

        // Not supported method for others method
        MethodNotSupportedHandler notSupportedFallbackHandler = new MethodNotSupportedHandler();
        umaRouter.route(RESOURCE_REGISTRATION_PATH).handler(notSupportedFallbackHandler);
        umaRouter.route(RESOURCE_REGISTRATION_PATH+"/:"+RESOURCE_ID).handler(notSupportedFallbackHandler);
        umaRouter.route(PERMISSION_PATH).handler(notSupportedFallbackHandler);

        // error handler
        errorHandler(umaRouter);

        router.mountSubRouter(path(), umaRouter);
    }

    private void errorHandler(Router router) {
        router.route().failureHandler(new UmaExceptionHandler());
    }
}
