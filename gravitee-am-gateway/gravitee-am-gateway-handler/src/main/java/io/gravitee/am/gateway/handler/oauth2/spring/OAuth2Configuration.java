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

import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.approval.impl.ApprovalServiceImpl;
import io.gravitee.am.gateway.handler.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.certificate.impl.CertificateManagerImpl;
import io.gravitee.am.gateway.handler.oauth2.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oauth2.assertion.impl.ClientAssertionServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.client.impl.ClientSyncServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.code.impl.AuthorizationCodeServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.granter.CompositeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.extensiongrant.ExtensionGrantManager;
import io.gravitee.am.gateway.handler.oauth2.granter.extensiongrant.impl.ExtensionGrantManagerImpl;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.introspection.impl.IntrospectionServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.revocation.RevocationTokenService;
import io.gravitee.am.gateway.handler.oauth2.revocation.impl.RevocationTokenServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.scope.ScopeService;
import io.gravitee.am.gateway.handler.oauth2.scope.impl.ScopeServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.TokenEnhancerImpl;
import io.gravitee.am.gateway.handler.oauth2.token.impl.TokenServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class OAuth2Configuration {

    @Bean
    public TokenGranter tokenGranter() {
        return new CompositeTokenGranter();
    }

    @Bean
    public ClientSyncService clientService() {
        return new ClientSyncServiceImpl();
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
    public ApprovalService approvalService() {
        return new ApprovalServiceImpl();
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
    public CertificateManager certificateManager() { return new CertificateManagerImpl(); }

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
}
