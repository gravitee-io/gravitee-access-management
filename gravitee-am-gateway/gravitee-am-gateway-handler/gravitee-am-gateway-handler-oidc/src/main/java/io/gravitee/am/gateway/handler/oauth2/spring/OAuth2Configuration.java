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
import io.gravitee.am.gateway.handler.oauth2.service.revocation.RevocationTokenService;
import io.gravitee.am.gateway.handler.oauth2.service.revocation.impl.RevocationTokenServiceImpl;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public IntrospectionService introspectionService() {
        return new IntrospectionServiceImpl();
    }

    @Bean
    public AuthorizationCodeService authorizationCodeService() {
        return new AuthorizationCodeServiceImpl();
    }

    @Bean
    public UserConsentService userConsentService() {
        return new UserConsentServiceImpl();
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
    public RevocationTokenService revocationTokenService() {
        return new RevocationTokenServiceImpl();
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
    public ProtocolProvider oAuth2Provider() {
        return new OAuth2Provider();
    }

    @Bean
    public TokenManager tokenManager() {
        return new TokenManagerImpl();
    }
}
