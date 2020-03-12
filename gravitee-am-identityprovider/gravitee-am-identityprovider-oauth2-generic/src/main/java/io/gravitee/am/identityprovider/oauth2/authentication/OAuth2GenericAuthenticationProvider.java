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
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.AuthenticationFlow;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectAuthenticationProvider;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderMapper;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderRoleMapper;
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
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(OAuth2GenericAuthenticationProviderConfiguration.class)
public class OAuth2GenericAuthenticationProvider implements OpenIDConnectAuthenticationProvider, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2GenericAuthenticationProvider.class);
    private static final String HASH_VALUE_PARAMETER = "urlHash";
    private static final String ACCESS_TOKEN_PARAMETER = "access_token";
    private static final String ID_TOKEN_PARAMETER = "id_token";
    private static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
    private static final String TOKEN_ENDPOINT = "token_endpoint";
    private static final String USERINFO_ENDPOINT = "userinfo_endpoint";

    @Autowired
    private WebClient client;

    @Autowired
    private OAuth2GenericIdentityProviderMapper mapper;

    @Autowired
    private OAuth2GenericIdentityProviderRoleMapper roleMapper;

    @Autowired
    private OAuth2GenericIdentityProviderConfiguration configuration;

    private JWTProcessor jwtProcessor;

    @Override
    public Request signInUrl(String redirectUri) {
        try {
            UriBuilder builder = UriBuilder.fromHttpUrl(configuration.getUserAuthorizationUri());
            builder.addParameter(Parameters.CLIENT_ID, configuration.getClientId());
            builder.addParameter(Parameters.RESPONSE_TYPE, configuration.getResponseType());
            // append scopes
            if (configuration.getScopes() != null && !configuration.getScopes().isEmpty()) {
                builder.addParameter(Parameters.SCOPE, String.join(SCOPE_DELIMITER, configuration.getScopes()));
            }
            // nonce parameter is required for implicit/hybrid flow
            if (!io.gravitee.am.common.oauth2.ResponseType.CODE.equals(configuration.getResponseType())) {
                builder.addParameter(io.gravitee.am.common.oidc.Parameters.NONCE, SecureRandomString.generate());
            }
            // append redirect_uri
            builder.addParameter(Parameters.REDIRECT_URI, configuration.isEncodeRedirectUri() ? UriBuilder.encodeURIComponent(redirectUri) : redirectUri);

            Request request = new Request();
            request.setMethod(HttpMethod.GET);
            request.setUri(builder.buildString());
            return request;
        } catch (Exception e) {
            LOGGER.error("An error occurs while building OpenID Connect Sign In URL", e);
            return null;
        }
    }

    @Override
    public AuthenticationFlow authenticationFlow() {
        return io.gravitee.am.common.oauth2.ResponseType.CODE.equals(configuration.getResponseType()) ? AuthenticationFlow.AUTHORIZATION_CODE_FLOW : AuthenticationFlow.IMPLICIT_FLOW;
    }

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return authenticate(authentication)
                .flatMap(token -> profile(token, authentication));
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return Maybe.empty();
    }

    private Maybe<Token> authenticate(Authentication authentication) {
        // implicit flow, retrieve the hashValue of the URL (#access_token=....&token_type=...)
        if (AuthenticationFlow.IMPLICIT_FLOW.equals(authenticationFlow())){
            final String hashValue = authentication.getContext().request().parameters().getFirst(HASH_VALUE_PARAMETER);
            Map<String, String> hashValues = getParams(hashValue.substring(1));

            // implicit flow was used with response_type=id_token token, access token is already fetched, continue
            if (ResponseType.ID_TOKEN_TOKEN.equals(configuration.getResponseType())) {
                String accessToken = hashValues.get(ACCESS_TOKEN_PARAMETER);
                // put the id_token in context for later use
                authentication.getContext().set(ID_TOKEN_PARAMETER, hashValues.get(ID_TOKEN_PARAMETER));
                return Maybe.just(new Token(accessToken, TokenTypeHint.ACCESS_TOKEN));
            }

            // implicit flow was used with response_type=id_token, id token is already fetched, continue
            if (ResponseType.ID_TOKEN.equals(configuration.getResponseType())) {
                String idToken = hashValues.get(ID_TOKEN_PARAMETER);
                return Maybe.just(new Token(idToken, TokenTypeHint.ID_TOKEN));
            }
        }

        // authorization code flow, exchange code for an access token
        // prepare body request parameters
        final String authorizationCode = authentication.getContext().request().parameters().getFirst(configuration.getCodeParameter());
        if (authorizationCode == null || authorizationCode.isEmpty()) {
            LOGGER.debug("Authorization code is missing, skip authentication");
            return Maybe.error(new BadCredentialsException("Missing authorization code"));
        }
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair(Parameters.CLIENT_ID, configuration.getClientId()));
        urlParameters.add(new BasicNameValuePair(Parameters.CLIENT_SECRET, configuration.getClientSecret()));
        urlParameters.add(new BasicNameValuePair(Parameters.REDIRECT_URI, (String) authentication.getContext().get(Parameters.REDIRECT_URI)));
        urlParameters.add(new BasicNameValuePair(Parameters.CODE, authorizationCode));
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
        if (TokenTypeHint.ACCESS_TOKEN.equals(token.getTypeHint()) && configuration.isUseIdTokenForUserInfo()) {
            if (authentication.getContext().get(ID_TOKEN_PARAMETER) != null) {
                String idToken = (String) authentication.getContext().get(ID_TOKEN_PARAMETER);
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

    private User createUser(Map<String, Object> attributes) {
        String username = (String) attributes.getOrDefault(StandardClaims.PREFERRED_USERNAME, attributes.get(StandardClaims.SUB));
        User user = new DefaultUser(username);
        ((DefaultUser) user).setId((String) attributes.get(StandardClaims.SUB));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, attributes.get(StandardClaims.SUB));
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, username);
        // apply user mapping
        additionalInformation.putAll(applyUserMapping(attributes));
        // update username if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
            ((DefaultUser) user).setUsername((String) additionalInformation.get(StandardClaims.PREFERRED_USERNAME));
        }
        ((DefaultUser) user).setAdditionalInformation(additionalInformation);
        // set user roles
        ((DefaultUser) user).setRoles(applyRoleMapping(attributes));
        return user;
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
                Assert.notNull(configuration.getUserAuthorizationUri(), "OAuth 2.0 Authoriziation endpoint is required");

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

    private Map<String, String> getParams(String query) {
        Map<String, String> query_pairs = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return query_pairs;
    }

    private Map<String, Object> applyUserMapping(Map<String, Object> attributes) {
        if (!mappingEnabled()) {
            // set default standard claims
            return Stream.concat(StandardClaims.claims().stream(), CustomClaims.claims().stream())
                    .filter(claimName -> attributes.containsKey(claimName))
                    .collect(Collectors.toMap(claimName -> claimName, claimName -> attributes.get(claimName)));
        }

        Map<String, Object> claims = new HashMap<>();
        this.mapper.getMappers().forEach((k, v) -> {
            if (attributes.containsKey(v)) {
                claims.put(k, attributes.get(v));
            }
        });
        return claims;
    }

    private List<String> applyRoleMapping(Map<String, Object> attributes) {
        if (!roleMappingEnabled()) {
            return Collections.emptyList();
        }

        Set<String> roles = new HashSet<>();
        roleMapper.getRoles().forEach((role, users) -> {
            Arrays.asList(users).forEach(u -> {
                // role mapping have the following syntax userAttribute=userValue
                String[] roleMapping = u.split("=",2);
                String userAttribute = roleMapping[0];
                String userValue = roleMapping[1];
                if (attributes.containsKey(userAttribute)) {
                    Object attribute = attributes.get(userAttribute);
                    // attribute is a list
                    if (attribute instanceof Collection && ((Collection) attribute).contains(userValue)) {
                        roles.add(role);
                    } else if (userValue.equals(attributes.get(userAttribute))) {
                        roles.add(role);
                    }
                }
            });
        });

        return new ArrayList<>(roles);
    }

    private boolean mappingEnabled() {
        return this.mapper != null && this.mapper.getMappers() != null && !this.mapper.getMappers().isEmpty();
    }

    private boolean roleMappingEnabled() {
        return this.roleMapper != null && this.roleMapper.getRoles() != null && !this.roleMapper.getRoles().isEmpty();
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
