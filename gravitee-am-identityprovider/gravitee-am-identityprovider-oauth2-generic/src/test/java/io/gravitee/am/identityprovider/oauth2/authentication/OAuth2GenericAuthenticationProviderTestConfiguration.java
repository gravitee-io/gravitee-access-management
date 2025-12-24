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
package io.gravitee.am.identityprovider.oauth2.authentication;

import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.ResourceRetriever;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.social.ProviderResponseType;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderConfiguration;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.http.WebClientBuilder;
import io.vertx.rxjava3.core.Vertx;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Properties;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class OAuth2GenericAuthenticationProviderTestConfiguration {

    @Autowired
    private Environment environment;

    @Bean
    public OAuth2GenericIdentityProviderConfiguration oAuth2GenericIdentityProviderConfiguration() {
        OAuth2GenericIdentityProviderConfiguration configuration = new OAuth2GenericIdentityProviderConfiguration();
        configuration.setResponseType(ProviderResponseType.CODE);
        configuration.setClientId("test-client-id");
        configuration.setClientSecret("test-client-secret");
        configuration.setAccessTokenUri("http://localhost:19999/oauth/token");
        configuration.setUserAuthorizationUri("http://localhost:19999/oauth/authorize");
        configuration.setUserProfileUri("http://localhost:19999/profile");

        return configuration;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return new OAuth2GenericAuthenticationProvider();
    }

    @Bean
    public DefaultIdentityProviderMapper mapper() {
        return new DefaultIdentityProviderMapper();
    }

    @Bean
    public DefaultIdentityProviderRoleMapper roleMapper() {
        return new DefaultIdentityProviderRoleMapper();
    }

    @Bean
    public DefaultIdentityProviderGroupMapper groupMapper() {
        return new DefaultIdentityProviderGroupMapper();
    }

    @Bean
    public CertificateService certificateService() {
        return Mockito.mock();
    }

    @Bean("graviteeProperties")
    public Properties properties() {
        return new Properties();
    }

    @Bean
    public Vertx vertx() {
        return Vertx.vertx();
    }

    @Bean
    public WebClientBuilder webClientBuilder() {
        return new WebClientBuilder(environment);
    }

    @Bean
    public ResourceRetriever defaultResourceRetriever(){
        return new DefaultResourceRetriever();
    }
}
