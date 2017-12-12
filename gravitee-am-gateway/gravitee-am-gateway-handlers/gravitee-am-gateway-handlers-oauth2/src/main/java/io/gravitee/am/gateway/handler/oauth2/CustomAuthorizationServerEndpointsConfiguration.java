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
package io.gravitee.am.gateway.handler.oauth2;

import io.gravitee.am.gateway.handler.oauth2.provider.endpoint.RevokeTokenEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerEndpointsConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomAuthorizationServerEndpointsConfiguration extends AuthorizationServerEndpointsConfiguration {

    /*
    @Autowired
    private AuthorizationEndpoint authorizationEndpoint;

    @PostConstruct
    public void init() {
        authorizationEndpoint.setUserApprovalPage("forward:/oauth/scope_approval");
        authorizationEndpoint.setErrorPage("forward:/oauth/scope_approval_error");
    }

    @Bean
    public ScopeApprovalEndpoint approvalEndpoint() {
        return new ScopeApprovalEndpoint();
    }

    @Bean
    public ScopeApprovalErrorEndpoint approvalErrorEndpoint() {
        return new ScopeApprovalErrorEndpoint();
    }
    */

    /*
    @Bean
    public RevokeTokenEndpoint revokeTokenEndpoint() {
        RevokeTokenEndpoint endpoint = new RevokeTokenEndpoint(
                getEndpointsConfigurer().getTokenStore(),
                getEndpointsConfigurer().getResourceServerTokenServices());
        endpoint.setExceptionTranslator(getEndpointsConfigurer().getExceptionTranslator());
        return endpoint;
    }
    */
}
