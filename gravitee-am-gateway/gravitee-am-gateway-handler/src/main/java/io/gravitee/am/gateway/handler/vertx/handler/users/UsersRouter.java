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
package io.gravitee.am.gateway.handler.vertx.handler.users;

import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.gateway.handler.vertx.handler.users.endpoint.consents.UserConsentEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.users.endpoint.consents.UserConsentsEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.users.handler.AuthTokenParseHandler;
import io.gravitee.am.model.Domain;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UsersRouter {

    @Autowired
    private Vertx vertx;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private Domain domain;

    public Router route() {
        // Create the Users router
        final Router router = Router.router(vertx);

        // Declare Users routes
        final UserConsentsEndpointHandler userConsentsHandler = new UserConsentsEndpointHandler(userService, clientSyncService, domain);
        final UserConsentEndpointHandler userConsentHandler = new UserConsentEndpointHandler(userService, clientSyncService, domain);

        // User consent routes
        router.routeWithRegex(".*consents.*")
                .pathRegex("\\/(?<userId>[^\\/]+)\\/([^\\/]+)")
                .handler(AuthTokenParseHandler.create(jwtService, tokenService, clientSyncService, "consent_admin"));
        router.get("/:userId/consents")
                .handler(userConsentsHandler::list);
        router.delete("/:userId/consents")
                .handler(userConsentsHandler::revoke);
        router.get("/:userId/consents/:consentId")
                .handler(userConsentHandler::get);
        router.delete("/:userId/consents/:consentId")
                .handler(userConsentHandler::revoke);

        return router;
    }
}
