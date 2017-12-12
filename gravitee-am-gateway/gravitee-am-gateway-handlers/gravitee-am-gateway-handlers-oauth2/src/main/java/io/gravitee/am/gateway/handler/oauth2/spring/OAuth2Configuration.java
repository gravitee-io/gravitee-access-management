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

import io.gravitee.am.gateway.handler.oauth2.AuthorizationServerConfiguration;
import io.gravitee.am.gateway.handler.oauth2.OAuth2SecurityConfiguration;
import io.gravitee.am.gateway.handler.oauth2.OpenIDResourceServerConfiguration;
import io.gravitee.am.gateway.handler.oauth2.WebMvcConfiguration;
import io.gravitee.am.gateway.handler.oauth2.provider.client.DomainBasedClientDetailsService;
import io.gravitee.am.gateway.handler.oauth2.provider.jwt.JwtKeyPairFactory;
import io.gravitee.am.gateway.handler.oauth2.security.CertificateManager;
import io.gravitee.am.gateway.handler.oauth2.security.ExtensionGrantManager;
import io.gravitee.am.gateway.handler.oauth2.security.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.security.impl.CertificateManagerImpl;
import io.gravitee.am.gateway.handler.oauth2.security.impl.IdentityProviderManagerImpl;
import io.gravitee.am.gateway.handler.oauth2.security.impl.ExtensionGrantManagerImpl;
import io.gravitee.am.gateway.handler.oauth2.service.DomainScopeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.provider.ClientDetailsService;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({
        WebMvcConfiguration.class,
        AuthorizationServerConfiguration.class,
        OAuth2SecurityConfiguration.class,
        OpenIDResourceServerConfiguration.class
})
public class OAuth2Configuration {

    @Bean
    public ClientDetailsService clientDetailsService() {
        return new DomainBasedClientDetailsService();
    }

    @Bean
    public JwtKeyPairFactory jwtKeyPairFactory() {
        return new JwtKeyPairFactory();
    }

    @Bean
    public IdentityProviderManager identityProviderManager() {
        return new IdentityProviderManagerImpl();
    }

    @Bean
    public CertificateManager certificateManager() {
        return new CertificateManagerImpl();
    }

    @Bean
    public ExtensionGrantManager tokenGranterManager() {
        return new ExtensionGrantManagerImpl();
    }

    @Bean
    public DomainScopeService scopeService() {
        return new DomainScopeService();
    }
}
