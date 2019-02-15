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
package io.gravitee.am.gateway.handler.vertx.handler.root;

import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.gateway.handler.vertx.auth.handler.FormLoginHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.OAuth2ClientAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.provider.OAuth2ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.ErrorHandlerEndpoint;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.ClientRequestParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.login.LoginCallbackEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.login.LoginCallbackParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.login.LoginEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.login.LoginRequestParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.logout.LogoutEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.PasswordPolicyRequestParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.UserTokenRequestParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.password.*;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.register.*;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.ThymeleafTemplateEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RootRouter {

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    @Qualifier("managementUserService")
    private UserService userService;

    @Autowired
    private ThymeleafTemplateEngine thymeleafTemplateEngine;

    @Autowired
    private Domain domain;

    @Autowired
    private Vertx vertx;

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private PasswordValidator passwordValidator;

    public Router route(AuthProvider userAuthProvider) {
        // create the root router
        final Router router = Router.router(vertx);

        // create OAuth 2.0 Client authentication handler
        final AuthProvider identityProviderAuthProvider = new AuthProvider(new OAuth2ClientAuthenticationProvider(identityProviderManager, userAuthenticationManager));

        // create user management handler
        Handler<RoutingContext> userTokenRequestParseHandler = new UserTokenRequestParseHandler(userService);
        Handler<RoutingContext> clientRequestParseHandler = new ClientRequestParseHandler(clientSyncService);
        Handler<RoutingContext> registerHandler = new RegisterEndpointHandler(thymeleafTemplateEngine);
        Handler<RoutingContext> registerSubmissionRequestHandler = new RegisterSubmissionRequestParseHandler();
        Handler<RoutingContext> registerSubmissionHandler = new RegisterSubmissionEndpointHandler(userService);
        Handler<RoutingContext> registerConfirmationRequestParseHandler = new RegisterConfirmationRequestParseHandler(userService);
        Handler<RoutingContext> registerConfirmationEndpointHandler = new RegisterConfirmationEndpointHandler(thymeleafTemplateEngine);
        Handler<RoutingContext> registerConfirmationSubmissionRequestParseHandler = new RegisterConfirmationSubmissionRequestParseHandler();
        Handler<RoutingContext> registerConfirmationSubmissionEndpointHandler = new RegisterConfirmationSubmissionEndpointHandler(userService);
        Handler<RoutingContext> forgotPasswordEndpointHandler = new ForgotPasswordEndpointHandler(thymeleafTemplateEngine);
        Handler<RoutingContext> forgotPasswordSubmissionRequestParseHandler = new ForgotPasswordSubmissionRequestParseHandler();
        Handler<RoutingContext> forgotPasswordSubmissionEndpointHandler = new ForgotPasswordSubmissionEndpointHandler(userService);
        Handler<RoutingContext> resetPasswordRequestParseHandler = new ResetPasswordRequestParseHandler(userService);
        Handler<RoutingContext> resetPasswordHandler = new ResetPasswordEndpointHandler(thymeleafTemplateEngine);
        Handler<RoutingContext> resetPasswordSubmissionRequestParseHandler = new ResetPasswordSubmissionRequestParseHandler();
        Handler<RoutingContext> resetPasswordSubmissionHandler = new ResetPasswordSubmissionEndpointHandler(userService);
        Handler<RoutingContext> passwordPolicyRequestParseHandler = new PasswordPolicyRequestParseHandler(passwordValidator);

        // login route
        router.get("/login")
                .handler(new LoginRequestParseHandler())
                .handler(clientRequestParseHandler)
                .handler(new LoginEndpointHandler(thymeleafTemplateEngine, domain, identityProviderManager));
        router.post("/login").handler(FormLoginHandler.create(userAuthProvider.getDelegate()));

        // oauth 2.0 login callback route
        router.get("/login/callback")
                .handler(new LoginCallbackParseHandler(clientSyncService))
                .handler(OAuth2ClientAuthHandler.create(identityProviderAuthProvider.getDelegate(), identityProviderManager))
                .handler(new LoginCallbackEndpointHandler());

        // logout route
        router.route("/logout").handler(LogoutEndpointHandler.create());

        // error route
        router.route(HttpMethod.GET, "/error")
                .handler(new ErrorHandlerEndpoint(thymeleafTemplateEngine));

        // mount forgot/reset registration pages only if the option is enabled
        if (domain.getLoginSettings() != null && domain.getLoginSettings().isRegisterEnabled()) {
            router.route(HttpMethod.GET, "/register")
                    .handler(clientRequestParseHandler)
                    .handler(registerHandler);
            router.route(HttpMethod.POST, "/register")
                    .handler(registerSubmissionRequestHandler)
                    .handler(passwordPolicyRequestParseHandler)
                    .handler(registerSubmissionHandler);
        }

        router.route(HttpMethod.GET,"/confirmRegistration")
                .handler(registerConfirmationRequestParseHandler)
                .handler(registerConfirmationEndpointHandler);
        router.route(HttpMethod.POST, "/confirmRegistration")
                .handler(registerConfirmationSubmissionRequestParseHandler)
                .handler(userTokenRequestParseHandler)
                .handler(passwordPolicyRequestParseHandler)
                .handler(registerConfirmationSubmissionEndpointHandler);

        // mount forgot/reset password pages only if the option is enabled
        if (domain.getLoginSettings() != null && domain.getLoginSettings().isForgotPasswordEnabled()) {
            router.route(HttpMethod.GET, "/forgotPassword")
                    .handler(clientRequestParseHandler)
                    .handler(forgotPasswordEndpointHandler);
            router.route(HttpMethod.POST, "/forgotPassword")
                    .handler(forgotPasswordSubmissionRequestParseHandler)
                    .handler(clientRequestParseHandler)
                    .handler(forgotPasswordSubmissionEndpointHandler);
            router.route(HttpMethod.GET, "/resetPassword")
                    .handler(resetPasswordRequestParseHandler)
                    .handler(resetPasswordHandler);
            router.route(HttpMethod.POST, "/resetPassword")
                    .handler(resetPasswordSubmissionRequestParseHandler)
                    .handler(userTokenRequestParseHandler)
                    .handler(passwordPolicyRequestParseHandler)
                    .handler(resetPasswordSubmissionHandler);
        }
        return router;
    }
}
