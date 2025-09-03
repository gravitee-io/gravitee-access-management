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

import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.identityprovider.api.IdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.oidc.jwt.KeyResolver;
import io.gravitee.am.identityprovider.common.oauth2.authentication.AbstractOpenIDConnectAuthenticationProvider;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.social.ProviderResponseType;
import io.gravitee.am.identityprovider.oauth2.authentication.spring.OAuth2GenericAuthenticationProviderConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(OAuth2GenericAuthenticationProviderConfiguration.class)
public class OAuth2GenericAuthenticationProvider extends AbstractOpenIDConnectAuthenticationProvider {

    private static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
    private static final String TOKEN_ENDPOINT = "token_endpoint";
    private static final String USERINFO_ENDPOINT = "userinfo_endpoint";
    private static final String END_SESSION_ENDPOINT = "end_session_endpoint";
    private static final String JWKS_ENDPOINT = "jwks_uri";

    @Autowired
    @Qualifier("oauthWebClient")
    private WebClient client;

    @Autowired
    private IdentityProviderMapper mapper;

    @Autowired
    private IdentityProviderRoleMapper roleMapper;

    @Autowired
    private IdentityProviderGroupMapper groupMapper;

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
    protected IdentityProviderGroupMapper getIdentityProviderGroupMapper() {
        return this.groupMapper;
    }

    @Override
    protected WebClient getClient() {
        return this.client;
    }

    @Override
    public void afterPropertiesSet() {
        // check configuration
        // a client secret is required if authorization code flow is used
        if(ProviderResponseType.CODE.equals(configuration.getProviderResponseType())
                && (configuration.getClientSecret() == null || configuration.getClientSecret().isEmpty())) {
            throw new IllegalArgumentException("A client_secret must be supplied in order to use the Authorization Code flow");
        }

        initializeAuthProvider().subscribe();
    }

    protected Completable initializeAuthProvider() {
        // fetch OpenID Provider information
        final RetryWithDelay retryHandler = new RetryWithDelay();
        return getOpenIDProviderConfiguration(configuration)
                .doOnError(error -> LOGGER.warn("Unable to load configuration from '{}' due to : {}", configuration.getWellKnownUri(), error.getMessage()))
                .retryWhen(retryHandler)
                .andThen(Completable.fromAction(this::generateJWTProcessor));
    }

    private Completable getOpenIDProviderConfiguration(OAuth2GenericIdentityProviderConfiguration configuration) {
        // fetch OpenID Provider information
        if (configuration.getWellKnownUri() == null || configuration.getWellKnownUri().isEmpty()) {
            return Completable.complete();
        }
        return client.getAbs(configuration.getWellKnownUri())
                        .rxSend()
                        .map(httpClientResponse -> {
                            if (httpClientResponse.statusCode() != 200) {
                                throw new IllegalArgumentException("Invalid OIDC Well-Known Endpoint : " + httpClientResponse.statusMessage());
                            }
                            return httpClientResponse.bodyAsJsonObject().getMap();
                        }).flatMap(providerConfiguration -> {
                                    try {
                                        if (providerConfiguration.containsKey(AUTHORIZATION_ENDPOINT)) {
                                            configuration.setUserAuthorizationUri((String) providerConfiguration.get(AUTHORIZATION_ENDPOINT));
                                        }
                                        if (providerConfiguration.containsKey(TOKEN_ENDPOINT)) {
                                            configuration.setAccessTokenUri((String) providerConfiguration.get(TOKEN_ENDPOINT));
                                        }
                                        if (providerConfiguration.containsKey(USERINFO_ENDPOINT)) {
                                            configuration.setUserProfileUri((String) providerConfiguration.get(USERINFO_ENDPOINT));
                                        }
                                        if (providerConfiguration.containsKey(END_SESSION_ENDPOINT)) {
                                            configuration.setLogoutUri((String) providerConfiguration.get(END_SESSION_ENDPOINT));
                                        }

                                        // try to use the JWKS provided by the well-known endpoint if it is not specified into the configuration form
                                        if (configuration.getPublicKeyResolver() == KeyResolver.JWKS_URL && ObjectUtils.isEmpty(configuration.getResolverParameter())) {
                                            configuration.setResolverParameter((String) providerConfiguration.get(JWKS_ENDPOINT));
                                        }

                                        // configuration verification
                                        Assert.notNull(configuration.getUserAuthorizationUri(), "OAuth 2.0 Authorization endpoint is required");

                                        if (configuration.getAccessTokenUri() == null && ProviderResponseType.CODE.equals(configuration.getProviderResponseType())) {
                                            return Single.error(new IllegalStateException("OAuth 2.0 token endpoint is required for the Authorization code flow"));
                                        }

                                        if (configuration.getUserProfileUri() == null && !configuration.isUseIdTokenForUserInfo()) {
                                            return Single.error(new IllegalStateException("OpenID Connect UserInfo Endpoint is required to retrieve user information"));
                                        }
                                    } catch (Exception e) {
                                        return Single.error(new IllegalArgumentException("Invalid OIDC Well-Known Endpoint : " + e.getMessage()));
                                    }
                                    return Single.just(providerConfiguration);
                                }
                        ).ignoreElement();
    }

    /**
     * trigger a retry with a delay of 1 second up to 60 seconds.
     */
    private static class RetryWithDelay implements Function<Flowable<Throwable>, Publisher<?>> {

        private int delayInSec = 0;

        @Override
        public Publisher<?> apply(Flowable<Throwable> throwableFlowable) throws Exception {
            return throwableFlowable.flatMap(err-> {
                if (delayInSec < 60) {
                    delayInSec = delayInSec + Math.max(delayInSec, 1);
                }
                return Flowable.timer(delayInSec, TimeUnit.SECONDS);
            });
        }
    }

    void setJwtProcessor(JWTProcessor jwtProcessor) {
        this.jwtProcessor = jwtProcessor;
    }

}
