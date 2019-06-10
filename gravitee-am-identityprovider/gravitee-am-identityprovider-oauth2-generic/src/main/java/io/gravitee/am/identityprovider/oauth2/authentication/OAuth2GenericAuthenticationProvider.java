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
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderMapper;
import io.gravitee.am.identityprovider.oauth2.authentication.spring.OAuth2GenericAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.oauth2.jwt.algo.Signature;
import io.gravitee.am.identityprovider.oauth2.jwt.jwks.hmac.MACJWKSourceResolver;
import io.gravitee.am.identityprovider.oauth2.jwt.jwks.remote.RemoteJWKSourceResolver;
import io.gravitee.am.identityprovider.oauth2.jwt.jwks.rsa.RSAJWKSourceResolver;
import io.gravitee.am.identityprovider.oauth2.jwt.processor.AbstractKeyProcessor;
import io.gravitee.am.identityprovider.oauth2.jwt.processor.HMACKeyProcessor;
import io.gravitee.am.identityprovider.oauth2.jwt.processor.JWKSKeyProcessor;
import io.gravitee.am.identityprovider.oauth2.jwt.processor.RSAKeyProcessor;
import io.gravitee.am.identityprovider.oauth2.resolver.KeyResolver;
import io.gravitee.am.identityprovider.oauth2.utils.URLEncodedUtils;
import io.gravitee.am.model.http.BasicNameValuePair;
import io.gravitee.am.model.http.NameValuePair;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(OAuth2GenericAuthenticationProviderConfiguration.class)
public class OAuth2GenericAuthenticationProvider implements OAuth2AuthenticationProvider, InitializingBean {

    private static final String ACCESS_TOKEN_PARAMETER = "access_token";
    private static final String ID_TOKEN_PARAMETER = "id_token";
    private static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
    private static final String TOKEN_ENDPOINT = "token_endpoint";
    private static final String USERINFO_ENDPOINT = "userinfo_endpoint";

    @Autowired
    private WebClient client;

    @Autowired
    private OAuth2IdentityProviderConfiguration configuration;

    @Autowired
    private OAuth2GenericIdentityProviderMapper mapper;

    private JWTProcessor jwtProcessor;

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return authenticate(authentication)
                .flatMap(token -> profile(token, authentication));
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return Maybe.empty();
    }

    @Override
    public OAuth2IdentityProviderConfiguration configuration() {
        return configuration;
    }

    private Maybe<Token> authenticate(Authentication authentication) {
        // implicit flow was used with response_type=id_token token, access token is already fetched, continue
        if (authentication.getAdditionalInformation().containsKey(ACCESS_TOKEN_PARAMETER)) {
            String accessToken = (String) authentication.getAdditionalInformation().get(ACCESS_TOKEN_PARAMETER);
            return Maybe.just(new Token(accessToken, TokenTypeHint.ACCESS_TOKEN));
        }

        // implicit flow was used with response_type=id_token, id token is already fetched, continue
        if (authentication.getAdditionalInformation().containsKey(ID_TOKEN_PARAMETER)) {
            String idToken = (String) authentication.getAdditionalInformation().get(ID_TOKEN_PARAMETER);
            return Maybe.just(new Token(idToken, TokenTypeHint.ID_TOKEN));
        }

        // authorization code flow, exchange code for an access token
        // prepare body request parameters
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair(Parameters.CLIENT_ID, configuration.getClientId()));
        urlParameters.add(new BasicNameValuePair(Parameters.CLIENT_SECRET, configuration.getClientSecret()));
        urlParameters.add(new BasicNameValuePair(Parameters.REDIRECT_URI, (String) authentication.getAdditionalInformation().get(Parameters.REDIRECT_URI)));
        urlParameters.add(new BasicNameValuePair(Parameters.CODE, (String) authentication.getCredentials()));
        urlParameters.add(new BasicNameValuePair(Parameters.GRANT_TYPE, "authorization_code"));
        String bodyRequest = URLEncodedUtils.format(urlParameters);

        return client.postAbs(configuration.getAccessTokenUri())
                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyRequest.length()))
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
                .rxSendBuffer(Buffer.buffer(bodyRequest))
                .toMaybe()
                .map(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpResponse.statusMessage());
                    }

                    String accessToken = httpResponse.bodyAsJsonObject().getString(ACCESS_TOKEN_PARAMETER);
                    return new Token(accessToken, TokenTypeHint.ACCESS_TOKEN);
                });

    }

    private Maybe<User> profile(Token token, Authentication authentication) {
        // we only have the id_token, try to decode it and create the end-user
        if (TokenTypeHint.ID_TOKEN.equals(token.getTypeHint())) {
            return retrieveUserFromIdToken(token.getValue());
        }

        // if it's an access token but user ask for id token verification, try to decode it and create the end-user
        if (TokenTypeHint.ACCESS_TOKEN.equals(token.getTypeHint())
                && ((OAuth2GenericIdentityProviderConfiguration) configuration).isUseIdTokenForUserInfo()) {
            if (authentication.getAdditionalInformation().containsKey(ID_TOKEN_PARAMETER)) {
                String idToken = (String) authentication.getAdditionalInformation().get(ID_TOKEN_PARAMETER);
                return retrieveUserFromIdToken(idToken);
            } else {
                // no suitable value to retrieve user
                return Maybe.error(new BadCredentialsException("No suitable value to retrieve user information"));
            }
        }

        // retrieve user claims from the UserInfo Endpoint
        return client.getAbs(configuration.getUserProfileUri())
                .putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.getValue())
                .rxSend()
                .toMaybe()
                .map(httpClientResponse -> {
                    if (httpClientResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpClientResponse.statusMessage());
                    }

                    return createUser(httpClientResponse.bodyAsJsonObject().getMap());
                });
    }

    private Maybe<User> retrieveUserFromIdToken(String idToken) {
        return Maybe.fromCallable(() -> jwtProcessor.process(idToken, null))
                .onErrorResumeNext(ex -> {
                    return Maybe.error(new BadCredentialsException(ex.getMessage()));
                })
                .map(jwtClaimsSet -> createUser(jwtClaimsSet.getClaims()));
    }

    private User createUser(Map<String, Object> jsonNode) {
        String username = (String) jsonNode.getOrDefault(StandardClaims.PREFERRED_USERNAME, jsonNode.get(StandardClaims.SUB));
        User user = new DefaultUser(username);
        ((DefaultUser) user).setId((String) jsonNode.get(StandardClaims.SUB));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, jsonNode.get(StandardClaims.SUB));
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, username);
        if (this.mapper != null && this.mapper.getMappers() != null && !this.mapper.getMappers().isEmpty()) {
            this.mapper.getMappers().forEach((k, v) -> {
                if (jsonNode.containsValue(v)) {
                    additionalInformation.put(k, jsonNode.get(v));
                }
            });
        } else {
            // set default standard claims
            StandardClaims.claims().stream()
                    .filter(claimName -> jsonNode.containsKey(claimName))
                    .forEach(claimName -> additionalInformation.put(claimName, jsonNode.get(claimName)));
        }
        ((DefaultUser) user).setAdditionalInformation(additionalInformation);
        return user;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        OAuth2GenericIdentityProviderConfiguration configuration = (OAuth2GenericIdentityProviderConfiguration) this.configuration;

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
            client.getAbs(configuration.getWellKnownUri())
                    .rxSend()
                    .map(httpClientResponse -> {
                        if (httpClientResponse.statusCode() != 200) {
                            throw new IllegalArgumentException("Invalid OIDC Well-Known Endpoint " + httpClientResponse.statusMessage());
                        }
                        return httpClientResponse.bodyAsJsonObject().getMap();
                    })
                    .subscribe(
                            providerConfiguration -> {
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
                                Assert.notNull(configuration.getUserAuthorizationUri(), "OAuth 2.0 Authoriziation endpoint is required");

                                if (configuration.getAccessTokenUri() == null && io.gravitee.am.common.oauth2.ResponseType.CODE.equals(configuration.getResponseType())) {
                                    throw new IllegalStateException("OAuth 2.0 token endpoint is required for the Authorization code flow");
                                }

                                if (configuration.getUserProfileUri() == null && !configuration.isUseIdTokenForUserInfo()) {
                                    throw new IllegalStateException("OpenID Connect UserInfo Endpoint is required to retrieve user information");
                                }
                            },
                            error -> { throw new IllegalStateException(error.getMessage()); }
                    );
        }
    }

    private void generateJWTProcessor(OAuth2GenericIdentityProviderConfiguration configuration) {
        if (configuration.isUseIdTokenForUserInfo() || ResponseType.ID_TOKEN.equals(configuration.getResponseType())) {
            if (configuration.getPublicKeyResolver() == null || configuration.getResolverParameter() == null) {
                throw new IllegalStateException("An public key resolver must be supply to verify the ID Token");
            }

            final Signature signature = (configuration.getSignature() == null) ? Signature.RSA_RS256 : configuration.getSignature();

            AbstractKeyProcessor keyProcessor = null;
            // init JWT key source (Remote URL or from configuration file)
            if (KeyResolver.JWKS_URL.equals(configuration.getPublicKeyResolver())) {
                keyProcessor = new JWKSKeyProcessor();
                keyProcessor.setJwkSourceResolver(new RemoteJWKSourceResolver(configuration.getResolverParameter()));
            } else {
                // get the corresponding key processor
                final String resolverParameter = configuration.getResolverParameter();
                switch (signature) {
                    case RSA_RS256:
                    case RSA_RS384:
                    case RSA_RS512:
                        keyProcessor = new RSAKeyProcessor();
                        keyProcessor.setJwkSourceResolver(new RSAJWKSourceResolver(resolverParameter));
                        break;
                    case HMAC_HS256:
                    case HMAC_HS384:
                    case HMAC_HS512:
                        keyProcessor = new HMACKeyProcessor();
                        keyProcessor.setJwkSourceResolver(new MACJWKSourceResolver(resolverParameter));
                        break;
                }
            }

            Assert.notNull(keyProcessor, "A key processor must be set");
            jwtProcessor = keyProcessor.create(signature);
        }
    }

    private class Token {
        private String value;
        private TokenTypeHint typeHint;

        public Token(String value, TokenTypeHint typeHint) {
            this.value = value;
            this.typeHint = typeHint;
        }

        public String getValue() {
            return value;
        }

        public TokenTypeHint getTypeHint() {
            return typeHint;
        }
    }
}
