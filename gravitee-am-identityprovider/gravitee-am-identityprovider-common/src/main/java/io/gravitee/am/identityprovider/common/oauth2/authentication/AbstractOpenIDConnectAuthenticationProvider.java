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
package io.gravitee.am.identityprovider.common.oauth2.authentication;

import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.AuthenticationFlow;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectAuthenticationProvider;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.oidc.jwt.KeyResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.hmac.MACJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.remote.RemoteJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.rsa.RSAJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.AbstractKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.HMACKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.JWKSKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.RSAKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.utils.URLEncodedUtils;
import io.gravitee.am.model.http.BasicNameValuePair;
import io.gravitee.am.model.http.NameValuePair;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractOpenIDConnectAuthenticationProvider extends AbstractSocialAuthenticationProvider implements OpenIDConnectAuthenticationProvider, InitializingBean {

    public static final String HASH_VALUE_PARAMETER = "urlHash";
    public static final String ACCESS_TOKEN_PARAMETER = "access_token";
    public static final String ID_TOKEN_PARAMETER = "id_token";

    protected JWTProcessor jwtProcessor;

    @Override
    public AuthenticationFlow authenticationFlow() {
        return io.gravitee.am.common.oauth2.ResponseType.CODE.equals(getConfiguration().getResponseType()) ? AuthenticationFlow.AUTHORIZATION_CODE_FLOW : AuthenticationFlow.IMPLICIT_FLOW;
    }

    @Override
    protected Map<String, Object> defaultClaims(Map attributes) {
        return Stream.concat(StandardClaims.claims().stream(), CustomClaims.claims().stream())
                .filter(claimName -> attributes.containsKey(claimName))
                .filter(claimName -> attributes.get(claimName) != null) // sometimes values is null that throws a NPE during the collect phase
                .collect(Collectors.toMap(claimName -> claimName, claimName -> attributes.get(claimName)));
    }

    protected Maybe<User> retrieveUserFromIdToken(AuthenticationContext authContext, String idToken) {
        return Maybe.fromCallable(() -> jwtProcessor.process(idToken, null))
                .onErrorResumeNext(ex -> {
                    return Maybe.error(new BadCredentialsException(ex.getMessage()));
                })
                .map(jwtClaimsSet -> createUser(authContext, jwtClaimsSet.getClaims()));
    }

    @Override
    public Request signInUrl(String redirectUri, String state) {
        try {
            UriBuilder builder = UriBuilder.fromHttpUrl(getConfiguration().getUserAuthorizationUri());
            builder.addParameter(Parameters.CLIENT_ID, getConfiguration().getClientId());
            builder.addParameter(Parameters.RESPONSE_TYPE, getConfiguration().getResponseType());
            // append scopes
            if (getConfiguration().getScopes() != null && !getConfiguration().getScopes().isEmpty()) {
                builder.addParameter(Parameters.SCOPE, String.join(SCOPE_DELIMITER, getConfiguration().getScopes()));
            }
            // nonce parameter is required for implicit/hybrid flow
            if (!io.gravitee.am.common.oauth2.ResponseType.CODE.equals(getConfiguration().getResponseType())) {
                builder.addParameter(io.gravitee.am.common.oidc.Parameters.NONCE, SecureRandomString.generate());
            }
            // add state if provided.
            if(!StringUtils.isEmpty(state)) {
                builder.addParameter(Parameters.STATE, state);
            }

            // append redirect_uri
            builder.addParameter(Parameters.REDIRECT_URI, getConfiguration().isEncodeRedirectUri() ? UriBuilder.encodeURIComponent(redirectUri) : redirectUri);

            Request request = new Request();
            request.setMethod(HttpMethod.GET);
            request.setUri(builder.buildString());
            return request;
        } catch (Exception e) {
            LOGGER.error("An error occurs while building OpenID Connect Sign In URL", e);
            return null;
        }
    }

    protected Maybe<Token> authenticate(Authentication authentication) {
        // implicit flow, retrieve the hashValue of the URL (#access_token=....&token_type=...)
        if (AuthenticationFlow.IMPLICIT_FLOW.equals(authenticationFlow())){
            final String hashValue = authentication.getContext().request().parameters().getFirst(HASH_VALUE_PARAMETER);
            Map<String, String> hashValues = getParams(hashValue.substring(1));

            // implicit flow was used with response_type=id_token token, access token is already fetched, continue
            if (ResponseType.ID_TOKEN_TOKEN.equals(getConfiguration().getResponseType())) {
                String accessToken = hashValues.get(ACCESS_TOKEN_PARAMETER);
                // put the id_token in context for later use
                authentication.getContext().set(ID_TOKEN_PARAMETER, hashValues.get(ID_TOKEN_PARAMETER));
                return Maybe.just(new Token(accessToken, TokenTypeHint.ACCESS_TOKEN));
            }

            // implicit flow was used with response_type=id_token, id token is already fetched, continue
            if (ResponseType.ID_TOKEN.equals(getConfiguration().getResponseType())) {
                String idToken = hashValues.get(ID_TOKEN_PARAMETER);
                return Maybe.just(new Token(idToken, TokenTypeHint.ID_TOKEN));
            }
        }

        // authorization code flow, exchange code for an access token
        // prepare body request parameters
        final String authorizationCode = authentication.getContext().request().parameters().getFirst(getConfiguration().getCodeParameter());
        if (authorizationCode == null || authorizationCode.isEmpty()) {
            LOGGER.debug("Authorization code is missing, skip authentication");
            return Maybe.error(new BadCredentialsException("Missing authorization code"));
        }

        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair(Parameters.CLIENT_ID, getConfiguration().getClientId()));
        urlParameters.add(new BasicNameValuePair(Parameters.CLIENT_SECRET, getConfiguration().getClientSecret()));
        urlParameters.add(new BasicNameValuePair(Parameters.REDIRECT_URI, String.valueOf(authentication.getContext().get(Parameters.REDIRECT_URI))));
        urlParameters.add(new BasicNameValuePair(Parameters.CODE, authorizationCode));
        urlParameters.add(new BasicNameValuePair(Parameters.GRANT_TYPE, "authorization_code"));
        String bodyRequest = URLEncodedUtils.format(urlParameters);

        return getClient().postAbs(getConfiguration().getAccessTokenUri())
                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyRequest.length()))
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
                .rxSendBuffer(Buffer.buffer(bodyRequest))
                .toMaybe()
                .map(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpResponse.statusMessage());
                    }

                    JsonObject response = httpResponse.bodyAsJsonObject();
                    String accessToken = response.getString(ACCESS_TOKEN_PARAMETER);
                    if (getConfiguration().isUseIdTokenForUserInfo()) {
                        // put the id_token in context for later use
                        String idToken = response.getString(ID_TOKEN_PARAMETER);
                        authentication.getContext().set(ID_TOKEN_PARAMETER, idToken);
                    }
                    return new Token(accessToken, TokenTypeHint.ACCESS_TOKEN);
                });

    }

    protected Maybe<User> profile(Token token, Authentication authentication) {
        // we only have the id_token, try to decode it and create the end-user
        if (TokenTypeHint.ID_TOKEN.equals(token.getTypeHint())) {
            return retrieveUserFromIdToken(authentication.getContext(), token.getValue());
        }

        // if it's an access token but user ask for id token verification, try to decode it and create the end-user
        if (TokenTypeHint.ACCESS_TOKEN.equals(token.getTypeHint()) && getConfiguration().isUseIdTokenForUserInfo()) {
            if (authentication.getContext().get(ID_TOKEN_PARAMETER) != null) {
                String idToken = String.valueOf(authentication.getContext().get(ID_TOKEN_PARAMETER));
                return retrieveUserFromIdToken(authentication.getContext(), idToken);
            } else {
                // no suitable value to retrieve user
                return Maybe.error(new BadCredentialsException("No suitable value to retrieve user information"));
            }
        }

        // retrieve user claims from the UserInfo Endpoint
        return getClient().getAbs(getConfiguration().getUserProfileUri())
                .putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.getValue())
                .rxSend()
                .toMaybe()
                .map(httpClientResponse -> {
                    if (httpClientResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpClientResponse.statusMessage());
                    }

                    return createUser(authentication.getContext(), httpClientResponse.bodyAsJsonObject().getMap());
                });
    }

    protected User createUser(AuthenticationContext authContext, Map<String, Object> attributes) {
        String username = String.valueOf(attributes.getOrDefault(StandardClaims.PREFERRED_USERNAME, attributes.get(StandardClaims.SUB)));
        DefaultUser user = new DefaultUser(username);
        user.setId(String.valueOf(attributes.get(StandardClaims.SUB)));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, attributes.get(StandardClaims.SUB));
        // apply user mapping
        additionalInformation.putAll(applyUserMapping(attributes));
        // update username if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
            user.setUsername(String.valueOf(additionalInformation.get(StandardClaims.PREFERRED_USERNAME)));
        } else {
            additionalInformation.put(StandardClaims.PREFERRED_USERNAME, username);
        }
        user.setAdditionalInformation(additionalInformation);
        // set user roles
        user.setRoles(applyRoleMapping(authContext, attributes));
        return user;
    }

    protected void generateJWTProcessor(OpenIDConnectIdentityProviderConfiguration configuration) {
        if (getConfiguration().isUseIdTokenForUserInfo() || ResponseType.ID_TOKEN.equals(getConfiguration().getResponseType())) {
            if (getConfiguration().getPublicKeyResolver() == null || getConfiguration().getResolverParameter() == null) {
                throw new IllegalStateException("An public key resolver must be supply to verify the ID Token");
            }

            final SignatureAlgorithm signature = (getConfiguration().getSignatureAlgorithm() == null) ? SignatureAlgorithm.RS256 : getConfiguration().getSignatureAlgorithm();

            AbstractKeyProcessor keyProcessor = null;
            // init JWT key source (Remote URL or from configuration file)
            if (KeyResolver.JWKS_URL.equals(getConfiguration().getPublicKeyResolver())) {
                keyProcessor = new JWKSKeyProcessor();
                keyProcessor.setJwkSourceResolver(new RemoteJWKSourceResolver(getConfiguration().getResolverParameter()));
            } else {
                // get the corresponding key processor
                final String resolverParameter = getConfiguration().getResolverParameter();
                switch (signature) {
                    case RS256:
                    case RS384:
                    case RS512:
                        keyProcessor = new RSAKeyProcessor();
                        keyProcessor.setJwkSourceResolver(new RSAJWKSourceResolver(resolverParameter));
                        break;
                    case HS256:
                    case HS384:
                    case HS512:
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

}
