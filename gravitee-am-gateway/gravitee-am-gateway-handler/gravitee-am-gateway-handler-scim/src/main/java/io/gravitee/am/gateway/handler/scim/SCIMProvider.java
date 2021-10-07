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
package io.gravitee.am.gateway.handler.scim;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.scim.resources.ErrorHandler;
import io.gravitee.am.gateway.handler.scim.resources.configuration.ServiceProviderConfigurationEndpointHandler;
import io.gravitee.am.gateway.handler.scim.resources.groups.GroupEndpoint;
import io.gravitee.am.gateway.handler.scim.resources.groups.GroupsEndpoint;
import io.gravitee.am.gateway.handler.scim.resources.users.UserEndpoint;
import io.gravitee.am.gateway.handler.scim.resources.users.UsersEndpoint;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.am.gateway.handler.scim.service.ServiceProviderConfigService;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.common.service.AbstractService;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SCIMProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    @Autowired
    private Router router;

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ServiceProviderConfigService serviceProviderConfigService;

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private OAuth2AuthProvider oAuth2AuthProvider;

    @Autowired
    private CorsHandler corsHandler;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (isSCIMEnabled()) {
            // Create the SCIM router
            final Router scimRouter = Router.router(vertx);

            // CORS handler
            scimRouter.route().handler(corsHandler);

            // Declare SCIM routes
            // see <a href="https://tools.ietf.org/html/rfc7644#section-3.2">3.2. SCIM Endpoints and HTTP Methods</a>

            // Service Provider configuration
            ServiceProviderConfigurationEndpointHandler serviceProviderConfigurationEndpointHandler = ServiceProviderConfigurationEndpointHandler.create(serviceProviderConfigService);
            serviceProviderConfigurationEndpointHandler.setObjectMapper(objectMapper);
            scimRouter.get("/ServiceProviderConfig").handler(serviceProviderConfigurationEndpointHandler);

            // SCIM resources routes are OAuth 2.0 secured
            OAuth2AuthHandler oAuth2AuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider, "scim");
            oAuth2AuthHandler.extractToken(true);
            oAuth2AuthHandler.extractClient(true);
            scimRouter.route().handler(oAuth2AuthHandler);

            // Users resource
            UsersEndpoint usersEndpoint = new UsersEndpoint(domain, userService, objectMapper);
            UserEndpoint userEndpoint = new UserEndpoint(domain, userService, objectMapper);

            scimRouter.get("/Users").handler(usersEndpoint::list);
            scimRouter.get("/Users/:id").handler(userEndpoint::get);
            scimRouter.post("/Users").handler(usersEndpoint::create);
            scimRouter.put("/Users/:id").handler(userEndpoint::update);
            scimRouter.patch("/Users/:id").handler(userEndpoint::patch);
            scimRouter.delete("/Users/:id").handler(userEndpoint::delete);

            // Groups resource
            GroupsEndpoint groupsEndpoint = new GroupsEndpoint(groupService, objectMapper);
            GroupEndpoint groupEndpoint = new GroupEndpoint(groupService, objectMapper);

            scimRouter.get("/Groups").handler(groupsEndpoint::list);
            scimRouter.get("/Groups/:id").handler(groupEndpoint::get);
            scimRouter.post("/Groups").handler(groupsEndpoint::create);
            scimRouter.put("/Groups/:id").handler(groupEndpoint::update);
            scimRouter.patch("/Groups/:id").handler(groupEndpoint::patch);
            scimRouter.delete("/Groups/:id").handler(groupEndpoint::delete);

            // error handler
            scimRouter.route().failureHandler(new ErrorHandler());

            // mount SCIM router
            router.mountSubRouter(path(), scimRouter);
        }
    }

    @Override
    public String path() {
        return "/scim";
    }

    private boolean isSCIMEnabled() {
        return domain.getScim() != null && domain.getScim().isEnabled();
    }
}
