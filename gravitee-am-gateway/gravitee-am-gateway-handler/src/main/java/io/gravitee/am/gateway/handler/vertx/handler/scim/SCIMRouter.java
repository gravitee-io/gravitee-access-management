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
package io.gravitee.am.gateway.handler.vertx.handler.scim;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.scim.GroupService;
import io.gravitee.am.gateway.handler.scim.ServiceProviderConfigService;
import io.gravitee.am.gateway.handler.scim.UserService;
import io.gravitee.am.gateway.handler.vertx.handler.scim.endpoint.configuration.ServiceProviderConfigurationEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.scim.endpoint.groups.*;
import io.gravitee.am.gateway.handler.vertx.handler.scim.endpoint.users.*;
import io.gravitee.am.gateway.handler.vertx.handler.scim.handler.BearerTokensParseHandler;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SCIMRouter {

    @Autowired
    private Vertx vertx;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("scimUserService")
    private UserService userService;

    @Autowired
    @Qualifier("scimGroupService")
    private GroupService groupService;

    @Autowired
    private ServiceProviderConfigService serviceProviderConfigService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private PasswordValidator passwordValidator;

    public Router route() {
        // Create the SCIM router
        final Router router = Router.router(vertx);

        // Declare SCIM routes
        // see <a href="https://tools.ietf.org/html/rfc7644#section-3.2">3.2. SCIM Endpoints and HTTP Methods</a>

        // Service Provider configuration
        ServiceProviderConfigurationEndpointHandler serviceProviderConfigurationEndpointHandler = ServiceProviderConfigurationEndpointHandler.create(serviceProviderConfigService);
        serviceProviderConfigurationEndpointHandler.setObjectMapper(objectMapper);
        router.get("/ServiceProviderConfig").handler(serviceProviderConfigurationEndpointHandler);

        // SCIM resources routes are OAuth 2.0 secured
        router.route().handler(new BearerTokensParseHandler(jwtService, tokenService, clientSyncService));

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

        router.get("/Users").handler(listUserEndpointHandler);
        router.get("/Users/:id").handler(getUserEndpointHandler);
        router.post("/Users").handler(createUserEndpointHandler);
        router.put("/Users/:id").handler(updateUserEndpointHandler);
        router.delete("/Users/:id").handler(deleteUserEndpointHandler);

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

        router.get("/Groups").handler(listGroupEndpointHandler);
        router.get("/Groups/:id").handler(getGroupEndpointHandler);
        router.post("/Groups").handler(createGroupEndpointHandler);
        router.put("/Groups/:id").handler(updateGroupEndpointHandler);
        router.delete("/Groups/:id").handler(deleteGroupEndpointHandler);

        return router;
    }

}
