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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.endpoint.ErrorEndpoint;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.AuthenticationFlowContextHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.CSPHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.PolicyChainHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.SSOSessionHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.XFrameHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.XSSHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSessionHandler;
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.auth.handler.SocialAuthHandler;
import io.gravitee.am.gateway.handler.root.resources.auth.provider.SocialAuthenticationProvider;
import io.gravitee.am.gateway.handler.root.resources.endpoint.identifierfirst.IdentifierFirstLoginEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.login.LoginCallbackEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.login.LoginEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.login.LoginPostEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.login.LoginSSOPOSTEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.logout.LogoutCallbackEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.logout.LogoutEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.mfa.MFAChallengeAlternativesEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.mfa.MFAChallengeEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.mfa.MFAChallengeFailureHandler;
import io.gravitee.am.gateway.handler.root.resources.endpoint.mfa.MFAEnrollEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.mfa.MFARecoveryCodeEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.password.ForgotPasswordEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.password.ForgotPasswordSubmissionEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.password.ResetPasswordEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.password.ResetPasswordSubmissionEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.register.*;
import io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn.WebAuthnLoginCredentialsEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn.WebAuthnLoginEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn.WebAuthnLoginPostEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn.WebAuthnRegisterCredentialsEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn.WebAuthnRegisterEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn.WebAuthnRegisterPostEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn.WebAuthnRegisterSuccessEndpoint;
import io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn.WebAuthnResponseEndpoint;
import io.gravitee.am.gateway.handler.root.resources.handler.transactionid.TransactionIdHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.ConditionalBodyHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.LocaleHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.botdetection.BotDetectionHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.client.ClientRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.common.RedirectUriValidationHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.consent.DataConsentHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.error.ErrorHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.geoip.GeoIpHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginCallbackDeviceIdHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginCallbackFailureHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginCallbackOpenIDConnectFlowHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginCallbackParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginFailureHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginFormHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginHideFormHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginNegotiateAuthenticationHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginPostWebAuthnHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSelectionRuleHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.loginattempt.LoginAttemptHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.mfa.MFAChallengeUserHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.rememberdevice.DeviceIdentifierHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.rememberdevice.RememberDeviceSettingsHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.PasswordPolicyRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserTokenRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.activity.UserActivityHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.password.*;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterAccessHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterConfirmationRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterConfirmationSubmissionRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterFailureHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterProcessHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterSubmissionRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterVerifyRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnAccessHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnEnforcePasswordHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnLoginHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnRegisterHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnRememberDeviceHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnResponseHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.RateLimiterService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.am.service.VerifyAttemptService;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.common.service.AbstractService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.rxjava3.ext.web.handler.CSRFHandler;
import io.vertx.rxjava3.ext.web.handler.StaticHandler;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import static io.vertx.core.http.HttpMethod.GET;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RootProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    public static final String PATH_LOGIN = "/login";
    public static final String PATH_LOGIN_CALLBACK = "/login/callback";
    public static final String PATH_LOGIN_SSO_POST = "/login/SSO/POST";
    public static final String PATH_LOGIN_SSO_SPNEGO = "/login/SSO/SPNEGO";
    public static final String PATH_MFA_ENROLL = "/mfa/enroll";
    public static final String PATH_MFA_CHALLENGE = "/mfa/challenge";
    public static final String PATH_MFA_CHALLENGE_ALTERNATIVES = "/mfa/challenge/alternatives";
    public static final String PATH_MFA_RECOVERY_CODE = "/mfa/recovery_code";
    public static final String PATH_LOGOUT = "/logout";
    public static final String PATH_LOGOUT_CALLBACK = "/logout/callback";
    public static final String PATH_REGISTER = "/register";
    public static final String PATH_CONFIRM_REGISTRATION = "/confirmRegistration";
    public static final String PATH_RESET_PASSWORD = "/resetPassword";
    public static final String PATH_WEBAUTHN_REGISTER = "/webauthn/register";
    public static final String PATH_WEBAUTHN_REGISTER_SUCCESS = "/webauthn/register/success";
    public static final String PATH_WEBAUTHN_REGISTER_CREDENTIALS = "/webauthn/register/credentials";
    public static final String PATH_VERIFY_REGISTRATION = "/verifyRegistration";
    public static final String PATH_WEBAUTHN_RESPONSE = "/webauthn/response";
    public static final String PATH_WEBAUTHN_LOGIN = "/webauthn/login";
    public static final String PATH_WEBAUTHN_LOGIN_CREDENTIALS = "/webauthn/login/credentials";
    public static final String PATH_FORGOT_PASSWORD = "/forgotPassword";
    public static final String PATH_IDENTIFIER_FIRST_LOGIN = "/login/identifier";
    public static final String PATH_ERROR = "/error";
    private static final String PASSWORD_HISTORY = "/passwordHistory";

    @Value("${handlers.request.transaction.header:X-Gravitee-Transaction-Id}")
    private String transactionHeader;

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
    private PasswordService passwordService;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private CookieSessionHandler sessionHandler;

    @Autowired
    private SSOSessionHandler ssoSessionHandler;

    @Autowired
    private CookieHandler cookieHandler;

    @Autowired
    private CSRFHandler csrfHandler;

    @Autowired
    private CSPHandler cspHandler;

    @Autowired
    private XFrameHandler xframeHandler;

    @Autowired
    private XSSHandler xssHandler;

    @Autowired
    @Qualifier("managementUserService")
    private UserService userService;

    @Autowired
    private PolicyChainHandler policyChainHandler;

    @Autowired
    private FactorManager factorManager;

    @Autowired
    private WebAuthn webAuthn;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private CertificateManager certificateManager;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Autowired
    private Environment environment;

    @Autowired
    private BotDetectionManager botDetectionManager;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private DeviceIdentifierManager deviceIdentifierManager;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private WebClient webClient;

    @Autowired
    public ObjectMapper objectMapper;

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private FactorService factorService;

    @Autowired
    private GatewayMetricProvider gatewayMetricProvider;

    @Autowired
    @Qualifier("gwMessageResolver")
    private GraviteeMessageResolver messageResolver;

    @Autowired
    private WebAuthnCookieService webAuthnCookieService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private PasswordHistoryService passwordHistoryService;

    @Autowired
    private VerifyAttemptService verifyAttemptService;

    @Autowired
    private EmailService emailService;
    @Autowired
    private AuditService auditService;


    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create the root router
        final Router rootRouter = Router.router(vertx);

        // body handler
        bodyHandler(rootRouter);

        // static handler
        staticHandler(rootRouter);

        // session cookie handler
        sessionAndCookieHandler(rootRouter);

        // Gravitee Context handler
        authFlowContextHandler(rootRouter);

        // CSRF handler
        csrfHandler(rootRouter);

        // CSP Handler
        cspHandler(rootRouter);

        xFrameHandler(rootRouter);

        xssHandler(rootRouter);

        // common handler
        Handler<RoutingContext> userTokenRequestParseHandler = new UserTokenRequestParseHandler(userService);
        Handler<RoutingContext> clientRequestParseHandler = new ClientRequestParseHandler(clientSyncService).setRequired(true);
        Handler<RoutingContext> clientRequestParseHandlerOptional = new ClientRequestParseHandler(clientSyncService);
        Handler<RoutingContext> passwordPolicyRequestParseHandler = new PasswordPolicyRequestParseHandler(passwordService, domain);
        Handler<RoutingContext> botDetectionHandler = new BotDetectionHandler(domain, botDetectionManager);
        Handler<RoutingContext> dataConsentHandler = new DataConsentHandler(environment);
        Handler<RoutingContext> geoIpHandler = new GeoIpHandler(userActivityService, vertx.eventBus());
        Handler<RoutingContext> loginAttemptHandler = new LoginAttemptHandler(domain, identityProviderManager, loginAttemptService, userActivityService);
        Handler<RoutingContext> rememberDeviceSettingsHandler = new RememberDeviceSettingsHandler();
        Handler<RoutingContext> deviceIdentifierHandler = new DeviceIdentifierHandler(deviceService);
        Handler<RoutingContext> userActivityHandler = new UserActivityHandler(userActivityService);
        Handler<RoutingContext> localeHandler = new LocaleHandler(messageResolver);
        Handler<RoutingContext> loginPostWebAuthnHandler = new LoginPostWebAuthnHandler(webAuthnCookieService);
        Handler<RoutingContext> redirectUriValidationHandler = new RedirectUriValidationHandler(domain);

        // Root policy chain handler
        rootRouter.route()
                // client_id is useful at root level in order to handle properly the ROOT app flow
                // but if the client_id is unknown or invalid (not only missing) the rootRouter will throw an error that will prevent to propagate the call to the right route
                // for instance, the OAuthProvider will not execute the /oauth/authorize and there will have 500 ERROR instead of "missing client_id" OAuth 2.0 error
                // See https://github.com/gravitee-io/issues/issues/5035
                .handler(new ClientRequestParseHandler(clientSyncService).setContinueOnError(true))
                .handler(dataConsentHandler)
                .handler(geoIpHandler)
                .handler(policyChainHandler.create(ExtensionPoint.ROOT));

        // Identifier First Login route
        rootRouter.get(PATH_IDENTIFIER_FIRST_LOGIN)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(new LoginSocialAuthenticationHandler(identityProviderManager, jwtService, certificateManager))
                .handler(policyChainHandler.create(ExtensionPoint.PRE_LOGIN_IDENTIFIER))
                .handler(localeHandler)
                .handler(new IdentifierFirstLoginEndpoint(thymeleafTemplateEngine, domain, botDetectionManager));

        rootRouter.post(PATH_IDENTIFIER_FIRST_LOGIN)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(botDetectionHandler)
                .handler(new LoginSocialAuthenticationHandler(identityProviderManager, jwtService, certificateManager))
                .handler(policyChainHandler.create(ExtensionPoint.POST_LOGIN_IDENTIFIER))
                .handler(new LoginSelectionRuleHandler(true))
                .handler(new IdentifierFirstLoginEndpoint(thymeleafTemplateEngine, domain, botDetectionManager));

        // login route
        rootRouter.get(PATH_LOGIN)
                .handler(clientRequestParseHandler)
                .handler(new LoginSocialAuthenticationHandler(identityProviderManager, jwtService, certificateManager))
                .handler(redirectUriValidationHandler)
                .handler(policyChainHandler.create(ExtensionPoint.PRE_LOGIN))
                .handler(new LoginHideFormHandler(domain))
                .handler(new LoginSelectionRuleHandler(false))
                .handler(localeHandler)
                .handler(new LoginEndpoint(thymeleafTemplateEngine, domain, botDetectionManager, deviceIdentifierManager, userActivityService));

        rootRouter.post(PATH_LOGIN)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(botDetectionHandler)
                .handler(loginAttemptHandler)
                .handler(new LoginFormHandler(userAuthProvider))
                .handler(deviceIdentifierHandler)
                .handler(userActivityHandler)
                .handler(policyChainHandler.create(ExtensionPoint.POST_LOGIN))
                .handler(loginPostWebAuthnHandler)
                .handler(new LoginPostEndpoint());

        rootRouter.route(PATH_LOGIN)
                .failureHandler(new LoginFailureHandler(authenticationFlowContextService, domain, identityProviderManager));

        // logout route
        rootRouter.route(PATH_LOGOUT)
                .handler(new LogoutEndpoint(domain, clientSyncService, jwtService, userService, authenticationFlowContextService, identityProviderManager, certificateManager, webClient));
        rootRouter.route(PATH_LOGOUT_CALLBACK)
                .handler(new LogoutCallbackEndpoint(domain, clientSyncService, jwtService, userService, authenticationFlowContextService, certificateManager));

        // SSO/Social login route
        Handler<RoutingContext> socialAuthHandler = SocialAuthHandler.create(new SocialAuthenticationProvider(userAuthenticationManager, eventManager, identityProviderManager, domain, gatewayMetricProvider));
        Handler<RoutingContext> loginCallbackParseHandler = new LoginCallbackParseHandler(clientSyncService, identityProviderManager, jwtService, certificateManager);
        Handler<RoutingContext> loginCallbackOpenIDConnectFlowHandler = new LoginCallbackOpenIDConnectFlowHandler(thymeleafTemplateEngine);
        Handler<RoutingContext> loginCallbackDeviceIdHandler = new LoginCallbackDeviceIdHandler(thymeleafTemplateEngine, deviceIdentifierManager);
        Handler<RoutingContext> loginCallbackFailureHandler = new LoginCallbackFailureHandler(domain, authenticationFlowContextService, identityProviderManager);
        Handler<RoutingContext> loginCallbackEndpoint = new LoginCallbackEndpoint();
        Handler<RoutingContext> loginSSOPOSTEndpoint = new LoginSSOPOSTEndpoint(thymeleafTemplateEngine);
        rootRouter.get(PATH_LOGIN_CALLBACK)
                .handler(loginCallbackOpenIDConnectFlowHandler)
                .handler(loginCallbackParseHandler)
                .handler(rememberDeviceSettingsHandler)
                .handler(loginCallbackDeviceIdHandler)
                .handler(socialAuthHandler)
                .handler(policyChainHandler.create(ExtensionPoint.POST_LOGIN))
                .handler(loginPostWebAuthnHandler)
                .handler(loginCallbackEndpoint)
                .failureHandler(loginCallbackFailureHandler);
        rootRouter.post(PATH_LOGIN_CALLBACK)
                .handler(loginCallbackOpenIDConnectFlowHandler)
                .handler(loginCallbackParseHandler)
                .handler(socialAuthHandler)
                .handler(deviceIdentifierHandler)
                .handler(policyChainHandler.create(ExtensionPoint.POST_LOGIN))
                .handler(loginPostWebAuthnHandler)
                .handler(loginCallbackEndpoint)
                .failureHandler(loginCallbackFailureHandler);
        rootRouter.get(PATH_LOGIN_SSO_POST)
                .handler(loginSSOPOSTEndpoint);
        rootRouter.get(PATH_LOGIN_SSO_SPNEGO)
                .handler(policyChainHandler.create(ExtensionPoint.PRE_LOGIN))
                .handler(new LoginNegotiateAuthenticationHandler(userAuthProvider, thymeleafTemplateEngine))
                .handler(policyChainHandler.create(ExtensionPoint.POST_LOGIN))
                .handler(new LoginPostEndpoint());

        // MFA route
        Handler<RoutingContext> mfaChallengeUserHandler = new MFAChallengeUserHandler(userService);
        rootRouter.route(PATH_MFA_ENROLL)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(localeHandler)
                .handler(new MFAEnrollEndpoint(factorManager, thymeleafTemplateEngine, userService, domain, applicationContext));
        rootRouter.route(PATH_MFA_CHALLENGE)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(rememberDeviceSettingsHandler)
                .handler(localeHandler)
                .handler(mfaChallengeUserHandler)
                .handler(new MFAChallengeEndpoint(factorManager, userService, thymeleafTemplateEngine, deviceService, applicationContext,
                        domain, credentialService, factorService, rateLimiterService, verifyAttemptService, emailService, auditService))
                .failureHandler(new MFAChallengeFailureHandler(authenticationFlowContextService));
        rootRouter.route(PATH_MFA_CHALLENGE_ALTERNATIVES)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(localeHandler)
                .handler(mfaChallengeUserHandler)
                .handler(new MFAChallengeAlternativesEndpoint(thymeleafTemplateEngine, factorManager, domain));
        rootRouter.route(PATH_MFA_RECOVERY_CODE)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(localeHandler)
                .handler(new MFARecoveryCodeEndpoint(thymeleafTemplateEngine, domain, userService, factorManager, applicationContext));

        // WebAuthn route
        Handler<RoutingContext> webAuthnAccessHandler = new WebAuthnAccessHandler(domain, factorManager);
        Handler<RoutingContext> webAuthnRememberDeviceHandler = new WebAuthnRememberDeviceHandler(webAuthnCookieService, domain);
        rootRouter.get(PATH_WEBAUTHN_REGISTER)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(webAuthnAccessHandler)
                .handler(localeHandler)
                .handler(new WebAuthnRegisterEndpoint(thymeleafTemplateEngine, domain, factorManager));
        rootRouter.post(PATH_WEBAUTHN_REGISTER)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(webAuthnAccessHandler)
                .handler(new WebAuthnRegisterHandler(factorService, factorManager, domain, webAuthn, credentialService))
                .handler(webAuthnRememberDeviceHandler)
                .handler(new WebAuthnRegisterPostEndpoint(domain));
        rootRouter.route(PATH_WEBAUTHN_REGISTER_CREDENTIALS)
                .handler(clientRequestParseHandler)
                .handler(webAuthnAccessHandler)
                .handler(new WebAuthnRegisterCredentialsEndpoint(domain, webAuthn));
        rootRouter.route(PATH_WEBAUTHN_REGISTER_SUCCESS)
                .handler(clientRequestParseHandler)
                .handler(new WebAuthnRegisterSuccessEndpoint(thymeleafTemplateEngine, credentialService, domain));
        rootRouter.get(PATH_WEBAUTHN_LOGIN)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(webAuthnAccessHandler)
                .handler(new LoginSocialAuthenticationHandler(identityProviderManager, jwtService, certificateManager))
                .handler(localeHandler)
                .handler(new WebAuthnEnforcePasswordHandler(domain, webAuthnCookieService))
                .handler(new WebAuthnLoginEndpoint(thymeleafTemplateEngine, domain, deviceIdentifierManager, userActivityService));
        rootRouter.post(PATH_WEBAUTHN_LOGIN)
                .handler(clientRequestParseHandler)
                .handler(redirectUriValidationHandler)
                .handler(webAuthnAccessHandler)
                .handler(new WebAuthnLoginHandler(factorService, factorManager, domain, webAuthn, credentialService, userAuthenticationManager))
                .handler(deviceIdentifierHandler)
                .handler(userActivityHandler)
                .handler(policyChainHandler.create(ExtensionPoint.POST_LOGIN))
                .handler(webAuthnRememberDeviceHandler)
                .handler(new WebAuthnLoginPostEndpoint());
        rootRouter.route(PATH_WEBAUTHN_LOGIN)
                .failureHandler(new LoginFailureHandler(authenticationFlowContextService, domain, identityProviderManager));
        rootRouter.route(PATH_WEBAUTHN_LOGIN_CREDENTIALS)
                .handler(clientRequestParseHandler)
                .handler(webAuthnAccessHandler)
                .handler(new WebAuthnLoginCredentialsEndpoint(webAuthn));
        rootRouter.post(PATH_WEBAUTHN_RESPONSE)
                .handler(clientRequestParseHandler)
                .handler(webAuthnAccessHandler)
                .handler(new WebAuthnResponseHandler(factorService, factorManager, domain, webAuthn, credentialService, userAuthenticationManager))
                .handler(deviceIdentifierHandler)
                .handler(userActivityHandler)
                .handler(new WebAuthnResponseEndpoint());

        // Registration route
        Handler<RoutingContext> registerAccessHandler = new RegisterAccessHandler(domain);
        rootRouter.route(GET, PATH_REGISTER)
                .handler(clientRequestParseHandler)
                .handler(registerAccessHandler)
                .handler(new LoginSocialAuthenticationHandler(identityProviderManager, jwtService, certificateManager))
                .handler(policyChainHandler.create(ExtensionPoint.PRE_REGISTER))
                .handler(localeHandler)
                .handler(new RegisterEndpoint(thymeleafTemplateEngine, domain, botDetectionManager));
        rootRouter.route(HttpMethod.POST, PATH_REGISTER)
                .handler(new RegisterSubmissionRequestParseHandler())
                .handler(clientRequestParseHandlerOptional)
                .handler(botDetectionHandler)
                .handler(registerAccessHandler)
                .handler(passwordPolicyRequestParseHandler)
                .handler(new RegisterProcessHandler(userService, domain))
                .handler(policyChainHandler.create(ExtensionPoint.POST_REGISTER))
                .handler(new RegisterSubmissionEndpoint(environment));
        rootRouter.route(PATH_REGISTER)
                .failureHandler(new RegisterFailureHandler());

        rootRouter.route(GET, PATH_CONFIRM_REGISTRATION)
                .handler(new RegisterConfirmationRequestParseHandler(userService))
                .handler(clientRequestParseHandlerOptional)
                .handler(policyChainHandler.create(ExtensionPoint.PRE_REGISTRATION_CONFIRMATION))
                .handler(localeHandler)
                .handler(new RegisterConfirmationEndpoint(thymeleafTemplateEngine, domain));
        rootRouter.route(HttpMethod.POST, PATH_CONFIRM_REGISTRATION)
                .handler(new RegisterConfirmationSubmissionRequestParseHandler())
                .handler(userTokenRequestParseHandler)
                .handler(passwordPolicyRequestParseHandler)
                .handler(policyChainHandler.create(ExtensionPoint.POST_REGISTRATION_CONFIRMATION))
                .handler(new RegisterConfirmationSubmissionEndpoint(userService, environment));

        // Registration Verify
        rootRouter.route(GET, PATH_VERIFY_REGISTRATION)
                .handler(new RegisterVerifyRequestParseHandler(domain, userService))
                .handler(clientRequestParseHandlerOptional)
                .handler(localeHandler)
                .handler(new RegisterVerifyEndpoint(domain, thymeleafTemplateEngine));
        rootRouter.route(PATH_VERIFY_REGISTRATION)
                .handler(new ErrorHandler(PATH_VERIFY_REGISTRATION));

        // Forgot password route
        final var forgotPasswordAccessHandler = new ForgotPasswordAccessHandler(domain);
        final var resetPasswordFailureHandler = new ErrorHandler(PATH_RESET_PASSWORD);
        rootRouter.route(GET, PATH_FORGOT_PASSWORD)
                .handler(clientRequestParseHandler)
                .handler(forgotPasswordAccessHandler)
                .handler(localeHandler)
                .handler(new ForgotPasswordEndpoint(thymeleafTemplateEngine, domain, botDetectionManager));
        rootRouter.route(HttpMethod.POST, PATH_FORGOT_PASSWORD)
                .handler(new ForgotPasswordSubmissionRequestParseHandler(domain))
                .handler(clientRequestParseHandler)
                .handler(botDetectionHandler)
                .handler(forgotPasswordAccessHandler)
                .handler(new ForgotPasswordSubmissionEndpoint(userService, domain));

        router.route(PATH_FORGOT_PASSWORD).failureHandler(new ErrorHandler(PATH_ERROR, true));

        rootRouter.route(GET, PATH_RESET_PASSWORD)
                .handler(new ResetPasswordRequestParseHandler(userService))
                .handler(clientRequestParseHandlerOptional)
                .handler(userTokenRequestParseHandler)
                .handler(new ResetPasswordOneTimeTokenHandler())
                .handler(localeHandler)
                .handler(policyChainHandler.create(ExtensionPoint.PRE_RESET_PASSWORD))
                .handler(new ResetPasswordEndpoint(thymeleafTemplateEngine, domain));
        rootRouter.route(HttpMethod.POST, PATH_RESET_PASSWORD)
                .handler(new ResetPasswordSubmissionRequestParseHandler())
                .handler(userTokenRequestParseHandler)
                .handler(new ResetPasswordOneTimeTokenHandler())
                .handler(passwordPolicyRequestParseHandler)
                .handler(policyChainHandler.create(ExtensionPoint.POST_RESET_PASSWORD))
                .handler(new ResetPasswordSubmissionEndpoint(userService, environment));
        rootRouter.route(PATH_RESET_PASSWORD)
                .failureHandler(resetPasswordFailureHandler);

        rootRouter.route(HttpMethod.POST, PASSWORD_HISTORY)
                  .handler(clientRequestParseHandlerOptional)
                  .handler(new PasswordHistoryHandler(passwordHistoryService, userService, domain));

        rootRouter.route(HttpMethod.POST, "/passwordValidation")
                  .handler(clientRequestParseHandlerOptional)
                  .handler(new PasswordValidationHandler(passwordService, userService, domain));

        // error route
        rootRouter.route(GET, PATH_ERROR)
                .handler(new ErrorEndpoint(domain, thymeleafTemplateEngine, clientSyncService, jwtService));

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

        // Define cookieHandler once and globally.
        router.route().handler(cookieHandler);

        // Login endpoint
        router.route(PATH_LOGIN)
                .handler(sessionHandler);
        router
                .route(PATH_LOGIN_CALLBACK)
                .handler(sessionHandler);
        router
                .route(PATH_LOGIN_SSO_POST)
                .handler(sessionHandler);
        router
                .route(PATH_LOGIN_SSO_SPNEGO)
                .handler(sessionHandler);

        // MFA endpoint
        router.route(PATH_MFA_ENROLL)
                .handler(sessionHandler)
                .handler(ssoSessionHandler);
        router.route(PATH_MFA_CHALLENGE)
                .handler(sessionHandler)
                .handler(ssoSessionHandler);
        router.route(PATH_MFA_CHALLENGE_ALTERNATIVES)
                .handler(sessionHandler)
                .handler(ssoSessionHandler);
        router.route(PATH_MFA_RECOVERY_CODE)
                .handler(sessionHandler)
                .handler(ssoSessionHandler);

        // Logout endpoint
        router
                .route(PATH_LOGOUT)
                .handler(sessionHandler);
        router
                .route(PATH_LOGOUT_CALLBACK)
                .handler(sessionHandler);

        // Registration confirmation endpoint
        router
                .route(PATH_REGISTER)
                .handler(sessionHandler);
        router
                .route(PATH_CONFIRM_REGISTRATION)
                .handler(sessionHandler);

        // Reset password endpoint
        router
                .route(PATH_RESET_PASSWORD)
                .handler(sessionHandler);

        router
                .route(PATH_FORGOT_PASSWORD)
                .handler(sessionHandler);

        // WebAuthn endpoint
        router
                .route(PATH_WEBAUTHN_REGISTER)
                .handler(sessionHandler);
        router
                .route(PATH_WEBAUTHN_REGISTER_CREDENTIALS)
                .handler(sessionHandler);
        router
                .route(PATH_WEBAUTHN_REGISTER_SUCCESS)
                .handler(sessionHandler);
        router
                .route(PATH_WEBAUTHN_RESPONSE)
                .handler(sessionHandler);
        router
                .route(PATH_WEBAUTHN_LOGIN)
                .handler(sessionHandler);
        router
                .route(PATH_WEBAUTHN_LOGIN_CREDENTIALS)
                .handler(sessionHandler);

        // Identifier First login endpoint
        router
                .route(PATH_IDENTIFIER_FIRST_LOGIN)
                .handler(sessionHandler);

        // Registration Verify confirmation
        router.route(PATH_VERIFY_REGISTRATION)
                .handler(sessionHandler);
    }

    private void authFlowContextHandler(Router router) {
        // Login endpoint
        AuthenticationFlowContextHandler authenticationFlowContextHandler = new AuthenticationFlowContextHandler(authenticationFlowContextService, environment);
        TransactionIdHandler transactionIdHandler = new TransactionIdHandler(transactionHeader);
        router.route(GET, PATH_LOGIN).handler(transactionIdHandler);
        router.route(PATH_LOGIN).handler(authenticationFlowContextHandler);
        router.route(PATH_LOGIN_CALLBACK).handler(authenticationFlowContextHandler);
        router.route(PATH_LOGIN_SSO_POST).handler(authenticationFlowContextHandler);
        router.route(PATH_LOGIN_SSO_SPNEGO).handler(authenticationFlowContextHandler);

        // MFA endpoint
        router.route(PATH_MFA_ENROLL).handler(authenticationFlowContextHandler);
        router.route(PATH_MFA_CHALLENGE).handler(authenticationFlowContextHandler);
        router.route(PATH_MFA_RECOVERY_CODE).handler(authenticationFlowContextHandler);

        // Registration confirmation endpoint
        router.route(PATH_REGISTER).handler(authenticationFlowContextHandler);
        router.route(PATH_CONFIRM_REGISTRATION).handler(authenticationFlowContextHandler);

        // Reset password endpoint
        router.route(PATH_RESET_PASSWORD).handler(authenticationFlowContextHandler);

        // WebAuthn endpoint
        router.route(GET, PATH_WEBAUTHN_REGISTER).handler(transactionIdHandler);
        router.route(PATH_WEBAUTHN_REGISTER).handler(authenticationFlowContextHandler);
        router.route(PATH_WEBAUTHN_REGISTER_SUCCESS).handler(authenticationFlowContextHandler);
        router.route(PATH_WEBAUTHN_RESPONSE).handler(authenticationFlowContextHandler);
        router.route(GET, PATH_WEBAUTHN_LOGIN).handler(transactionIdHandler);
        router.route(PATH_WEBAUTHN_LOGIN).handler(authenticationFlowContextHandler);

        // Identifier First Login endpoint
        router.route(GET, PATH_IDENTIFIER_FIRST_LOGIN).handler(transactionIdHandler);
        router.route(PATH_IDENTIFIER_FIRST_LOGIN).handler(authenticationFlowContextHandler);
    }

    private void csrfHandler(Router router) {
        // /login/callback does not need csrf as it is not submit to our server.
        // /oauth/consent csrf is managed by handler-oidc (see OAuth2Provider).
        router.route(PATH_FORGOT_PASSWORD).handler(csrfHandler);
        router.route(PATH_LOGIN).handler(csrfHandler);
        router.route(PATH_IDENTIFIER_FIRST_LOGIN).handler(csrfHandler);
        router.route(PATH_LOGIN_SSO_POST).handler(csrfHandler);
        router.route(PATH_MFA_CHALLENGE).handler(csrfHandler);
        router.route(PATH_MFA_CHALLENGE_ALTERNATIVES).handler(csrfHandler);
        router.route(PATH_MFA_RECOVERY_CODE).handler(csrfHandler);
        router.route(PATH_MFA_ENROLL).handler(csrfHandler);
        router.route(PATH_REGISTER).handler(csrfHandler);
        router.route(PATH_CONFIRM_REGISTRATION).handler(csrfHandler);
        router.route(PATH_RESET_PASSWORD).handler(csrfHandler);
        router.route(PATH_VERIFY_REGISTRATION).handler(csrfHandler);
        router.route(PATH_WEBAUTHN_REGISTER_SUCCESS).handler(csrfHandler);
    }

    private void cspHandler(Router router) {
        // /login/callback does not need csp as it is not submit to our server.
        // /oauth/consent csp is managed by handler-oidc (see OAuth2Provider).
        router.route(PATH_LOGIN).handler(cspHandler);
        router.route(PATH_LOGIN_CALLBACK).handler(cspHandler);
        router.route(PATH_LOGIN_SSO_POST).handler(cspHandler);
        router.route(PATH_LOGIN_SSO_SPNEGO).handler(cspHandler);
        router.route(PATH_MFA_ENROLL).handler(cspHandler);
        router.route(PATH_MFA_CHALLENGE).handler(cspHandler);
        router.route(PATH_MFA_CHALLENGE_ALTERNATIVES).handler(cspHandler);
        router.route(PATH_MFA_RECOVERY_CODE).handler(cspHandler);
        router.route(PATH_LOGOUT).handler(cspHandler);
        router.route(PATH_LOGOUT_CALLBACK).handler(cspHandler);
        router.route(PATH_REGISTER).handler(cspHandler);
        router.route(PATH_CONFIRM_REGISTRATION).handler(cspHandler);
        router.route(PATH_RESET_PASSWORD).handler(cspHandler);
        router.route(PATH_WEBAUTHN_REGISTER).handler(cspHandler);
        router.route(PATH_WEBAUTHN_REGISTER_SUCCESS).handler(cspHandler);
        router.route(PATH_WEBAUTHN_RESPONSE).handler(cspHandler);
        router.route(PATH_WEBAUTHN_LOGIN).handler(cspHandler);
        router.route(PATH_FORGOT_PASSWORD).handler(cspHandler);
        router.route(PATH_IDENTIFIER_FIRST_LOGIN).handler(cspHandler);
        router.route(PATH_VERIFY_REGISTRATION).handler(cspHandler);
        router.route(PATH_ERROR).handler(cspHandler);
    }

    private void xFrameHandler(Router router) {
        router.route(PATH_LOGIN).handler(xframeHandler);
        router.route(PATH_LOGIN_CALLBACK).handler(xframeHandler);
        router.route(PATH_LOGIN_SSO_POST).handler(xframeHandler);
        router.route(PATH_LOGIN_SSO_SPNEGO).handler(xframeHandler);
        router.route(PATH_MFA_ENROLL).handler(xframeHandler);
        router.route(PATH_MFA_CHALLENGE).handler(xframeHandler);
        router.route(PATH_MFA_CHALLENGE_ALTERNATIVES).handler(xframeHandler);
        router.route(PATH_LOGOUT).handler(xframeHandler);
        router.route(PATH_LOGOUT_CALLBACK).handler(xframeHandler);
        router.route(PATH_REGISTER).handler(xframeHandler);
        router.route(PATH_CONFIRM_REGISTRATION).handler(xframeHandler);
        router.route(PATH_RESET_PASSWORD).handler(xframeHandler);
        router.route(PATH_WEBAUTHN_REGISTER).handler(xframeHandler);
        router.route(PATH_WEBAUTHN_REGISTER_SUCCESS).handler(xframeHandler);
        router.route(PATH_WEBAUTHN_RESPONSE).handler(xframeHandler);
        router.route(PATH_WEBAUTHN_LOGIN).handler(xframeHandler);
        router.route(PATH_FORGOT_PASSWORD).handler(xframeHandler);
        router.route(PATH_IDENTIFIER_FIRST_LOGIN).handler(xframeHandler);
        router.route(PATH_VERIFY_REGISTRATION).handler(xframeHandler);
        router.route(PATH_ERROR).handler(xframeHandler);
    }

    private void xssHandler(Router router) {
        router.route(PATH_LOGIN).handler(xssHandler);
        router.route(PATH_LOGIN_CALLBACK).handler(xssHandler);
        router.route(PATH_LOGIN_SSO_POST).handler(xssHandler);
        router.route(PATH_LOGIN_SSO_SPNEGO).handler(xssHandler);
        router.route(PATH_MFA_ENROLL).handler(xssHandler);
        router.route(PATH_MFA_CHALLENGE).handler(xssHandler);
        router.route(PATH_MFA_CHALLENGE_ALTERNATIVES).handler(xssHandler);
        router.route(PATH_LOGOUT).handler(xssHandler);
        router.route(PATH_LOGOUT_CALLBACK).handler(xssHandler);
        router.route(PATH_REGISTER).handler(xssHandler);
        router.route(PATH_CONFIRM_REGISTRATION).handler(xssHandler);
        router.route(PATH_RESET_PASSWORD).handler(xssHandler);
        router.route(PATH_WEBAUTHN_REGISTER).handler(xssHandler);
        router.route(PATH_WEBAUTHN_REGISTER_SUCCESS).handler(xssHandler);
        router.route(PATH_WEBAUTHN_RESPONSE).handler(xssHandler);
        router.route(PATH_WEBAUTHN_LOGIN).handler(xssHandler);
        router.route(PATH_FORGOT_PASSWORD).handler(xssHandler);
        router.route(PATH_IDENTIFIER_FIRST_LOGIN).handler(xssHandler);
        router.route(PATH_VERIFY_REGISTRATION).handler(xssHandler);
        router.route(PATH_ERROR).handler(xssHandler);
    }

    private void staticHandler(Router router) {
        router.route().handler(StaticHandler.create());
    }

    private void bodyHandler(Router router) {
        router.route()
                .handler(new ConditionalBodyHandler(environment));
    }

    private void errorHandler(Router router) {
        Handler<RoutingContext> errorHandler = new ErrorHandler(PATH_ERROR);
        router.route(PATH_LOGOUT).failureHandler(errorHandler);
        router.route(PATH_LOGOUT_CALLBACK).failureHandler(errorHandler);
        router.route(PATH_LOGIN).failureHandler(errorHandler);
        router.route(PATH_IDENTIFIER_FIRST_LOGIN).failureHandler(errorHandler);
        router.route(PATH_WEBAUTHN_LOGIN).failureHandler(errorHandler);
        router.route(PATH_WEBAUTHN_REGISTER).failureHandler(errorHandler);
        router.route(PATH_WEBAUTHN_REGISTER_SUCCESS).failureHandler(errorHandler);
        router.route(PATH_WEBAUTHN_RESPONSE).failureHandler(errorHandler);

        router.route(PATH_VERIFY_REGISTRATION).failureHandler(errorHandler);

        router.route(PATH_MFA_ENROLL).failureHandler(errorHandler);
        router.route(PATH_MFA_CHALLENGE_ALTERNATIVES).failureHandler(errorHandler);
        router.route(PATH_MFA_RECOVERY_CODE).failureHandler(errorHandler);

        router.route(PATH_CONFIRM_REGISTRATION).failureHandler(errorHandler);
    }
}
