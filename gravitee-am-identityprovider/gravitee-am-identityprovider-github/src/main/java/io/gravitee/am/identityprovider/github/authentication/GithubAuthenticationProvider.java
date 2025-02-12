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
package io.gravitee.am.identityprovider.github.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.IdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.common.oauth2.authentication.AbstractSocialAuthenticationProvider;
import io.gravitee.am.common.web.URLParametersUtils;
import io.gravitee.am.identityprovider.github.GithubIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.github.authentication.spring.GithubAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.github.model.GithubUser;
import io.gravitee.am.model.http.BasicNameValuePair;
import io.gravitee.am.model.http.NameValuePair;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Import(GithubAuthenticationProviderConfiguration.class)
public class GithubAuthenticationProvider extends AbstractSocialAuthenticationProvider<GithubIdentityProviderConfiguration> {

    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CODE = "code";

    @Autowired
    @Qualifier("gitHubWebClient")
    private WebClient client;

    @Autowired
    private GithubIdentityProviderConfiguration configuration;

    @Autowired
    private DefaultIdentityProviderMapper mapper;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Autowired
    private DefaultIdentityProviderGroupMapper groupMapper;

    @Override
    protected GithubIdentityProviderConfiguration getConfiguration() {
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
    protected Maybe<Token> authenticate(Authentication authentication) {
        // prepare body request parameters
        final String authorizationCode = authentication.getContext().request().parameters().getFirst(configuration.getCodeParameter());
        if (authorizationCode == null || authorizationCode.isEmpty()) {
            log.debug("Authorization code is missing, skip authentication");
            return Maybe.error(new BadCredentialsException("Missing authorization code"));
        }
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair(CLIENT_ID, configuration.getClientId()));
        urlParameters.add(new BasicNameValuePair(CLIENT_SECRET, configuration.getClientSecret()));
        urlParameters.add(new BasicNameValuePair(REDIRECT_URI, (String) authentication.getContext().get(REDIRECT_URI)));
        urlParameters.add(new BasicNameValuePair(CODE, authorizationCode));
        String bodyRequest = URLParametersUtils.format(urlParameters);

        return client.postAbs(configuration.getAccessTokenUri())
                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyRequest.length()))
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
                .rxSendBuffer(Buffer.buffer(bodyRequest))
                .toMaybe()
                .map(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        log.error("HTTP error {} is thrown while exchanging code. The response body is: {} ", httpResponse.statusCode(), httpResponse.bodyAsString());
                        throw new BadCredentialsException(httpResponse.statusMessage());
                    }

                    Map<String, String> bodyResponse = URLParametersUtils.parse(httpResponse.bodyAsString());
                    // Set original token if allowed
                    final String tokenValue = bodyResponse.get(ACCESS_TOKEN_PARAMETER);
                    if (configuration.isStoreOriginalTokens()) {
                        authentication.getContext().set(ACCESS_TOKEN_PARAMETER, tokenValue);
                    }
                    return new Token(tokenValue, TokenTypeHint.ACCESS_TOKEN);
                });
    }

    @Override
    protected Maybe<User> profile(Token accessToken, Authentication authentication) {
        final String tokenValue = accessToken.value();
        return client.getAbs(configuration.getUserProfileUri())
                .putHeader(AUTHORIZATION, "token " + tokenValue)
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
        var user = new DefaultUser(String.valueOf(attributes.get(GithubUser.LOGIN)));
        user.setId(String.valueOf(attributes.get(GithubUser.ID)));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        // Standard claims
        additionalInformation.put(StandardClaims.SUB, attributes.get(GithubUser.ID));
        additionalInformation.put(StandardClaims.NAME, attributes.get(GithubUser.NAME));
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, attributes.get(GithubUser.LOGIN));
        // apply user mapping
        additionalInformation.putAll(applyUserMapping(authContext, attributes));
        // update username if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
            user.setUsername((String) additionalInformation.get(StandardClaims.PREFERRED_USERNAME));
        }
        user.setAdditionalInformation(additionalInformation);
        // set user roles
        user.setRoles(applyRoleMapping(authContext, attributes));
        user.setGroups(applyGroupMapping(authContext, attributes));
        return user;
    }

    protected Map<String, Object> defaultClaims(Map<String, Object> attributes) {
        Map<String, Object> claims = new HashMap<>();
        try {
            String[] fullName = String.valueOf(attributes.get(GithubUser.NAME)).split("\\s+");
            if (fullName.length > 0) {
                claims.put(StandardClaims.GIVEN_NAME, fullName[0]);
            }
            if (fullName.length > 1) {
                claims.put(StandardClaims.FAMILY_NAME, fullName[1]);
            }
        } catch (Exception e) {
            log.debug("Unable to resolve Github user full name : {}", attributes.get(GithubUser.NAME), e);
        }

        claims.put(StandardClaims.PROFILE, attributes.get(GithubUser.HTML_URL));
        claims.put(StandardClaims.PICTURE, attributes.get(GithubUser.AVATAR_URL));
        claims.put(StandardClaims.WEBSITE, attributes.get(GithubUser.BLOG));
        claims.put(StandardClaims.EMAIL, attributes.get(GithubUser.EMAIL));
        claims.put(StandardClaims.ZONEINFO, attributes.get(GithubUser.LOCATION));
        claims.put(StandardClaims.UPDATED_AT, attributes.get(GithubUser.UPDATED_AT));

        // custom GitHub claims
        claims.put(GithubUser.AVATAR_URL, attributes.get(GithubUser.AVATAR_URL));
        claims.put(GithubUser.GRAVATAR_ID, attributes.get(GithubUser.GRAVATAR_ID));
        claims.put(GithubUser.URL, attributes.get(GithubUser.URL));
        claims.put(GithubUser.HTML_URL, attributes.get(GithubUser.HTML_URL));
        claims.put(GithubUser.FOLLOWERS_URL, attributes.get(GithubUser.FOLLOWERS_URL));
        claims.put(GithubUser.FOLLOWING_URL, attributes.get(GithubUser.FOLLOWING_URL));
        claims.put(GithubUser.GISTS_URL, attributes.get(GithubUser.GISTS_URL));
        claims.put(GithubUser.STARRED_URL, attributes.get(GithubUser.STARRED_URL));
        claims.put(GithubUser.SUBSCRIPTIONS_URL, attributes.get(GithubUser.SUBSCRIPTIONS_URL));
        claims.put(GithubUser.ORGANIZATIONS_URL, attributes.get(GithubUser.ORGANIZATIONS_URL));
        claims.put(GithubUser.REPOS_URL, attributes.get(GithubUser.REPOS_URL));
        claims.put(GithubUser.EVENTS_URL, attributes.get(GithubUser.EVENTS_URL));
        claims.put(GithubUser.RECEIVED_EVENTS_URL, attributes.get(GithubUser.RECEIVED_EVENTS_URL));
        claims.put(GithubUser.SITE_ADMIN, attributes.get(GithubUser.SITE_ADMIN));
        claims.put(GithubUser.NAME, attributes.get(GithubUser.NAME));
        claims.put(GithubUser.COMPANY, attributes.get(GithubUser.COMPANY));
        claims.put(GithubUser.LOCATION, attributes.get(GithubUser.LOCATION));
        claims.put(GithubUser.PUBLIC_REPOS, attributes.get(GithubUser.PUBLIC_REPOS));
        claims.put(GithubUser.PUBLIC_GISTS, attributes.get(GithubUser.PUBLIC_GISTS));
        claims.put(GithubUser.FOLLOWERS, attributes.get(GithubUser.FOLLOWERS));
        claims.put(GithubUser.FOLLOWING, attributes.get(GithubUser.FOLLOWING));
        claims.put(GithubUser.BIO, attributes.get(GithubUser.BIO));
        claims.put(GithubUser.BLOG, attributes.get(GithubUser.BLOG));
        claims.put(GithubUser.CREATED_AT, attributes.get(GithubUser.CREATED_AT));

        return claims;
    }
}
