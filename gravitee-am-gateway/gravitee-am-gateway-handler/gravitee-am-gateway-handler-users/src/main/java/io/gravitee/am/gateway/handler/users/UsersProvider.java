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
package io.gravitee.am.gateway.handler.users;

import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.users.resources.consents.UserConsentEndpointHandler;
import io.gravitee.am.gateway.handler.users.resources.consents.UserConsentsEndpointHandler;
import io.gravitee.am.gateway.handler.users.service.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.common.service.AbstractService;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UsersProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private UserService userService;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private Router router;

    @Autowired
    private OAuth2AuthProvider oAuth2AuthProvider;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Create the Users router
        final Router usersRouter = Router.router(vertx);

        final UserConsentsEndpointHandler userConsentsHandler = new UserConsentsEndpointHandler(userService, clientSyncService, domain);
        final UserConsentEndpointHandler userConsentHandler = new UserConsentEndpointHandler(userService, clientSyncService, domain);
        final OAuth2AuthHandler oAuth2AuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider, "consent_admin");
        oAuth2AuthHandler.extractToken(true);
        oAuth2AuthHandler.selfResource(true, "userId");

        // user consent routes
        usersRouter.routeWithRegex(".*consents.*")
                .pathRegex("\\/(?<userId>[^\\/]+)\\/([^\\/]+)")
                .handler(oAuth2AuthHandler);
        usersRouter.get("/:userId/consents")
                .handler(userConsentsHandler::list);
        usersRouter.delete("/:userId/consents")
                .handler(userConsentsHandler::revoke);
        usersRouter.get("/:userId/consents/:consentId")
                .handler(userConsentHandler::get);
        usersRouter.delete("/:userId/consents/:consentId")
                .handler(userConsentHandler::revoke);

        // error handler
        usersRouter.route().failureHandler(new ErrorHandler());

        router.mountSubRouter(path(), usersRouter);
    }

    @Override
    public String path() {
        return "/users";
    }
}
