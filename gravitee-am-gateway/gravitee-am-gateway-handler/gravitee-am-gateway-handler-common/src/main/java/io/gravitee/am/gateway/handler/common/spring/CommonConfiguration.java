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
package io.gravitee.am.gateway.handler.common.spring;

import io.gravitee.am.gateway.handler.common.alert.AlertEventProcessor;
import io.gravitee.am.gateway.handler.common.audit.AuditReporterManager;
import io.gravitee.am.gateway.handler.common.audit.impl.AuditReporterManagerImpl;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.idp.impl.IdentityProviderManagerImpl;
import io.gravitee.am.gateway.handler.common.auth.listener.AuthenticationEventListener;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationService;
import io.gravitee.am.gateway.handler.common.auth.user.impl.UserAuthenticationManagerImpl;
import io.gravitee.am.gateway.handler.common.auth.user.impl.UserAuthenticationServiceImpl;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.certificate.impl.CertificateManagerImpl;
import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.client.impl.ClientManagerImpl;
import io.gravitee.am.gateway.handler.common.client.impl.ClientSyncServiceImpl;
import io.gravitee.am.gateway.handler.common.email.EmailManager;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.email.impl.EmailManagerImpl;
import io.gravitee.am.gateway.handler.common.email.impl.EmailServiceImpl;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.common.flow.impl.FlowManagerImpl;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.impl.JWTServiceImpl;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenService;
import io.gravitee.am.gateway.handler.common.oauth2.impl.IntrospectionTokenServiceImpl;
import io.gravitee.am.gateway.handler.common.policy.DefaultRulesEngine;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.ruleengine.SpELRuleEngine;
import io.gravitee.am.gateway.handler.common.spring.web.WebConfiguration;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.user.impl.UserServiceImpl;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl.OAuth2AuthProviderImpl;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl.UserAuthProviderImpl;
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.context.TemplateVariableProviderFactory;
import io.gravitee.am.gateway.handler.context.spring.ContextConfiguration;
import io.gravitee.am.gateway.policy.spring.PolicyConfiguration;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan("io.gravitee.am.gateway.handler.common.utils")
@Import({
        WebConfiguration.class,
        FreemarkerConfiguration.class,
        PolicyConfiguration.class,
        ContextConfiguration.class,
        RiskAssessmentConfiguration.class})
public class CommonConfiguration {

    @Autowired
    private Environment environment;

    @Autowired
    private Vertx vertx;

    @Bean
    @Qualifier("oidcWebClient")
    public WebClient webClient() {
        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout(Integer.valueOf(environment.getProperty("oidc.http.connectionTimeout", "10")) * 1000)
                .setMaxPoolSize(Integer.valueOf(environment.getProperty("oidc.http.pool.maxTotalConnection", "200")))
                .setTrustAll(Boolean.valueOf(environment.getProperty("oidc.http.client.trustAll", "true")));

        return WebClient.create(vertx, options);
    }

    @Bean
    public RuleEngine ruleEngine() {
        return new SpELRuleEngine();
    }

    @Bean
    public IdentityProviderManager identityProviderManager() {
        return new IdentityProviderManagerImpl();
    }

    @Bean
    public UserAuthenticationManager userAuthenticationManager() {
        return new UserAuthenticationManagerImpl();
    }

    @Bean
    public UserAuthenticationService userAuthenticationService() {
        return new UserAuthenticationServiceImpl();
    }

    @Bean
    public AuditReporterManager auditReporterManager() {
        return new AuditReporterManagerImpl();
    }

    @Bean
    public CertificateManager certificateManager() {
        return new CertificateManagerImpl();
    }

    @Bean
    public JWTService jwtService() {
        return new JWTServiceImpl();
    }

    @Bean
    public ClientManager clientManager() {
        return new ClientManagerImpl();
    }

    @Bean
    public ClientSyncService clientSyncService() {
        return new ClientSyncServiceImpl();
    }

    @Bean
    public UserAuthProvider userAuthProvider() {
        return new UserAuthProviderImpl();
    }

    @Bean
    public OAuth2AuthProvider oAuth2AuthProvider() {
        return new OAuth2AuthProviderImpl();
    }

    @Bean
    public AuthenticationEventListener authenticationEventListener() {
        return new AuthenticationEventListener();
    }

    @Bean
    public AlertEventProcessor alertEventProcessor() {
        return new AlertEventProcessor();
    }

    @Bean
    public FlowManager flowManager() {
        return new FlowManagerImpl();
    }

    @Bean
    public ExecutionContextFactory executionContextFactory() {
        return new ExecutionContextFactory();
    }

    @Bean
    public TemplateVariableProviderFactory templateVariableProviderFactory() {
        return new TemplateVariableProviderFactory();
    }

    @Bean
    public IntrospectionTokenService introspectiontokenservice() {
        return new IntrospectionTokenServiceImpl();
    }

    @Bean
    public UserService userService() {
        return new UserServiceImpl();
    }

    @Bean
    public EmailService emailService(
            @Value("${email.enabled:false}") boolean enabled,
            @Value("${user.resetPassword.email.subject:Please reset your password}") String resetPasswordSubject,
            @Value("${user.resetPassword.token.expire-after:300}") int resetPasswordExpireAfter,
            @Value("${user.blockedAccount.email.subject:Account has been locked}") String blockedAccountSubject,
            @Value("${user.blockedAccount.token.expire-after:86400}") int blockedAccountExpireAfter,
            @Value("${user.mfaChallenge.email.subject:Verification Code}") String mfaChallengeSubject,
            @Value("${user.mfaChallenge.token.expire-after:300}") int mfaChallengeExpireAfter,
            @Value("${user.mfaVerifyAttempt.email.subject:${msg('email.verify_attempt.subject')}}") String mfaVerifyAttemptSubject,
            @Value("${user.registration.verify.email.subject:${msg('email.registration_verify.subject')}}") String registrationVerifySubject,
            @Value("${user.registration.verify.time.value:7}") int userRegistrationVerifyTimeValue,
            @Value("${user.registration.verify.time.unit:DAYS}") TimeUnit userRegistrationVerifyTimeUnit
    ) {
        return new EmailServiceImpl(
                enabled,
                resetPasswordSubject,
                resetPasswordExpireAfter,
                blockedAccountSubject,
                blockedAccountExpireAfter,
                mfaChallengeSubject,
                mfaChallengeExpireAfter,
                mfaVerifyAttemptSubject,
                registrationVerifySubject,
                Math.toIntExact(userRegistrationVerifyTimeUnit.toSeconds(userRegistrationVerifyTimeValue))
        );
    }

    @Bean
    public EmailManager emailManager() {
        return new EmailManagerImpl();
    }

    @Bean
    public WebAuthnCookieService webAuthnCookieService() {
        return new WebAuthnCookieService();
    }

    @Bean
    public RulesEngine rulesEngine() {
        return new DefaultRulesEngine();
    }
}
