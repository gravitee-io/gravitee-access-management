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

import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.common.oauth2.authentication.AbstractOpenIDConnectAuthenticationProvider;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.oauth2.authentication.spring.OAuth2GenericAuthenticationProviderConfiguration;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(OAuth2GenericAuthenticationProviderConfiguration.class)
public class OAuth2GenericAuthenticationProvider extends AbstractOpenIDConnectAuthenticationProvider<OAuth2GenericIdentityProviderConfiguration> {

    private static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
    private static final String TOKEN_ENDPOINT = "token_endpoint";
    private static final String USERINFO_ENDPOINT = "userinfo_endpoint";

    @Autowired
    @Qualifier("oauthWebClient")
    private WebClient client;

    @Autowired
    private DefaultIdentityProviderMapper mapper;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Autowired
    private OAuth2GenericIdentityProviderConfiguration configuration;

    @Override
    public OpenIDConnectIdentityProviderConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    protected IdentityProviderMapper getIdentityProviderMapper() {
        return this.mapper;
    }

    @Override
    protected IdentityProviderRoleMapper getIdentityProviderRoleMapper() {
        return this.roleMapper;
    }

    @Override
    protected WebClient getClient() {
        return this.client;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        OAuth2GenericIdentityProviderConfiguration configuration = this.configuration;

        // check configuration
        // a client secret is required if authorization code flow is used
        if (io.gravitee.am.common.oauth2.ResponseType.CODE.equals(configuration.getResponseType())
                && (configuration.getClientSecret() == null || configuration.getClientSecret().isEmpty())) {
            throw new IllegalArgumentException("A client_secret must be supplied in order to use the Authorization Code flow");
        }

        // fetch OpenID Provider information
        getOpenIDProviderConfiguration(configuration);

        // generate jwt processor if we try to fetch user information from the ID Token
        generateJWTProcessor(configuration);
    }

    private void getOpenIDProviderConfiguration(OAuth2GenericIdentityProviderConfiguration configuration) {
        // fetch OpenID Provider information
        if (configuration.getWellKnownUri() != null && !configuration.getWellKnownUri().isEmpty()) {
            try {
                Map<String, Object> providerConfiguration = client.getAbs(configuration.getWellKnownUri())
                        .rxSend()
                        .map(httpClientResponse -> {
                            if (httpClientResponse.statusCode() != 200) {
                                throw new IllegalArgumentException("Invalid OIDC Well-Known Endpoint : " + httpClientResponse.statusMessage());
                            }
                            return httpClientResponse.bodyAsJsonObject().getMap();
                        }).blockingGet();

                if (providerConfiguration.containsKey(AUTHORIZATION_ENDPOINT)) {
                    configuration.setUserAuthorizationUri((String) providerConfiguration.get(AUTHORIZATION_ENDPOINT));
                }
                if (providerConfiguration.containsKey(TOKEN_ENDPOINT)) {
                    configuration.setAccessTokenUri((String) providerConfiguration.get(TOKEN_ENDPOINT));
                }
                if (providerConfiguration.containsKey(USERINFO_ENDPOINT)) {
                    configuration.setUserProfileUri((String) providerConfiguration.get(USERINFO_ENDPOINT));
                }

                // configuration verification
                Assert.notNull(configuration.getUserAuthorizationUri(), "OAuth 2.0 Authorization endpoint is required");

                if (configuration.getAccessTokenUri() == null && io.gravitee.am.common.oauth2.ResponseType.CODE.equals(configuration.getResponseType())) {
                    throw new IllegalStateException("OAuth 2.0 token endpoint is required for the Authorization code flow");
                }

                if (configuration.getUserProfileUri() == null && !configuration.isUseIdTokenForUserInfo()) {
                    throw new IllegalStateException("OpenID Connect UserInfo Endpoint is required to retrieve user information");
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage());
            }
        }
    }
}
