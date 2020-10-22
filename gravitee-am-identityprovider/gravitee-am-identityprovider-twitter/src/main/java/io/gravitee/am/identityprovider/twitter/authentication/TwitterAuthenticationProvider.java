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
package io.gravitee.am.identityprovider.twitter.authentication;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.common.oauth2.authentication.AbstractSocialAuthenticationProvider;
import io.gravitee.am.identityprovider.twitter.TwitterIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.twitter.authentication.spring.TwitterAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.twitter.authentication.utils.OAuthCredentials;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.util.Maps;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.identityprovider.twitter.authentication.utils.SignerUtils.*;
import static java.util.Collections.emptyMap;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(TwitterAuthenticationProviderConfiguration.class)
public class TwitterAuthenticationProvider extends AbstractSocialAuthenticationProvider<TwitterIdentityProviderConfiguration> {
    private static final String HMAC_SHA1_JAVA_ALGO = "HmacSHA1";


    private static final String FOLLOWERS = "followers";
    private static final String FOLLOWERS_COUNT = "followers_count";
    private static final String FRIENDS = "friends";
    private static final String FRIENDS_COUNT = "friends_count";
    private static final String DESCRIPTION = "description";
    private static final String LOCATION = "location";
    private static final String CREATED_AT = "create_at";
    private static final String FULL_NAME = "full_name";
    private static final String TWITTER_BASE_URL = "https://twitter.com/";
    private static final String TWITTER_NAME = "name";
    private static final String TWITTER_PROFILE_IMG = "profile_image_url_https";
    private static final String TWITTER_SCREEN_NAME = "screen_name";
    private static final String TWITTER_ID = "id_str";
    private static final String TWITTER_EMAIL = "email";
    private static final String TWITTER_LANG = "lang";
    private static final String TWITTER_TIME_ZONE = "time_zone";
    private static final String TWITTER_UPDATED_AT = "updated_at";

    @Autowired
    @Qualifier("twitterWebClient")
    private WebClient client;

    @Autowired
    private DefaultIdentityProviderMapper mapper;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Autowired
    private TwitterIdentityProviderConfiguration configuration;

    @Override
    public TwitterIdentityProviderConfiguration getConfiguration() {
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

    protected final Cache<String, String> tokenMemory = CacheBuilder.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();

    @Override
    public Request signInUrl(String redirectUri, String state) {
        throw new IllegalStateException("signInUrl isn't implemented for Twitter IdP");
    }

    @Override
    public Maybe<Request> asyncSignInUrl(String redirectUri, String state) {
        try {
            if(!StringUtils.isEmpty(state)) {
                // Add state to redirect uri if specified. Note: Twitter is not oidc compliant and does not allow to specify a 'state' query parameter on its own authorization url.
                final UriBuilder uriBuilder = UriBuilder.fromURIString(redirectUri).addParameter(Parameters.STATE, state);
                redirectUri = uriBuilder.buildString();
            }

            Map<String, String> parameters = Maps.<String, String>builder()
                    .put(OAUTH_CALLBACK, redirectUri)
                    .put(OAUTH_CONSUMER_KEY, configuration.getClientId())
                    .put(OAUTH_SIGNATURE_METHOD, OAUTH_SIGNATURE_METHOD_VALUE)
                    .put(OAUTH_VERSION, OAUTH_VERSION_VALUE)
                    .build();

            String authorization = getAuthorizationHeader("POST", configuration.getRequestTokenUrl(), emptyMap(), parameters, new OAuthCredentials(configuration));

            return getClient().postAbs(getConfiguration().getRequestTokenUrl())
                    .putHeader(HttpHeaders.AUTHORIZATION, authorization)
                    .rxSend()
                    .toMaybe()
                    .map(httpResponse -> {
                        if (httpResponse.statusCode() != 200) {
                            throw new BadCredentialsException(httpResponse.statusMessage());
                        }
                        String body = httpResponse.bodyAsString();
                        String[] tokenResponse = body.split("&");
                        String token = null;
                        String tokenSecret = null;
                        String callbackState = null;
                        for (String responsePair : tokenResponse) {
                            String[] pair = responsePair.split("=");
                            if (pair.length > 1) {
                                if (OAUTH_TOKEN.equals(pair[0])) {
                                    token = pair[1];
                                }
                                if (OAUTH_TOKEN_SECRET.equals(pair[0])) {
                                    tokenSecret = pair[1];
                                }
                                if ("oauth_callback_confirmed".equals(pair[0])) {
                                    callbackState = pair[1];
                                }
                            }
                        }

                        if ("true".equalsIgnoreCase(callbackState)) {
                            tokenMemory.put(token, tokenSecret); // preserve toke & token secret for the next steps

                            UriBuilder builder = UriBuilder.fromHttpUrl(configuration.getUserAuthorizationUri());
                            builder.addParameter(OAUTH_TOKEN, token);

                            Request request = new Request();
                            request.setMethod(HttpMethod.GET);
                            request.setUri(builder.build().toString());
                            return request;
                        }

                        throw new BadCredentialsException("Token returned by Twitter mismatch");
                    });
        } catch (BadCredentialsException e) {
            LOGGER.error("An error occurs while building Sign In URL", e);
            return Maybe.empty();
        }
    }

    @Override
    protected Maybe<Token> authenticate(Authentication authentication) {
        final String oauthToken = authentication.getContext().request().parameters().getFirst(configuration.getCodeParameter());
        final String tokenVerifier = authentication.getContext().request().parameters().getFirst(configuration.getTokenVerifier());

        if (oauthToken == null || oauthToken.isEmpty() || tokenMemory.getIfPresent(oauthToken) == null) {
            LOGGER.debug("OAuth Token is missing, skip authentication");
            return Maybe.error(new BadCredentialsException("Missing OAuth Token"));
        }

        if (tokenVerifier == null || tokenVerifier.isEmpty()) {
            LOGGER.debug("Token Verifier is missing, skip authentication");
            return Maybe.error(new BadCredentialsException("Missing Token Verifier"));
        }

        Map<String, String> parameters = Maps.<String, String>builder()
                .put(OAUTH_VERIFIER, tokenVerifier)
                .build();

        Map<String, String> oauthParams = Maps.<String, String>builder()
                .put(OAUTH_CONSUMER_KEY, configuration.getClientId())
                .put(OAUTH_TOKEN, oauthToken)
                .put(OAUTH_SIGNATURE_METHOD, OAUTH_SIGNATURE_METHOD_VALUE)
                .put(OAUTH_VERSION, OAUTH_VERSION_VALUE)
                .build();

        String authorization = getAuthorizationHeader("POST",
                configuration.getAccessTokenUri(),
                parameters, oauthParams,
                new OAuthCredentials(configuration, oauthToken, tokenMemory.getIfPresent(oauthToken)));

        tokenMemory.invalidate(oauthToken);
        MultiMap form = MultiMap.caseInsensitiveMultiMap().set(OAUTH_VERIFIER, tokenVerifier);
        return client.postAbs(configuration.getAccessTokenUri())
                .putHeader(HttpHeaders.AUTHORIZATION, authorization)
                .rxSendForm(form)
                .toMaybe()
                .flatMap(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        return Maybe.error(new BadCredentialsException(httpResponse.bodyAsString()));
                    }
                    String[] tokenInfo = httpResponse.bodyAsString().split("&");
                    String token = "";
                    String secret = "";
                    for (String pairString: tokenInfo) {
                        String[] pair = pairString.split("=");
                        if (pair.length > 1) {
                            if (pair[0].equalsIgnoreCase(OAUTH_TOKEN)) {
                                token = pair[1];
                            }
                            if (pair[0].equalsIgnoreCase(OAUTH_TOKEN_SECRET)) {
                                secret = pair[1];
                            }
                        }
                    }
                    return Maybe.just(new Token(token, secret, TokenTypeHint.ACCESS_TOKEN));
                });
    }

    @Override
    protected Maybe<User> profile(Token token, Authentication authentication) {
        Map<String, String> parameters = Maps.<String, String>builder()
                .put("include_email", "true")
                .build();

        Map<String, String> oauthParams = Maps.<String, String>builder()
                .put(OAUTH_CONSUMER_KEY, configuration.getClientId())
                .put(OAUTH_TOKEN, token.getValue())
                .put(OAUTH_SIGNATURE_METHOD, OAUTH_SIGNATURE_METHOD_VALUE)
                .put(OAUTH_VERSION, OAUTH_VERSION_VALUE)
                .build();

        String authorization = getAuthorizationHeader("GET",
                configuration.getUserProfileUri(),
                parameters, oauthParams,
                new OAuthCredentials(configuration, token.getValue(), token.getSecret()));

        return client.getAbs(configuration.getUserProfileUri()+"?include_email=true")
                .putHeader(HttpHeaders.AUTHORIZATION, authorization)
                //.rxSendForm(form)
                .rxSend()
                .toMaybe()
                .flatMap(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        return Maybe.error(new BadCredentialsException(httpResponse.bodyAsString()));
                    }
                    JsonObject jsonObject = httpResponse.bodyAsJsonObject();

                    DefaultUser user = new DefaultUser(jsonObject.getString(TWITTER_SCREEN_NAME));
                    user.setId(jsonObject.getString(TWITTER_ID));

                    Map<String, Object> additionalInfos = new HashMap<>();
                    additionalInfos.putAll(applyUserMapping(jsonObject.getMap()));
                    user.setAdditionalInformation(additionalInfos);
                    user.setRoles(applyRoleMapping(jsonObject.getMap()));

                    return Maybe.just(user);
                });
    }

    @Override
    protected Map<String, Object> defaultClaims(Map<String, Object> attributes) {
        JsonObject jsonObject = JsonObject.mapFrom(attributes);
        Map<String, Object> claims = new HashMap<>();

        claims.put(StandardClaims.PROFILE, TWITTER_BASE_URL+jsonObject.getString(TWITTER_SCREEN_NAME));
        claims.put(StandardClaims.PREFERRED_USERNAME, jsonObject.getString(TWITTER_SCREEN_NAME));
        claims.put(StandardClaims.SUB, jsonObject.getString(TWITTER_ID));

        final String img = jsonObject.getString(TWITTER_PROFILE_IMG);
        if (img != null) {
            claims.put(StandardClaims.PICTURE, img);
        }

        final String email = jsonObject.getString(TWITTER_EMAIL);
        if (email != null) {
            claims.put(StandardClaims.EMAIL, email);
        }

        final String zone = jsonObject.getString(TWITTER_TIME_ZONE);
        if (zone != null) {
            claims.put(StandardClaims.ZONEINFO, zone);
        }
        final String updateAt = jsonObject.getString(TWITTER_UPDATED_AT);
        if (updateAt != null) {
            claims.put(StandardClaims.UPDATED_AT, updateAt);
        }
        final String locale = jsonObject.getString(TWITTER_LANG);
        if (locale != null) {
            claims.put(StandardClaims.LOCALE, locale);
        }

        // custom Twitter claims
        final String description = jsonObject.getString(DESCRIPTION);
        if (description != null) {
            claims.put(DESCRIPTION, description);
        }

        final String location = jsonObject.getString(LOCATION);
        if (location != null) {
            claims.put(LOCATION, location);
        }

        final String createdAt = jsonObject.getString(CREATED_AT);
        if (createdAt != null) {
            claims.put(CREATED_AT, createdAt);
        }

        final String fullName = jsonObject.getString(TWITTER_NAME);
        if (fullName != null) {
            claims.put(FULL_NAME, fullName);
        }

        claims.put(FOLLOWERS, jsonObject.getInteger(FOLLOWERS_COUNT, 0));
        claims.put(FRIENDS, jsonObject.getInteger(FRIENDS_COUNT,0));

        return claims;
    }
}
