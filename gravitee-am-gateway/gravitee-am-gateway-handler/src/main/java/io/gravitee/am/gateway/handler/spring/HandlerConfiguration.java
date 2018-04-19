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
package io.gravitee.am.gateway.handler.spring;

import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.auth.impl.UserAuthenticationManagerImpl;
import io.gravitee.am.gateway.handler.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.idp.impl.IdentityProviderManagerImpl;
import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.approval.impl.ApprovalServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.client.impl.ClientServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.code.impl.AuthorizationCodeServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.granter.CompositeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.introspection.impl.IntrospectionServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.scope.ScopeService;
import io.gravitee.am.gateway.handler.oauth2.scope.impl.ScopeServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.TokenServiceImpl;
import io.gravitee.am.gateway.handler.oidc.discovery.spring.OpenIDDiscoveryConfiguration;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.gateway.handler.user.impl.UserServiceImpl;
import io.gravitee.am.gateway.handler.vertx.spring.SecurityDomainRouterConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({
        OpenIDDiscoveryConfiguration.class,
        SecurityDomainRouterConfiguration.class
})
public class HandlerConfiguration {

    @Bean
    public TokenGranter tokenGranter() {
        return new CompositeTokenGranter();
    }

    @Bean
    public ClientService clientService() {
        return new ClientServiceImpl();
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
    public IdentityProviderManager identityProviderManager() {
        return new IdentityProviderManagerImpl();
    }

    @Bean
    public UserAuthenticationManager userAuthenticationManager() {
        return new UserAuthenticationManagerImpl();
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
    public UserService userService() {
        return new UserServiceImpl();
    }
}
