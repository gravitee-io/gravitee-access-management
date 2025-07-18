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

import com.google.common.base.Strings;
import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.AuthenticationFlow;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.Pair;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.common.web.URLParametersUtils;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectAuthenticationProvider;
import io.gravitee.am.identityprovider.api.oidc.jwt.KeyResolver;
import io.gravitee.am.identityprovider.api.social.ProviderResponseType;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.hmac.MACJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.remote.RemoteJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.rsa.RSAJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.AbstractKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.HMACKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.JWKSKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.RSAKeyProcessor;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;
import static io.gravitee.am.common.utils.ConstantKeys.ID_TOKEN_EXCLUDED_CLAIMS;
import static io.gravitee.am.common.utils.ConstantKeys.STORE_ORIGINAL_TOKEN_KEY;
import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;
import static java.util.function.Predicate.not;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractOpenIDConnectAuthenticationProvider extends AbstractSocialAuthenticationProvider implements OpenIDConnectAuthenticationProvider, InitializingBean {

    @Value("${httpClient.timeout:10000}")
    protected int connectionTimeout;

    @Value("${httpClient.readTimeout:5000}")
    protected int readTimeout;

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    public static final String ID_TOKEN_PARAMETER = "id_token";

    protected JWTProcessor jwtProcessor;

    @Override
    public AuthenticationFlow authenticationFlow() {
        return io.gravitee.am.common.oauth2.ResponseType.CODE.equals(getConfiguration().getResponseType()) ? AuthenticationFlow.AUTHORIZATION_CODE_FLOW : AuthenticationFlow.IMPLICIT_FLOW;
    }

    @Override
    protected Map<String, Object> defaultClaims(Map attributes) {
        // we can safely cast in <String, Object> as the super class signature declare it.
        Map<String, Object> typedAttributes = attributes;
        return typedAttributes.keySet().stream()
                .filter(claimName -> attributes.get(claimName) != null) // sometimes values is null that throws a NPE during the collect phase
                .filter(not(ID_TOKEN_EXCLUDED_CLAIMS::contains))
                .collect(Collectors.toMap(claimName -> claimName, attributes::get));
    }

    protected Maybe<User> retrieveUserFromIdToken(AuthenticationContext authContext, String idToken) {
        if (this.jwtProcessor == null) {
            // ensure that jwtProcessor exist before using it
            return Maybe.error(new InternalAuthenticationServiceException("Identity provider has not been properly initialized"));
        }

        return Maybe.fromCallable(() -> this.jwtProcessor.process(idToken, null))
                .onErrorResumeNext(ex -> Maybe.error(new BadCredentialsException(ex.getMessage())))
                .map(jwtClaimsSet -> createUser(authContext, jwtClaimsSet.getClaims()));
    }

    public Request signInUrl(String redirectUri, String state) {
        try {
            var builder = prepareSignInUrl(redirectUri);
            if (builder == null) {
                return null;
            }
            if (StringUtils.isNotBlank(state)) {
                builder.addParameter(Parameters.STATE, state);
            }
            return Request.get(builder.buildString());
        } catch (Exception e) {
            LOGGER.error("An error has occurred while building OpenID Connect Sign In URL", e);
            return null;
        }
    }

    @Override
    public Maybe<Request> asyncSignInUrl(String redirectUri, JWT state, StateEncoder stateEncoder) {
        try {
            var builder = prepareSignInUrl(redirectUri);
            if (builder == null) {
                return Maybe.empty();
            }
            // PKCE
            if (getConfiguration().usePkce()) {
                var codeVerifier = SecureRandomString.generate();
                if (state == null) {
                    state = new JWT();
                }
                state.put(Claims.ENCRYPTED_CODE_VERIFIER, codeVerifier);
                builder.addParameter(Parameters.CODE_CHALLENGE, getConfiguration().getCodeChallengeMethod().getChallenge(codeVerifier));
                builder.addParameter(Parameters.CODE_CHALLENGE_METHOD, getConfiguration().getCodeChallengeMethod().getUriValue());
            }

            if (state == null || state.isEmpty()) {
                return Maybe.just(Request.get(builder.buildString()));
            }

            return stateEncoder.encode(state)
                    .flatMapMaybe(encodedState -> {
                        builder.addParameter(Parameters.STATE, encodedState);
                        return Maybe.just(Request.get(builder.buildString()));
                    });
        } catch (Exception e) {
            LOGGER.error("An error has occurred while building OpenID Connect Sign In URL", e);
            return Maybe.empty();
        }
    }

    private UriBuilder prepareSignInUrl(String redirectUri) {
        if (getConfiguration().getUserAuthorizationUri() == null) {
            LOGGER.warn("Social Provider {} can't provide signInUrl, userAuthorizationUri is null", this.getClass().getSimpleName());
            return null;
        }

        UriBuilder builder = UriBuilder.fromHttpUrl(getConfiguration().getUserAuthorizationUri())
                .addParameter(Parameters.CLIENT_ID, getConfiguration().getClientId())
                .addParameter(Parameters.RESPONSE_TYPE, getConfiguration().getProviderResponseType().value());

        // append scopes
        if (getConfiguration().getScopes() != null && !getConfiguration().getScopes().isEmpty()) {
            builder.addParameter(Parameters.SCOPE, String.join(SCOPE_DELIMITER, getConfiguration().getScopes()));
        }
        // nonce parameter is required for implicit/hybrid flow
        if (!io.gravitee.am.common.oauth2.ResponseType.CODE.equals(getConfiguration().getResponseType())) {
            builder.addParameter(io.gravitee.am.common.oidc.Parameters.NONCE, SecureRandomString.generate());
        }

        // including the response_mode parameter with the default value for selected response_type is not recommended
        // https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#ResponseModes
        if (getConfiguration().getResponseMode() != getConfiguration().getProviderResponseType().defaultResponseMode()) {
            builder.addParameter(Parameters.RESPONSE_MODE, getConfiguration().getResponseMode().name().toLowerCase(Locale.ROOT));
        }

        builder.addParameter(Parameters.REDIRECT_URI, getConfiguration().isEncodeRedirectUri() ? encodeURIComponent(redirectUri) : redirectUri);
        return builder;
    }

    protected Maybe<Token> authenticate(Authentication authentication) {

        var responseMode = getConfiguration().getResponseMode();

        var authorizationParameters = responseMode.extractParameters(authentication.getContext().request());

        // implicit flow, retrieve the hashValue of the URL (#access_token=....&token_type=...)
        if (AuthenticationFlow.IMPLICIT_FLOW == authenticationFlow()) {
            return authenticateImplicitFlow(
                    authorizationParameters,
                    getConfiguration().getProviderResponseType(),
                    authentication.getContext()::set);
        } else {
            authorizationParameters.add(Parameters.REDIRECT_URI, String.valueOf(authentication.getContext().get(Parameters.REDIRECT_URI)));
            authorizationParameters.add(ConstantKeys.IDP_CODE_VERIFIER,(String) authentication.getContext().get(ConstantKeys.IDP_CODE_VERIFIER));
            return authenticateAuthorizationCodeFlow(authorizationParameters, authentication.getContext()::set);
        }

    }

    private Maybe<Token> authenticateAuthorizationCodeFlow(MultiValueMap<String, String> authorizationParameters,
                                                           BiConsumer<String, String> storeInContext) {
        // authorization code flow, exchange code for an access token
        // prepare body request parameters
        final String authorizationCode = authorizationParameters.getFirst(getConfiguration().getCodeParameter());
        if (authorizationCode == null || authorizationCode.isEmpty()) {
            LOGGER.debug("Authorization code is missing, skip authentication");
            return Maybe.error(new BadCredentialsException("Missing authorization code"));
        }

        final List<Pair<String,String>> urlParameters = new ArrayList<>();
        final HttpRequest<Buffer> tokenRequest = getClient().postAbs(getConfiguration().getAccessTokenUri());

        if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(this.getConfiguration().getClientAuthenticationMethod())) {
            tokenRequest.basicAuthentication(getConfiguration().getClientId(), getConfiguration().getClientSecret());
        } else {
            urlParameters.add(Pair.of(Parameters.CLIENT_SECRET, getConfiguration().getClientSecret()));
        }

        urlParameters.add(Pair.of(Parameters.CLIENT_ID, getConfiguration().getClientId()));
        urlParameters.add(Pair.of(Parameters.REDIRECT_URI, authorizationParameters.getFirst(Parameters.REDIRECT_URI)));
        urlParameters.add(Pair.of(Parameters.CODE, authorizationCode));
        urlParameters.add(Pair.of(Parameters.GRANT_TYPE, GrantType.AUTHORIZATION_CODE));
        if (getConfiguration().usePkce()) {
            Optional.ofNullable(authorizationParameters.getFirst(ConstantKeys.IDP_CODE_VERIFIER))
                    .ifPresentOrElse(codeVerifier -> urlParameters.add(Pair.of(Parameters.CODE_VERIFIER, codeVerifier)),
                            () -> LOGGER.warn("PKCE is enabled, but there's no code verifier available for the request"));
        }

        String bodyRequest = URLParametersUtils.format(urlParameters);

        return tokenRequest
                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyRequest.length()))
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
                .rxSendBuffer(Buffer.buffer(bodyRequest))
                .toMaybe()
                .map(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        LOGGER.error("HTTP error {} is thrown while exchanging code. The response body is: {} ", httpResponse.statusCode(), httpResponse.bodyAsString());
                        throw new BadCredentialsException(httpResponse.statusMessage());
                    }
                    JsonObject response = httpResponse.bodyAsJsonObject();
                    String accessToken = response.getString(ACCESS_TOKEN_PARAMETER);
                    // We store the token is option is enabled
                    if (getConfiguration().isStoreOriginalTokens() && !Strings.isNullOrEmpty(accessToken)) {
                        storeInContext.accept(ACCESS_TOKEN_PARAMETER, accessToken);
                    }

                    // ID Token is always stored for SSO
                    String idToken = response.getString(ID_TOKEN_PARAMETER);
                    if (!Strings.isNullOrEmpty(idToken)) {
                        storeInContext.accept(ID_TOKEN_PARAMETER, idToken);
                    }

                    if (getConfiguration().isStoreOriginalTokens()) {
                        // put this to context to inform about storing tokens in user profile
                        storeInContext.accept(STORE_ORIGINAL_TOKEN_KEY, "true");
                    }
                    return new Token(accessToken, TokenTypeHint.ACCESS_TOKEN);
                });
    }

    private Maybe<Token> authenticateImplicitFlow(MultiValueMap<String, String> authorizationParams, ProviderResponseType providerResponseType,
                                                  BiConsumer<String, String> storeInContext) {
        if (getConfiguration().isStoreOriginalTokens()) {
            // put this to context to inform about storing tokens in user profile
            storeInContext.accept(STORE_ORIGINAL_TOKEN_KEY, "true");
        }
        // implicit flow was used with response_type=id_token token, access token is already fetched, continue
        if (providerResponseType.equals(ProviderResponseType.ID_TOKEN_TOKEN)) {
            String accessToken = authorizationParams.getFirst(ACCESS_TOKEN_PARAMETER);
            // We store the token is option is enabled
            if (getConfiguration().isStoreOriginalTokens() && !Strings.isNullOrEmpty(accessToken)) {
                storeInContext.accept(ACCESS_TOKEN_PARAMETER, accessToken);
            }

            // put the id_token in context for later use
            storeInContext.accept(ID_TOKEN_PARAMETER, authorizationParams.getFirst(ID_TOKEN_PARAMETER));
            return Maybe.just(new Token(accessToken, TokenTypeHint.ACCESS_TOKEN));
        }

        // implicit flow was used with response_type=id_token, id token is already fetched, continue
        if (ProviderResponseType.ID_TOKEN.equals(getConfiguration().getProviderResponseType())) {
            String idToken = authorizationParams.getFirst(ID_TOKEN_PARAMETER);
            // put the id_token in context for later use
            storeInContext.accept(ID_TOKEN_PARAMETER, idToken);
            return Maybe.just(new Token(idToken, TokenTypeHint.ID_TOKEN));
        }

        return Maybe.error(new IllegalStateException("Wrong response_type for implicit flow: %s".formatted(providerResponseType.value())));
    }

    protected Maybe<User> profile(Token token, Authentication authentication) {
        // we only have the id_token, try to decode it and create the end-user
        if (TokenTypeHint.ID_TOKEN.equals(token.typeHint())) {
            return retrieveUserFromIdToken(authentication.getContext(), token.value());
        }

        // if it's an access token but user ask for id token verification, try to decode it and create the end-user
        if (TokenTypeHint.ACCESS_TOKEN.equals(token.typeHint()) && getConfiguration().isUseIdTokenForUserInfo()) {
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
                .putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
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
        additionalInformation.putAll(applyUserMapping(authContext, attributes));
        // update username if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
            user.setUsername(String.valueOf(additionalInformation.get(StandardClaims.PREFERRED_USERNAME)));
        } else {
            additionalInformation.put(StandardClaims.PREFERRED_USERNAME, username);
        }
        user.setAdditionalInformation(additionalInformation);
        // set user roles
        user.setRoles(applyRoleMapping(authContext, attributes));
        user.setGroups(applyGroupMapping(authContext, attributes));
        return user;
    }

    protected void generateJWTProcessor() {
        if (getConfiguration().isUseIdTokenForUserInfo() || ResponseType.ID_TOKEN.equals(getConfiguration().getResponseType())) {
            if (getConfiguration().getPublicKeyResolver() == null || getConfiguration().getResolverParameter() == null) {
                throw new IllegalStateException("An public key resolver must be supply to verify the ID Token");
            }

            final SignatureAlgorithm signature = (getConfiguration().getSignatureAlgorithm() == null) ? SignatureAlgorithm.RS256 : getConfiguration().getSignatureAlgorithm();

            AbstractKeyProcessor keyProcessor = null;
            // init JWT key source (Remote URL or from configuration file)
            if (KeyResolver.JWKS_URL.equals(getConfiguration().getPublicKeyResolver())) {
                keyProcessor = new JWKSKeyProcessor();
                keyProcessor.setJwkSourceResolver(new RemoteJWKSourceResolver(getConfiguration().getResolverParameter(), connectionTimeout, readTimeout));
            } else {
                // get the corresponding key processor
                final String resolverParameter = getConfiguration().getResolverParameter();
                switch (signature) {
                    case RS256, RS384, RS512 -> {
                        keyProcessor = new RSAKeyProcessor();
                        keyProcessor.setJwkSourceResolver(new RSAJWKSourceResolver(resolverParameter));
                    }
                    case HS256, HS384, HS512 -> {
                        keyProcessor = new HMACKeyProcessor();
                        keyProcessor.setJwkSourceResolver(new MACJWKSourceResolver(resolverParameter));
                    }
                    default -> {
                        // No action needed for default case
                    }
                }
            }

            Assert.notNull(keyProcessor, "A key processor must be set");
            jwtProcessor = keyProcessor.create(signature);
        }
    }

}
