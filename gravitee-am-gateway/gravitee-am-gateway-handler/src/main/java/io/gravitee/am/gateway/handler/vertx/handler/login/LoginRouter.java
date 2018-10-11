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
package io.gravitee.am.gateway.handler.vertx.handler.login;

import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.vertx.auth.handler.FormLoginHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.OAuth2ClientAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.provider.OAuth2ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.handler.login.endpoint.LoginCallbackEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.login.endpoint.LoginEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.login.endpoint.LoginRequestParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.login.endpoint.LogoutEndpointHandler;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.model.Domain;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.templ.ThymeleafTemplateEngine;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginRouter {

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private ClientService clientService;

    @Autowired
    private ThymeleafTemplateEngine thymeleafTemplateEngine;

    @Autowired
    private Domain domain;

    @Autowired
    private Vertx vertx;

    @Autowired
    private UserService userService;

    public Router route(AuthProvider userAuthProvider) {
        // Create the login router
        final Router router = Router.router(vertx);

        // create authentication handlers
        final AuthProvider identityProviderAuthProvider = new AuthProvider(new OAuth2ClientAuthenticationProvider(identityProviderManager, userService));

        // login handler
        router.get("/login")
                .handler(new LoginRequestParseHandler(clientService))
                .handler(new LoginEndpointHandler(thymeleafTemplateEngine, domain, identityProviderManager));
        router.post("/login").handler(FormLoginHandler.create(userAuthProvider.getDelegate()));

        // oauth 2.0 login callback handler
        router.get("/login/callback")
                .handler(OAuth2ClientAuthHandler.create(identityProviderAuthProvider.getDelegate(), identityProviderManager))
                .handler(new LoginCallbackEndpointHandler());

        // logout handler
        router.route("/logout").handler(LogoutEndpointHandler.create());

        return router;
    }
}
