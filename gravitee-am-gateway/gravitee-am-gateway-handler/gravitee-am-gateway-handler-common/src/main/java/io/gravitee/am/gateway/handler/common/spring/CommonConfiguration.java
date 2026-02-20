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
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.gateway.handler.common.alert.AlertEventProcessor;
import io.gravitee.am.gateway.handler.common.audit.AuditReporterManager;
import io.gravitee.am.gateway.handler.common.audit.impl.GatewayAuditReporterManager;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.idp.impl.IdentityProviderManagerImpl;
import io.gravitee.am.gateway.handler.common.authorizationengine.AuthorizationEngineManager;
import io.gravitee.am.gateway.handler.common.authorizationengine.impl.AuthorizationEngineManagerImpl;
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
import io.gravitee.am.gateway.handler.common.email.EmailStagingService;
import io.gravitee.am.gateway.handler.common.email.impl.EmailManagerImpl;
import io.gravitee.am.gateway.handler.common.email.impl.EmailServiceImpl;
import io.gravitee.am.gateway.handler.common.email.impl.EmailStagingServiceImpl;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.common.flow.impl.FlowManagerImpl;
import io.gravitee.am.gateway.handler.common.group.GroupManager;
import io.gravitee.am.gateway.handler.common.group.impl.DefaultGroupManager;
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
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.gateway.handler.common.protectedresource.impl.ProtectedResourceManagerImpl;
import io.gravitee.am.gateway.handler.common.protectedresource.impl.ProtectedResourceSyncServiceImpl;
import io.gravitee.am.gateway.handler.common.role.RoleManager;
import io.gravitee.am.gateway.handler.common.role.impl.DefaultRoleManager;
import io.gravitee.am.gateway.handler.common.role.impl.InMemoryRoleManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.ruleengine.SpELRuleEngine;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.service.DeviceGatewayService;
import io.gravitee.am.gateway.handler.common.service.LoginAttemptGatewayService;
import io.gravitee.am.gateway.handler.common.service.RevokeTokenGatewayService;
import io.gravitee.am.gateway.handler.common.service.impl.RevokeTokenGatewayServiceImpl;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.gateway.handler.common.service.mfa.UserEventListener;
import io.gravitee.am.gateway.handler.common.service.mfa.VerifyAttemptService;
import io.gravitee.am.gateway.handler.common.service.mfa.impl.DomainEventListenerImpl;
import io.gravitee.am.gateway.handler.common.service.mfa.impl.RateLimiterServiceImpl;
import io.gravitee.am.gateway.handler.common.service.mfa.impl.UserEventListenerImpl;
import io.gravitee.am.gateway.handler.common.service.mfa.impl.VerifyAttemptServiceImpl;
import io.gravitee.am.gateway.handler.common.service.uma.UMAPermissionTicketService;
import io.gravitee.am.gateway.handler.common.service.uma.UMAResourceGatewayService;
import io.gravitee.am.gateway.handler.common.service.UserActivityGatewayService;
import io.gravitee.am.gateway.handler.common.service.impl.CredentialGatewayServiceImpl;
import io.gravitee.am.gateway.handler.common.service.impl.DeviceGatewayServiceImpl;
import io.gravitee.am.gateway.handler.common.service.impl.LoginAttemptGatewayServiceImpl;
import io.gravitee.am.gateway.handler.common.service.uma.impl.UMAPermissionTicketServiceImpl;
import io.gravitee.am.gateway.handler.common.service.uma.impl.UMAResourceGatewayServiceImpl;
import io.gravitee.am.gateway.handler.common.service.impl.UserActivityGatewayServiceImpl;
import io.gravitee.am.gateway.handler.common.spring.web.WebConfiguration;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.common.user.UserStore;
import io.gravitee.am.gateway.handler.common.user.impl.NoUserStore;
import io.gravitee.am.gateway.handler.common.user.impl.UserEnhancerFacade;
import io.gravitee.am.gateway.handler.common.user.impl.UserGatewayServiceImpl;
import io.gravitee.am.gateway.handler.common.user.impl.UserGatewayServiceImplV2;
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
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.dataplane.user.activity.configuration.UserActivityConfiguration;
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
    public AuthorizationEngineManager authorizationEngineManager() {
        return new AuthorizationEngineManagerImpl();
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
    public ProtectedResourceManager protectedResourceManager() {
        return new ProtectedResourceManagerImpl();
    }

    @Bean
    public ProtectedResourceSyncService protectedResourceSyncService() {
        return new ProtectedResourceSyncServiceImpl();
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
                                                                     ProtectedResourceManager protectedResourceManager,
                                                                     ProtectedResourceSyncService protectedResourceSyncService,
                                                                     BackwardCompatibleTokenRepository tokenRepository) {
        return new IntrospectionAccessTokenService(jwtService, clientSyncService, protectedResourceManager, protectedResourceSyncService, environment, tokenRepository);
    }

    @Bean
    @Qualifier("RefreshTokenIntrospection")
    public IntrospectionTokenService introspectionRefreshTokenService(JWTService jwtService,
                                                                      ClientSyncService clientSyncService,
                                                                      ProtectedResourceManager protectedResourceManager,
                                                                      ProtectedResourceSyncService protectedResourceSyncService,
                                                                      BackwardCompatibleTokenRepository tokenRepository) {
        return new IntrospectionRefreshTokenService(jwtService, clientSyncService, protectedResourceManager, protectedResourceSyncService, environment, tokenRepository);
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
    public UserGatewayService userService(Domain domain) {
        if (domain.getVersion() == DomainVersion.V1_0) {
            return new UserGatewayServiceImpl();
        }
        return new UserGatewayServiceImplV2();
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
        final String registrationConfirmationSubject = environment.getProperty("user.registration.confirmation.email.subject", String.class, "${msg('email.registration_confirmation.subject')}");
        final int userRegistrationConfirmationTimeValue = environment.getProperty("user.registration.confirmation.time.value", Integer.class, 24);
        final TimeUnit userRegistrationConfirmationTimeUnit = environment.getProperty("user.registration.confirmation.time.unit", TimeUnit.class, TimeUnit.HOURS);

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
                Math.toIntExact(userRegistrationVerifyTimeUnit.toSeconds(userRegistrationVerifyTimeValue)),
                registrationConfirmationSubject,
                Math.toIntExact(userRegistrationConfirmationTimeUnit.toSeconds(userRegistrationConfirmationTimeValue))
        );
    }

    @Bean
    public EmailStagingService emailStagingService() {
        return new EmailStagingServiceImpl();
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
    public GroupManager groupManager(Environment environment, EventManager eventManager, DataPlaneRegistry registry, Domain domain) {
        final var cachedRepository = registry.getGroupRepository(domain);
        // FIXME: sync process can not be done anymore, need to convert as a classical cache.
        //        Since the first implementation of the DataPlane split, groups are managed on the GW
        //        as consequence Sync is not possible.
        //        we may have to rethink the way users are linked to the group to keep track of the groups into the user profile
        //        so the Group can be request only of the user profile has at least one group and group can be cached for a short living time
        /*if (ConfigurationHelper.useInMemoryRoleAndGroupManager(environment)) {
            return new InMemoryGroupManager(domain, eventManager, cachedRepository);
        } else {*/
            return new DefaultGroupManager(cachedRepository);
        /*}*/
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

    @Bean
    public CredentialGatewayService credentialGatewayService(DataPlaneRegistry dataPlaneRegistry) {
        return new CredentialGatewayServiceImpl(dataPlaneRegistry);
    }

    @Bean
    public UserActivityGatewayService userActivityGatewayService(UserActivityConfiguration configuration, DataPlaneRegistry dataPlaneRegistry) {
        return new UserActivityGatewayServiceImpl(configuration, dataPlaneRegistry);
    }

    @Bean
    public DeviceGatewayService deviceGatewayService(DataPlaneRegistry dataPlaneRegistry) {
        return new DeviceGatewayServiceImpl(dataPlaneRegistry);
    }

    @Bean
    public LoginAttemptGatewayService loginAttemptGatewayService(DataPlaneRegistry dataPlaneRegistry) {
        return new LoginAttemptGatewayServiceImpl(dataPlaneRegistry);
    }

    @Bean
    public UMAResourceGatewayService umaResourceGatewayService(Domain domain, DataPlaneRegistry dataPlaneRegistry, ScopeService scopeService) {
        return new UMAResourceGatewayServiceImpl(domain, dataPlaneRegistry, scopeService);
    }

    @Bean
    public DomainDataPlane domainDataPlane(Domain domain, DataPlaneRegistry dataPlaneRegistry){
        DataPlaneDescription description = dataPlaneRegistry.getDescription(domain);
        return new DomainDataPlane(domain, description);
    }

    @Bean
    public UMAPermissionTicketService umaPermissionTicketService() {
        return new UMAPermissionTicketServiceImpl();
    }

    @Bean
    public RevokeTokenGatewayService revokeTokenGatewayService() {
        return new RevokeTokenGatewayServiceImpl();
    }

    @Bean
    public RateLimiterService rateLimiterService() {
        return new RateLimiterServiceImpl();
    }
    @Bean
    public VerifyAttemptService verifyAttemptService() {
        return new VerifyAttemptServiceImpl();
    }

    @Bean
    public UserEventListener userEventListener() {
        return new UserEventListenerImpl();
    }

    @Bean
    public DomainEventListenerImpl domainEventListener() {
        return new DomainEventListenerImpl();
    }

}
