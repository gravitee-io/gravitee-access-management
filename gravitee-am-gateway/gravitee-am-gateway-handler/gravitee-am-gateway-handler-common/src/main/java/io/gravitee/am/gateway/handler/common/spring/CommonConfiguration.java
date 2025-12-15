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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.common.alert.AlertEventProcessor;
import io.gravitee.am.gateway.handler.common.audit.AuditReporterManager;
import io.gravitee.am.gateway.handler.common.audit.impl.GatewayAuditReporterManager;
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
import io.gravitee.am.gateway.handler.common.group.GroupManager;
import io.gravitee.am.gateway.handler.common.group.impl.DefaultGroupManager;
import io.gravitee.am.gateway.handler.common.group.impl.InMemoryGroupManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.impl.JWTServiceImpl;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenFacade;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenService;
import io.gravitee.am.gateway.handler.common.oauth2.impl.IntrospectionAccessTokenService;
import io.gravitee.am.gateway.handler.common.oauth2.impl.IntrospectionRefreshTokenService;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManagerImpl;
import io.gravitee.am.gateway.handler.common.policy.DefaultRulesEngine;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.role.RoleManager;
import io.gravitee.am.gateway.handler.common.role.impl.DefaultRoleManager;
import io.gravitee.am.gateway.handler.common.role.impl.InMemoryRoleManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.ruleengine.SpELRuleEngine;
import io.gravitee.am.gateway.handler.common.spring.web.WebConfiguration;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.user.UserStore;
import io.gravitee.am.gateway.handler.common.user.impl.NoUserStore;
import io.gravitee.am.gateway.handler.common.user.impl.UserEnhancerFacade;
import io.gravitee.am.gateway.handler.common.user.impl.UserServiceImpl;
import io.gravitee.am.gateway.handler.common.user.impl.UserServiceImplV2;
import io.gravitee.am.gateway.handler.common.user.impl.UserStoreImpl;
import io.gravitee.am.gateway.handler.common.user.impl.UserStoreImplV2;
import io.gravitee.am.gateway.handler.common.utils.ConfigurationHelper;
import io.gravitee.am.gateway.handler.common.utils.StaticEnvironmentProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl.OAuth2AuthProviderImpl;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl.UserAuthProviderImpl;
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.context.TemplateVariableProviderFactory;
import io.gravitee.am.gateway.handler.context.spring.ContextConfiguration;
import io.gravitee.am.gateway.policy.spring.PolicyConfiguration;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.DomainVersion;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.service.impl.user.UserEnhancer;
import io.gravitee.node.api.cache.CacheManager;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.util.concurrent.TimeUnit;


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
    public static final String FALLBACK_TO_HMAC_SIGNATURE_CONFIG_PROPERTY = "applications.signing.fallback-to-hmac-signature";

    @Autowired
    private Environment environment;

    @Autowired
    private Vertx vertx;

    @PostConstruct
    public void initStaticEnvironmentProvider() {
        StaticEnvironmentProvider.setEnvironment(environment);
    }

    @Bean
    @Qualifier("oidcWebClient")
    public WebClient webClient() {
        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout(Integer.parseInt(environment.getProperty("oidc.http.connectionTimeout", "10")) * 1000)
                .setMaxPoolSize(Integer.parseInt(environment.getProperty("oidc.http.pool.maxTotalConnection", "200")))
                .setTrustAll(Boolean.parseBoolean(environment.getProperty("oidc.http.client.trustAll", "true")));

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
        return new GatewayAuditReporterManager();
    }

    @Bean
    public CertificateManager certificateManager() {
        return new CertificateManagerImpl();
    }

    @Bean
    public JWTService jwtService(
            CertificateManager certificateManager,
            ObjectMapper objectMapper,
            @Value("${" + FALLBACK_TO_HMAC_SIGNATURE_CONFIG_PROPERTY + ":true}") Boolean fallbackToHmacSignature) {
        return new JWTServiceImpl(certificateManager, objectMapper, fallbackToHmacSignature);
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
    @Qualifier("AccessTokenIntrospection")
    public IntrospectionTokenService introspectionAccessTokenService(JWTService jwtService,
                                                                     ClientSyncService clientSyncService,
                                                                     AccessTokenRepository accessTokenRepository) {
        return new IntrospectionAccessTokenService(jwtService, clientSyncService, accessTokenRepository);
    }

    @Bean
    @Qualifier("RefreshTokenIntrospection")
    public IntrospectionTokenService introspectionRefreshTokenService(JWTService jwtService,
                                                                      ClientSyncService clientSyncService,
                                                                      RefreshTokenRepository refreshTokenRepository) {
        return new IntrospectionRefreshTokenService(jwtService, clientSyncService, refreshTokenRepository);
    }

    @Bean
    public IntrospectionTokenFacade introspectionTokenFacade(@Qualifier("AccessTokenIntrospection") IntrospectionTokenService accessTokenIntrospectionService,
                                                             @Qualifier("RefreshTokenIntrospection") IntrospectionTokenService refreshTokenIntrospectionService){
        return new IntrospectionTokenFacade(accessTokenIntrospectionService, refreshTokenIntrospectionService);
    }

    @Bean
    public UserStore userStore(Domain domain, CacheManager cacheManager, Environment environment) {
        if (ConfigurationHelper.useUserStore(environment)) {
            return domain.getVersion() == DomainVersion.V1_0 ? new UserStoreImpl(cacheManager, environment) : new UserStoreImplV2(cacheManager, environment);
        }
        return new NoUserStore();
    }

    @Bean
    public UserService userService(Domain domain) {
        if (domain.getVersion() == DomainVersion.V1_0) {
            return new UserServiceImpl();
        }
        return new UserServiceImplV2();
    }

    @Bean
    public EmailService emailService() {
        final boolean enabled = environment.getProperty("email.enabled", Boolean.class, false);
        final String resetPasswordSubject = environment.getProperty("user.resetPassword.email.subject", String.class, "Please reset your password");
        final int resetPasswordExpireAfter = environment.getProperty("user.resetPassword.token.expire-after", Integer.class, 300);
        final String blockedAccountSubject = environment.getProperty("user.blockedAccount.email.subject", String.class, "Account has been locked");
        final int blockedAccountExpireAfter = environment.getProperty("user.blockedAccount.token.expire-after", Integer.class, 86400);
        final String mfaChallengeSubject = environment.getProperty("user.mfaChallenge.email.subject", String.class, "Verification Code");
        final int mfaChallengeExpireAfter = environment.getProperty("user.mfaChallenge.token.expire-after", Integer.class, 300);
        final String mfaVerifyAttemptSubject = environment.getProperty("user.mfaVerifyAttempt.email.subject", String.class, "${msg('email.verify_attempt.subject')}");
        final String registrationVerifySubject = environment.getProperty("user.registration.verify.email.subject", String.class, "${msg('email.registration_verify.subject')}");
        final int userRegistrationVerifyTimeValue = environment.getProperty("user.registration.verify.time.value", Integer.class, 7);
        final TimeUnit userRegistrationVerifyTimeUnit = environment.getProperty("user.registration.verify.time.unit", TimeUnit.class, TimeUnit.DAYS);

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

    @Bean
    public PasswordPolicyManager passwordPolicyManager() {
        return new PasswordPolicyManagerImpl();
    }
    @Bean
    public GroupManager groupManager(Environment environment) {
        if (ConfigurationHelper.useInMemoryRoleAndGroupManager(environment)) {
            return new InMemoryGroupManager();
        } else {
            return new DefaultGroupManager();
        }
    }

    @Bean
    public RoleManager roleManager(Environment environment) {
        if (ConfigurationHelper.useInMemoryRoleAndGroupManager(environment)) {
            return new InMemoryRoleManager();
        } else {
            return new DefaultRoleManager();
        }
    }

    @Bean
    @Primary
    public UserEnhancer facadeManagerUserEnhancer(GroupManager groupManager, RoleManager roleManager) {
        return new UserEnhancerFacade(groupManager, roleManager);
    }


}
