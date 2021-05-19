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
package io.gravitee.am.identityprovider.franceconnect.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.common.oauth2.authentication.AbstractSocialAuthenticationProvider;
import io.gravitee.am.identityprovider.common.oauth2.utils.URLEncodedUtils;
import io.gravitee.am.identityprovider.franceconnect.FranceConnectIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.franceconnect.authentication.spring.FranceConnectAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.franceconnect.model.FranceConnectUser;
import io.gravitee.am.model.http.BasicNameValuePair;
import io.gravitee.am.model.http.NameValuePair;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.MediaType;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(FranceConnectAuthenticationProviderConfiguration.class)
public class FranceConnectAuthenticationProvider extends AbstractSocialAuthenticationProvider<FranceConnectIdentityProviderConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FranceConnectAuthenticationProvider.class);
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CODE = "code";
    private static final String ACCESS_TOKEN_PARAMETER = "access_token";

    @Autowired
    @Qualifier("franceConnectWebClient")
    private WebClient client;

    @Autowired
    private FranceConnectIdentityProviderConfiguration configuration;

    @Autowired
    private DefaultIdentityProviderMapper mapper;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Override
    protected FranceConnectIdentityProviderConfiguration getConfiguration() {
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
    public Request signInUrl(String redirectUri, String state) {
        try {
            UriBuilder builder = UriBuilder.fromHttpUrl(getConfiguration().getUserAuthorizationUri());
            builder.addParameter(Parameters.CLIENT_ID, getConfiguration().getClientId());
            if (getConfiguration().getEnvironment() == FranceConnectIdentityProviderConfiguration.Environment.DEVELOPMENT) {
                // NOTE: Port is being proxied by nginx. Please have a look to the README.adoc file
                QueryStringDecoder decoder = new QueryStringDecoder(redirectUri);
                builder.addParameter(Parameters.REDIRECT_URI, "http://localhost:4242/callback");
            } else {
                builder.addParameter(Parameters.REDIRECT_URI, redirectUri);
            }

            builder.addParameter(Parameters.RESPONSE_TYPE, getConfiguration().getResponseType());
            builder.addParameter(io.gravitee.am.common.oidc.Parameters.NONCE, SecureRandomString.generate());

            if(!StringUtils.isEmpty(state)) {
                builder.addParameter(Parameters.STATE, state);
            }

            if (getConfiguration().getScopes() != null && !getConfiguration().getScopes().isEmpty()) {
                builder.addParameter(Parameters.SCOPE, String.join(SCOPE_DELIMITER, getConfiguration().getScopes()));
            }

            Request request = new Request();
            request.setMethod(HttpMethod.GET);
            request.setUri(builder.build().toString());
            return request;
        } catch (Exception e) {
            LOGGER.error("An error occurs while building Sign In URL", e);
            return null;
        }
    }

    @Override
    protected Maybe<Token> authenticate(Authentication authentication) {
        // prepare body request parameters
        final String authorizationCode = authentication.getContext().request().parameters().getFirst(configuration.getCodeParameter());
        if (authorizationCode == null || authorizationCode.isEmpty()) {
            LOGGER.debug("Authorization code is missing, skip authentication");
            return Maybe.error(new BadCredentialsException("Missing authorization code"));
        }
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair(CLIENT_ID, configuration.getClientId()));
        urlParameters.add(new BasicNameValuePair(CLIENT_SECRET, configuration.getClientSecret()));
        urlParameters.add(new BasicNameValuePair(Parameters.GRANT_TYPE, "authorization_code"));
        if (getConfiguration().getEnvironment() == FranceConnectIdentityProviderConfiguration.Environment.DEVELOPMENT) {
            // NOTE: Port is being proxied by nginx. Please have a look to the README.adoc file
            QueryStringDecoder decoder = new QueryStringDecoder((String) authentication.getContext().get(REDIRECT_URI));
            urlParameters.add(new BasicNameValuePair(Parameters.REDIRECT_URI, "http://localhost:4242/callback"));
        } else {
            urlParameters.add(new BasicNameValuePair(Parameters.REDIRECT_URI, (String) authentication.getContext().get(REDIRECT_URI)));
        }
        urlParameters.add(new BasicNameValuePair(CODE, authorizationCode));
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

                    JsonObject response = httpResponse.bodyAsJsonObject();
                    String accessToken = response.getString(ACCESS_TOKEN_PARAMETER);
                    return new Token(accessToken, TokenTypeHint.ACCESS_TOKEN);
                });
    }

    @Override
    protected Maybe<User> profile(Token accessToken, Authentication authentication) {
        return client.getAbs(configuration.getUserProfileUri())
                .putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.getValue())
                .rxSend()
                .toMaybe()
                .map(httpClientResponse -> {
                    if (httpClientResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpClientResponse.statusMessage());
                    }

                    return createUser(authentication.getContext(), httpClientResponse.bodyAsJsonObject().getMap());
                });
    }

    private User createUser(AuthenticationContext authContext, Map<String, Object> attributes) {
        String username = (String) attributes.getOrDefault(StandardClaims.PREFERRED_USERNAME, attributes.get(StandardClaims.SUB));

        // Looks like the preferred_username can be empty
        if (username == null || username.isEmpty()) {
            username = (String) attributes.get(StandardClaims.SUB);
        }

        User user = new DefaultUser(username);
        ((DefaultUser) user).setId(String.valueOf(attributes.get(StandardClaims.SUB)));

        if (attributes.get(StandardClaims.EMAIL) != null) {
            String email = (String) attributes.get(StandardClaims.EMAIL);
            if (email != null && !email.isEmpty()) {
                ((DefaultUser) user).setEmail(email);
            }
        }

        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();

        // Standard claims
        additionalInformation.put(StandardClaims.SUB, attributes.get(StandardClaims.SUB));
        additionalInformation.put(StandardClaims.NAME, attributes.get(StandardClaims.NAME));
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, attributes.get(StandardClaims.PREFERRED_USERNAME));

        // apply user mapping
        additionalInformation.putAll(applyUserMapping(attributes));

        // update username if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
            ((DefaultUser) user).setUsername((String) additionalInformation.get(StandardClaims.PREFERRED_USERNAME));
        }
        ((DefaultUser) user).setAdditionalInformation(additionalInformation);

        // set user roles
        ((DefaultUser) user).setRoles(applyRoleMapping(authContext, attributes));
        return user;
    }

    protected Map<String, Object> defaultClaims(Map<String, Object> attributes) {
        Map<String, Object> claims = new HashMap<>();

        // Standard OIDC claims
        claims.put(StandardClaims.GIVEN_NAME, attributes.get(StandardClaims.GIVEN_NAME));
        claims.put(StandardClaims.FAMILY_NAME, attributes.get(StandardClaims.FAMILY_NAME));
        claims.put(StandardClaims.EMAIL, attributes.get(StandardClaims.EMAIL));

        // custom GitHub claims
        claims.put(FranceConnectUser.BIRTH_DATE, attributes.get(FranceConnectUser.BIRTH_DATE));
        claims.put(FranceConnectUser.BIRTH_PLACE, attributes.get(FranceConnectUser.BIRTH_PLACE));
        claims.put(FranceConnectUser.BIRTH_COUNTRY, attributes.get(FranceConnectUser.BIRTH_COUNTRY));
        claims.put(FranceConnectUser.GENDER, attributes.get(FranceConnectUser.GENDER));

        return claims;
    }
}
