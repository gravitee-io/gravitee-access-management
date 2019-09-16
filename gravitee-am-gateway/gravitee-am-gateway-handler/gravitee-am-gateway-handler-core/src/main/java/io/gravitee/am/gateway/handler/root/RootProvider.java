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
package io.gravitee.am.gateway.handler.root;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.endpoint.ErrorEndpoint;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.PolicyChainHandler;
import io.gravitee.am.gateway.handler.root.resources.auth.handler.FormLoginHandler;
import io.gravitee.am.gateway.handler.root.resources.auth.handler.SocialAuthHandler;
import io.gravitee.am.gateway.handler.root.resources.auth.provider.SocialAuthenticationProvider;
import io.gravitee.am.gateway.handler.root.resources.endpoint.login.LoginCallbackEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.login.LoginEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.logout.LogoutEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.password.ForgotPasswordEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.password.ForgotPasswordSubmissionEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.password.ResetPasswordEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.password.ResetPasswordSubmissionEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.register.RegisterConfirmationEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.register.RegisterConfirmationSubmissionEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.register.RegisterEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.register.RegisterSubmissionEndpoint;
import io.gravitee.am.gateway.handler.root.resources.handler.client.ClientRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.error.ErrorHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.*;
import io.gravitee.am.gateway.handler.root.resources.handler.user.PasswordPolicyRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserTokenRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.password.ForgotPasswordSubmissionRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.password.ResetPasswordRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.password.ResetPasswordSubmissionRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterConfirmationRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterConfirmationSubmissionRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterSubmissionRequestParseHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.gravitee.common.service.AbstractService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RootProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Router router;

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private UserAuthProvider userAuthProvider;

    @Autowired
    private ThymeleafTemplateEngine thymeleafTemplateEngine;

    @Autowired
    private PasswordValidator passwordValidator;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private SessionHandler sessionHandler;

    @Autowired
    private CookieHandler cookieHandler;

    @Autowired
    private CSRFHandler csrfHandler;

    @Autowired
    @Qualifier("managementUserService")
    private UserService userService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private PolicyChainHandler policyChainHandler;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create the root router
        final Router rootRouter = Router.router(vertx);

        // social authentication handler
        final AuthProvider socialAuthProvider = new SocialAuthenticationProvider(identityProviderManager, userAuthenticationManager);

        // router user management handler
        Handler<RoutingContext> userTokenRequestParseHandler = new UserTokenRequestParseHandler(userService);
        Handler<RoutingContext> clientRequestParseHandler = new ClientRequestParseHandler(clientSyncService);
        ((ClientRequestParseHandler) clientRequestParseHandler).setRequired(true);
        Handler<RoutingContext> clientRequestParseHandlerOptional = new ClientRequestParseHandler(clientSyncService);
        Handler<RoutingContext> registerHandler = new RegisterEndpoint(thymeleafTemplateEngine);
        Handler<RoutingContext> registerSubmissionRequestHandler = new RegisterSubmissionRequestParseHandler();
        Handler<RoutingContext> registerSubmissionHandler = new RegisterSubmissionEndpoint(userService, domain);
        Handler<RoutingContext> registerConfirmationRequestParseHandler = new RegisterConfirmationRequestParseHandler(userService);
        Handler<RoutingContext> registerConfirmationEndpointHandler = new RegisterConfirmationEndpoint(thymeleafTemplateEngine);
        Handler<RoutingContext> registerConfirmationSubmissionRequestParseHandler = new RegisterConfirmationSubmissionRequestParseHandler();
        Handler<RoutingContext> registerConfirmationSubmissionEndpointHandler = new RegisterConfirmationSubmissionEndpoint(userService);
        Handler<RoutingContext> forgotPasswordEndpointHandler = new ForgotPasswordEndpoint(thymeleafTemplateEngine);
        Handler<RoutingContext> forgotPasswordSubmissionRequestParseHandler = new ForgotPasswordSubmissionRequestParseHandler();
        Handler<RoutingContext> forgotPasswordSubmissionEndpointHandler = new ForgotPasswordSubmissionEndpoint(userService, domain);
        Handler<RoutingContext> resetPasswordRequestParseHandler = new ResetPasswordRequestParseHandler(userService);
        Handler<RoutingContext> resetPasswordHandler = new ResetPasswordEndpoint(thymeleafTemplateEngine);
        Handler<RoutingContext> resetPasswordSubmissionRequestParseHandler = new ResetPasswordSubmissionRequestParseHandler();
        Handler<RoutingContext> resetPasswordSubmissionHandler = new ResetPasswordSubmissionEndpoint(userService);
        Handler<RoutingContext> passwordPolicyRequestParseHandler = new PasswordPolicyRequestParseHandler(passwordValidator);

        // body handler
        bodyHandler(rootRouter);

        // static handler
        staticHandler(rootRouter);

        // session cookie handler
        sessionAndCookieHandler(rootRouter);

        // CSRF handler
        csrfHandler(rootRouter);

        // Root policy chain handler
        rootRouter.route().handler(policyChainHandler.create(ExtensionPoint.ROOT));

        // login route
        rootRouter.get("/login")
                .handler(new LoginRequestParseHandler())
                .handler(clientRequestParseHandler)
                .handler(new LoginErrorHandler())
                .handler(new LoginEndpoint(thymeleafTemplateEngine, domain, identityProviderManager));
        rootRouter.post("/login")
                .handler(FormLoginHandler.create(userAuthProvider));

        // oauth 2.0 login callback route
        Handler<RoutingContext> loginCallbackParseHandler = new LoginCallbackParseHandler(clientSyncService, identityProviderManager);
        Handler<RoutingContext> loginCallbackOpenIDConnectFlowHandler = new LoginCallbackOpenIDConnectFlowHandler(thymeleafTemplateEngine);
        Handler<RoutingContext> loginCallbackFailureHandler = new LoginCallbackFailureHandler();
        Handler<RoutingContext> socialAuthHandler = SocialAuthHandler.create(socialAuthProvider);
        Handler<RoutingContext> loginCallbackEndpoint = new LoginCallbackEndpoint();
        rootRouter.get("/login/callback")
                .handler(loginCallbackParseHandler)
                .handler(loginCallbackOpenIDConnectFlowHandler)
                .handler(socialAuthHandler)
                .handler(loginCallbackEndpoint)
                .failureHandler(loginCallbackFailureHandler);
        rootRouter.post("/login/callback")
                .handler(loginCallbackParseHandler)
                .handler(socialAuthHandler)
                .handler(loginCallbackEndpoint)
                .failureHandler(loginCallbackFailureHandler);

        // logout route
        rootRouter.route("/logout").handler(LogoutEndpoint.create(domain, auditService));

        // error route
        rootRouter.route(HttpMethod.GET, "/error")
                .handler(new ErrorEndpoint(domain.getId(), thymeleafTemplateEngine, clientSyncService));

        // mount forgot/reset registration pages only if the option is enabled
        if (domain.getLoginSettings() != null && domain.getLoginSettings().isRegisterEnabled()) {
            rootRouter.route(HttpMethod.GET, "/register")
                    .handler(clientRequestParseHandler)
                    .handler(registerHandler);
            rootRouter.route(HttpMethod.POST, "/register")
                    .handler(registerSubmissionRequestHandler)
                    .handler(passwordPolicyRequestParseHandler)
                    .handler(registerSubmissionHandler);
        }

        rootRouter.route(HttpMethod.GET,"/confirmRegistration")
                .handler(registerConfirmationRequestParseHandler)
                .handler(clientRequestParseHandlerOptional)
                .handler(registerConfirmationEndpointHandler);
        rootRouter.route(HttpMethod.POST, "/confirmRegistration")
                .handler(registerConfirmationSubmissionRequestParseHandler)
                .handler(userTokenRequestParseHandler)
                .handler(passwordPolicyRequestParseHandler)
                .handler(registerConfirmationSubmissionEndpointHandler);

        // mount forgot/reset password pages only if the option is enabled
        if (domain.getLoginSettings() != null && domain.getLoginSettings().isForgotPasswordEnabled()) {
            rootRouter.route(HttpMethod.GET, "/forgotPassword")
                    .handler(clientRequestParseHandler)
                    .handler(forgotPasswordEndpointHandler);
            rootRouter.route(HttpMethod.POST, "/forgotPassword")
                    .handler(forgotPasswordSubmissionRequestParseHandler)
                    .handler(clientRequestParseHandler)
                    .handler(forgotPasswordSubmissionEndpointHandler);
            rootRouter.route(HttpMethod.GET, "/resetPassword")
                    .handler(resetPasswordRequestParseHandler)
                    .handler(clientRequestParseHandlerOptional)
                    .handler(resetPasswordHandler);
            rootRouter.route(HttpMethod.POST, "/resetPassword")
                    .handler(resetPasswordSubmissionRequestParseHandler)
                    .handler(userTokenRequestParseHandler)
                    .handler(passwordPolicyRequestParseHandler)
                    .handler(resetPasswordSubmissionHandler);
        }

        // error handler
        errorHandler(rootRouter);

        // mount root router
        router.mountSubRouter(path(), rootRouter);
    }

    @Override
    public String path() {
        return "/";
    }

    private void sessionAndCookieHandler(Router router) {
        // Login endpoint
        router.route("/login")
                .handler(cookieHandler)
                .handler(sessionHandler);
        router
                .route("/login/callback")
                .handler(cookieHandler)
                .handler(sessionHandler);

        // Logout endpoint
        router
                .route("/logout")
                .handler(cookieHandler)
                .handler(sessionHandler);

        // Registration confirmation endpoint
        router
                .route("/confirmRegistration")
                .handler(cookieHandler)
                .handler(sessionHandler);

        // Reset password endpoint
        router
                .route("/resetPassword")
                .handler(cookieHandler)
                .handler(sessionHandler);
    }

    private void csrfHandler(Router router) {
        router.route("/login").handler(csrfHandler);
        router.route("/resetPassword").handler(csrfHandler);
        router.route("/confirmRegistration").handler(csrfHandler);
    }

    private void staticHandler(Router router) {
        router.route().handler(StaticHandler.create());
    }

    private void bodyHandler(Router router) {
        router.route().handler(BodyHandler.create());
    }

    private void errorHandler(Router router) {
        Handler<RoutingContext> errorHandler = new ErrorHandler("/" + domain.getPath() + "/error");
        router.route("/login").failureHandler(errorHandler);
        router.route("/forgotPassword").failureHandler(errorHandler);
    }
}
