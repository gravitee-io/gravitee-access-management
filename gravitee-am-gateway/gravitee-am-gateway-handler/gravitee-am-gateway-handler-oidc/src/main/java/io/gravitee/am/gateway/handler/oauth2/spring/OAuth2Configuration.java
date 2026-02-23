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
package io.gravitee.am.gateway.handler.oauth2.spring;

import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.gateway.handler.api.ProtocolConfiguration;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.oauth2.OAuth2Provider;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.ClientAssertionServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.code.impl.AuthorizationCodeServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.consent.impl.UserConsentServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.granter.CompositeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.ExtensionGrantManager;
import io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.impl.ExtensionGrantManagerImpl;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.impl.IntrospectionServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.revocation.OAuthRevocationTokenService;
import io.gravitee.am.gateway.handler.oauth2.service.revocation.impl.OAuthRevocationTokenServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeService;
import io.gravitee.am.gateway.handler.oauth2.service.scope.impl.ScopeManagerImpl;
import io.gravitee.am.gateway.handler.oauth2.service.scope.impl.ScopeServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenManager;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.TokenEnhancerImpl;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.TokenManagerImpl;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.TokenServiceImpl;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeService;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl.DefaultTokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl.TokenExchangeServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TrustedIssuerResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl.TrustedIssuerResolverImpl;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceValidationService;
import io.gravitee.am.gateway.handler.oauth2.service.validation.impl.ResourceValidationServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceConsistencyValidationService;
import io.gravitee.am.gateway.handler.oauth2.service.validation.impl.ResourceConsistencyValidationServiceImpl;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class OAuth2Configuration implements ProtocolConfiguration {

    @Bean
    public TokenGranter tokenGranter() {
        return new CompositeTokenGranter();
    }

    @Bean
    public TokenService tokenService() {
        return new TokenServiceImpl();
    }

    @Bean
    public TokenExchangeService tokenExchangeService(List<TokenValidator> validators,
                                                     SubjectManager subjectManager,
                                                     ProtectedResourceManager protectedResourceManager,
                                                     UserGatewayService userGatewayService) {
        return new TokenExchangeServiceImpl(validators, subjectManager, protectedResourceManager, userGatewayService);
    }

    @Bean
    public TrustedIssuerResolver trustedIssuerResolver() {
        return new TrustedIssuerResolverImpl();
    }

    @Bean
    public TokenValidator accessTokenValidator(JWTService jwtService, TrustedIssuerResolver trustedIssuerResolver) {
        return new DefaultTokenValidator(jwtService, JWTService.TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, trustedIssuerResolver);
    }

    @Bean
    public TokenValidator idTokenValidator(JWTService jwtService, TrustedIssuerResolver trustedIssuerResolver) {
        return new DefaultTokenValidator(jwtService, JWTService.TokenType.ID_TOKEN, TokenType.ID_TOKEN, trustedIssuerResolver);
    }

    @Bean
    public TokenValidator refreshTokenValidator(JWTService jwtService, TrustedIssuerResolver trustedIssuerResolver) {
        return new DefaultTokenValidator(jwtService, JWTService.TokenType.REFRESH_TOKEN, TokenType.REFRESH_TOKEN, trustedIssuerResolver);
    }

    @Bean
    public TokenValidator jwtTokenValidator(JWTService jwtService, TrustedIssuerResolver trustedIssuerResolver) {
        return new DefaultTokenValidator(jwtService, JWTService.TokenType.JWT, TokenType.JWT, trustedIssuerResolver);
    }

    @Bean
    public IntrospectionService introspectionService() {
        return new IntrospectionServiceImpl();
    }

    @Bean
    public AuthorizationCodeService authorizationCodeService() {
        return new AuthorizationCodeServiceImpl();
    }

    @Bean
    public UserConsentService userConsentService(
            @Value("${oauth2.approval.expiry:-1}") int approvalExpirySeconds
    ) {
        return new UserConsentServiceImpl(approvalExpirySeconds);
    }

    @Bean
    public ScopeService scopeService() {
        return new ScopeServiceImpl();
    }

    @Bean
    public TokenEnhancer tokenEnhancer() {
        return new TokenEnhancerImpl();
    }

    @Bean
    public ExtensionGrantManager extensionGrantManager() {
        return new ExtensionGrantManagerImpl();
    }

    @Bean
    public OAuthRevocationTokenService revocationTokenService() {
        return new OAuthRevocationTokenServiceImpl();
    }

    @Bean
    public ClientAssertionService clientAssertionService() {
        return new ClientAssertionServiceImpl();
    }

    @Bean
    public ScopeManager scopeManager() {
        return new ScopeManagerImpl();
    }

        @Bean
        public ResourceValidationService resourceValidationService(ProtectedResourceManager protectedResourceManager, Environment environment) {
            return new ResourceValidationServiceImpl(protectedResourceManager, environment);
        }

        @Bean
        public ResourceConsistencyValidationService resourceConsistencyValidationService(Environment environment) {
            return new ResourceConsistencyValidationServiceImpl(environment);
        }

    @Bean
    public ProtocolProvider oAuth2Provider() {
        return new OAuth2Provider();
    }

    @Bean
    public TokenManager tokenManager() {
        return new TokenManagerImpl();
    }
}
