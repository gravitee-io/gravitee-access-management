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
package io.gravitee.am.identityprovider.azure.authentication;

import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.*;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectAuthenticationProvider;
import io.gravitee.am.identityprovider.azure.AzureADIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.azure.AzureADIdentityProviderMapper;
import io.gravitee.am.identityprovider.azure.AzureADIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.azure.authentication.spring.AzureADAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.azure.jwt.jwks.remote.RemoteJWKSourceResolver;
import io.gravitee.am.identityprovider.azure.jwt.processor.JWKSKeyProcessor;
import io.gravitee.am.identityprovider.azure.utils.URLEncodedUtils;
import io.gravitee.am.model.http.BasicNameValuePair;
import io.gravitee.am.model.http.NameValuePair;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(AzureADAuthenticationProviderConfiguration.class)
public class AzureADAuthenticationProvider implements OpenIDConnectAuthenticationProvider, InitializingBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureADAuthenticationProvider.class);

    private static final String DEFAULT_RESPONSE_TYPE = "code";

    private static final String ID_TOKEN_PARAMETER = "id_token";

    public static final String HOST_MICROSOFT_LOGIN = "https://login.microsoftonline.com/";
    public static final String AUTHORIZATION_PATH = "/oauth2/v2.0/authorize";
    public static final String TOKEN_PATH = "/oauth2/v2.0/token";
    public static final String JWKS_PATH = "/discovery/v2.0/keys";

    @Autowired
    @Qualifier("azureAdWebClient")
    private WebClient client;

    @Autowired
    private AzureADIdentityProviderMapper mapper;

    @Autowired
    private AzureADIdentityProviderRoleMapper roleMapper;

    @Autowired
    private AzureADIdentityProviderConfiguration configuration;

    private JWTProcessor jwtProcessor;

    public String getUserAuthorizationUri() {
        return HOST_MICROSOFT_LOGIN + configuration.getTenantId() + AUTHORIZATION_PATH;
    }

    public String getJwksUri() {
        return HOST_MICROSOFT_LOGIN + configuration.getTenantId() + JWKS_PATH;
    }

    public String getTokenUri() {
        return HOST_MICROSOFT_LOGIN + configuration.getTenantId() + TOKEN_PATH;
    }

    public String getResponseType() {
        return DEFAULT_RESPONSE_TYPE;
    }

    public void setJwtProcessor(JWTProcessor jwtProcessor) {
        this.jwtProcessor = jwtProcessor;
    }

    @Override
    public Request signInUrl(String redirectUri) {
        try {
            UriBuilder builder = UriBuilder.fromHttpUrl(getUserAuthorizationUri());
            builder.addParameter(Parameters.CLIENT_ID, configuration.getClientId());
            builder.addParameter(Parameters.RESPONSE_TYPE, getResponseType());

            forceOpenIdScope();

            // append scopes
            if (configuration.getScopes() != null && !configuration.getScopes().isEmpty()) {
                builder.addParameter(Parameters.SCOPE, String.join(SCOPE_DELIMITER, configuration.getScopes()));
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

    private void forceOpenIdScope() {
        if (configuration.getScopes() == null) {
            configuration.setScopes(new HashSet<>());
        }
        configuration.getScopes().add(Scope.OPENID.getKey());
    }

    @Override
    public AuthenticationFlow authenticationFlow() {
        return AuthenticationFlow.AUTHORIZATION_CODE_FLOW;
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

        return client.postAbs(getTokenUri())
                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyRequest.length()))
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
                .rxSendBuffer(Buffer.buffer(bodyRequest))
                .toMaybe()
                .map(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpResponse.statusMessage());
                    }

                    JsonObject response = httpResponse.bodyAsJsonObject();
                    // put the id_token in context for later use
                    String idToken = response.getString(ID_TOKEN_PARAMETER);
                    authentication.getContext().set(ID_TOKEN_PARAMETER, idToken);
                    return new Token(idToken, TokenTypeHint.ID_TOKEN);
                });

    }

    private Maybe<User> profile(Token token, Authentication authentication) {
        return retrieveUserFromIdToken(token.getValue());
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
        AzureADIdentityProviderConfiguration configuration = this.configuration;

        // check configuration
        // a client secret is required if authorization code flow is used
        if (io.gravitee.am.common.oauth2.ResponseType.CODE.equals(getResponseType())
                && (configuration.getClientSecret() == null || configuration.getClientSecret().isEmpty())) {
            throw new IllegalArgumentException("A client_secret must be supplied in order to use the Authorization Code flow");
        }

        // generate jwt processor if we try to fetch user information from the ID Token
        generateJWTProcessor();
    }

    private void generateJWTProcessor() {
        final SignatureAlgorithm signature = SignatureAlgorithm.RS256;
        JWKSKeyProcessor keyProcessor = new JWKSKeyProcessor();
        keyProcessor.setJwkSourceResolver(new RemoteJWKSourceResolver(getJwksUri()));
        jwtProcessor = keyProcessor.create(signature);
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