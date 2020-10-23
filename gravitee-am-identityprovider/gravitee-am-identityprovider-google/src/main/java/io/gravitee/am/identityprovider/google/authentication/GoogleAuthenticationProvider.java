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
package io.gravitee.am.identityprovider.google.authentication;

import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.common.oauth2.authentication.AbstractOpenIDConnectAuthenticationProvider;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.remote.RemoteJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.JWKSKeyProcessor;
import io.gravitee.am.identityprovider.google.GoogleIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.google.authentication.spring.GoogleAuthenticationProviderConfiguration;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.util.HashSet;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(GoogleAuthenticationProviderConfiguration.class)
public class GoogleAuthenticationProvider extends AbstractOpenIDConnectAuthenticationProvider<GoogleIdentityProviderConfiguration> {

    @Autowired
    @Qualifier("googleWebClient")
    private WebClient client;

    @Autowired
    private DefaultIdentityProviderMapper mapper;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Autowired
    private GoogleIdentityProviderConfiguration configuration;

    @Override
    public OpenIDConnectIdentityProviderConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    protected WebClient getClient() {
        return this.client;
    }

    public void setJwtProcessor(JWTProcessor jwtProcessor) {
        this.jwtProcessor = jwtProcessor;
    }

    @Override
    protected IdentityProviderMapper getIdentityProviderMapper() {
        return this.mapper;
    }

    @Override
    protected IdentityProviderRoleMapper getIdentityProviderRoleMapper() {
        return this.roleMapper;
    }

    private void forceOpenIdScope() {
        if (configuration.getScopes() == null) {
            configuration.setScopes(new HashSet<>());
        }
        configuration.getScopes().add(Scope.OPENID.getKey());
        configuration.getScopes().add(Scope.PROFILE.getKey());
        configuration.getScopes().add(Scope.EMAIL.getKey());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        GoogleIdentityProviderConfiguration configuration = this.configuration;

        // check configuration
        // a client secret is required if authorization code flow is used
        if (io.gravitee.am.common.oauth2.ResponseType.CODE.equals(configuration.getResponseType())
                && (configuration.getClientSecret() == null || configuration.getClientSecret().isEmpty())) {
            throw new IllegalArgumentException("A client_secret must be supplied in order to use the Authorization Code flow");
        }

        forceOpenIdScope();

        // generate jwt processor if we try to fetch user information from the ID Token
        generateJWTProcessor();
    }

    private void generateJWTProcessor() {
        final SignatureAlgorithm signature = SignatureAlgorithm.RS256;
        JWKSKeyProcessor keyProcessor = new JWKSKeyProcessor<>();
        keyProcessor.setJwkSourceResolver(new RemoteJWKSourceResolver(configuration.getResolverParameter()));
        jwtProcessor = keyProcessor.create(signature);
    }
}