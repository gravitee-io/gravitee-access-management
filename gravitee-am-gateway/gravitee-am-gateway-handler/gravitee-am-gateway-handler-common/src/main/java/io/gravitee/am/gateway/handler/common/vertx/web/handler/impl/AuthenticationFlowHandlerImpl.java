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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl;

import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.AuthenticationFlowHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.RedirectHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChainHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.ForceResetPasswordStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.FormIdentifierFirstLoginStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.FormLoginStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.RememberMeStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.SPNEGOStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.WebAuthnLoginStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.WebAuthnRegisterStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFAChallengeStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFAEnrollStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFARecoveryCodeStep;
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.gravitee.am.model.Domain;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.util.LinkedList;
import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_REMEMBER_ME_COOKIE_NAME;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationFlowHandlerImpl implements AuthenticationFlowHandler {

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private FactorManager factorManager;

    @Autowired
    private CredentialGatewayService credentialService;

    @Autowired
    private WebAuthnCookieService webAuthnCookieService;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private UserGatewayService userService;

    @Autowired
    private CertificateManager certificateManager;

    @Autowired
    private Environment environment;

    @Value("${http.cookie.rememberMe.name:" + DEFAULT_REMEMBER_ME_COOKIE_NAME + "}")
    private String rememberMeCookieName;

    @Override
    public Handler<RoutingContext> create() {
        List<AuthenticationFlowStep> steps = new LinkedList<>();
        steps.add(new RememberMeStep(RedirectHandler.create("/rememberedLogin"), jwtService, userService, rememberMeCookieName));
        steps.add(new SPNEGOStep(RedirectHandler.create("/login/SSO/SPNEGO"), identityProviderManager));
        steps.add(new FormIdentifierFirstLoginStep(RedirectHandler.create("/login/identifier"), domain));
        steps.add(new WebAuthnLoginStep(RedirectHandler.create("/webauthn/login"), domain, credentialService, webAuthnCookieService));
        steps.add(new FormLoginStep(RedirectHandler.create("/login")));
        steps.add(new ForceResetPasswordStep(RedirectHandler.create("/resetPassword"), jwtService, certificateManager, environment));
        steps.add(new WebAuthnRegisterStep(domain, RedirectHandler.create("/webauthn/register"), factorManager, credentialService));
        steps.add(new MFAEnrollStep(RedirectHandler.create("/mfa/enroll"), ruleEngine, factorManager));
        steps.add(new MFAChallengeStep(RedirectHandler.create("/mfa/challenge"), ruleEngine, factorManager));
        steps.add(new MFARecoveryCodeStep(RedirectHandler.create("/mfa/recovery_code"), ruleEngine, factorManager));
        return new AuthenticationFlowChainHandler(steps);
    }
}
