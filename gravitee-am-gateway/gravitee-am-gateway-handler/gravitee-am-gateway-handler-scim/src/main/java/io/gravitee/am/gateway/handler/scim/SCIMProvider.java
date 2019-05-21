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
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.scim.resources.ErrorHandler;
import io.gravitee.am.gateway.handler.scim.resources.configuration.ServiceProviderConfigurationEndpointHandler;
import io.gravitee.am.gateway.handler.scim.resources.groups.*;
import io.gravitee.am.gateway.handler.scim.resources.users.*;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.am.gateway.handler.scim.service.ServiceProviderConfigService;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.gravitee.common.service.AbstractService;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configurable
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
    private PasswordValidator passwordValidator;

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
            scimRouter.route().handler(OAuth2AuthHandler.create(oAuth2AuthProvider, "scim"));

            // Users resource
            ListUserEndpointHandler listUserEndpointHandler = ListUserEndpointHandler.create(userService);
            listUserEndpointHandler.setObjectMapper(objectMapper);
            GetUserEndpointHandler getUserEndpointHandler = GetUserEndpointHandler.create(userService);
            getUserEndpointHandler.setObjectMapper(objectMapper);
            CreateUserEndpointHandler createUserEndpointHandler = CreateUserEndpointHandler.create(userService);
            createUserEndpointHandler.setObjectMapper(objectMapper);
            createUserEndpointHandler.setPasswordValidator(passwordValidator);
            UpdateUserEndpointHandler updateUserEndpointHandler = UpdateUserEndpointHandler.create(userService);
            updateUserEndpointHandler.setObjectMapper(objectMapper);
            updateUserEndpointHandler.setPasswordValidator(passwordValidator);
            DeleteUserEndpointHandler deleteUserEndpointHandler = DeleteUserEndpointHandler.create(userService);

            scimRouter.get("/Users").handler(listUserEndpointHandler);
            scimRouter.get("/Users/:id").handler(getUserEndpointHandler);
            scimRouter.post("/Users").handler(createUserEndpointHandler);
            scimRouter.put("/Users/:id").handler(updateUserEndpointHandler);
            scimRouter.delete("/Users/:id").handler(deleteUserEndpointHandler);

            // Groups resource
            ListGroupEndpointHandler listGroupEndpointHandler = ListGroupEndpointHandler.create(groupService);
            listGroupEndpointHandler.setObjectMapper(objectMapper);
            GetGroupEndpointHandler getGroupEndpointHandler = GetGroupEndpointHandler.create(groupService);
            getGroupEndpointHandler.setObjectMapper(objectMapper);
            CreateGroupEndpointHandler createGroupEndpointHandler = CreateGroupEndpointHandler.create(groupService);
            createGroupEndpointHandler.setObjectMapper(objectMapper);
            UpdateGroupEndpointHandler updateGroupEndpointHandler = UpdateGroupEndpointHandler.create(groupService);
            updateGroupEndpointHandler.setObjectMapper(objectMapper);
            DeleteGroupEndpointHandler deleteGroupEndpointHandler = DeleteGroupEndpointHandler.create(groupService);

            scimRouter.get("/Groups").handler(listGroupEndpointHandler);
            scimRouter.get("/Groups/:id").handler(getGroupEndpointHandler);
            scimRouter.post("/Groups").handler(createGroupEndpointHandler);
            scimRouter.put("/Groups/:id").handler(updateGroupEndpointHandler);
            scimRouter.delete("/Groups/:id").handler(deleteGroupEndpointHandler);

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
