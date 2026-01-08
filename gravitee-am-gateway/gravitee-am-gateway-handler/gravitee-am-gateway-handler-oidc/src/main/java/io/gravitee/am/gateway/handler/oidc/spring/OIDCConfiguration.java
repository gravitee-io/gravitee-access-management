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
package io.gravitee.am.gateway.handler.oidc.spring;

import io.gravitee.am.gateway.handler.api.ProtocolConfiguration;
import io.gravitee.am.gateway.handler.ciba.spring.CIBAConfiguration;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.par.impl.PushedAuthorizationRequestServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.spring.OAuth2Configuration;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.ClientSecretService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.ClientService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.ClientSecretServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.ClientServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.DynamicClientRegistrationServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.impl.OpenIDDiscoveryServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.flow.CompositeFlow;
import io.gravitee.am.gateway.handler.oidc.service.flow.Flow;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.impl.IDTokenServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.jwe.impl.JWEServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.impl.JWKServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.jws.impl.JWSServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.gateway.handler.oidc.service.request.impl.RequestObjectServiceImpl;
import io.gravitee.am.gateway.handler.uma.spring.UMAConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({OAuth2Configuration.class, UMAConfiguration.class, CIBAConfiguration.class})
public class OIDCConfiguration implements ProtocolConfiguration {

    @Bean
    public Flow flow() {
        return new CompositeFlow();
    }

    @Bean
    public OpenIDDiscoveryService openIDConfigurationService() {
        return new OpenIDDiscoveryServiceImpl();
    }

    @Bean
    public DynamicClientRegistrationService dcrService() {
        return new DynamicClientRegistrationServiceImpl();
    }

    @Bean
    public ClientSecretService clientSecretService() {
        return new ClientSecretServiceImpl();
    }

    @Bean
    public ClientService clientService() {
        return new ClientServiceImpl();
    }

    @Bean
    public IDTokenService idTokenService() {
        return new IDTokenServiceImpl();
    }

    @Bean
    public JWEService jweService() {
        return new JWEServiceImpl();
    }

    @Bean
    public JWKService jwkService() {
        return new JWKServiceImpl();
    }

    @Bean
    public JWSService jwsService() {
        return new JWSServiceImpl();
    }

    @Bean
    public RequestObjectService requestObjectService() {
        return new RequestObjectServiceImpl();
    }

    @Bean
    public PushedAuthorizationRequestService pushedAuthorizationRequestService() {
        return new PushedAuthorizationRequestServiceImpl();
    }
}
