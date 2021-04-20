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

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.identityprovider.github.GithubIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.github.GithubIdentityProviderMapper;
import io.gravitee.am.identityprovider.github.GithubIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.github.authentication.spring.GithubAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.github.model.GithubUser;
import io.gravitee.am.identityprovider.github.utils.URLEncodedUtils;
import io.gravitee.am.model.http.BasicNameValuePair;
import io.gravitee.am.model.http.NameValuePair;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(GithubAuthenticationProviderConfiguration.class)
public class GithubAuthenticationProvider implements SocialAuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubAuthenticationProvider.class);
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
    private GithubIdentityProviderMapper mapper;

    @Autowired
    private GithubIdentityProviderRoleMapper roleMapper;

    @Override
    public Request signInUrl(String redirectUri) {
        try {
            UriBuilder builder = UriBuilder.fromHttpUrl(configuration.getUserAuthorizationUri());
            builder.addParameter(Parameters.CLIENT_ID, configuration.getClientId());
            builder.addParameter(Parameters.REDIRECT_URI, redirectUri);
            builder.addParameter(Parameters.RESPONSE_TYPE, configuration.getResponseType());
            if (configuration.getScopes() != null && !configuration.getScopes().isEmpty()) {
                builder.addParameter(Parameters.SCOPE, String.join(SCOPE_DELIMITER, configuration.getScopes()));
            }

            Request request = new Request();
            request.setMethod(HttpMethod.GET);
            request.setUri(builder.build().toString());
            return request;
        } catch (Exception e) {
            LOGGER.error("An error occurs while building GitHub Sign In URL", e);
            return null;
        }
    }

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return authenticate(authentication).flatMap(accessToken -> profile(accessToken));
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return Maybe.empty();
    }

    private Maybe<String> authenticate(Authentication authentication) {
        // prepare body request parameters
        final String authorizationCode = authentication.getContext().request().parameters().getFirst(configuration.getCodeParameter());
        if (authorizationCode == null || authorizationCode.isEmpty()) {
            LOGGER.debug("Authorization code is missing, skip authentication");
            return Maybe.error(new BadCredentialsException("Missing authorization code"));
        }
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair(CLIENT_ID, configuration.getClientId()));
        urlParameters.add(new BasicNameValuePair(CLIENT_SECRET, configuration.getClientSecret()));
        urlParameters.add(new BasicNameValuePair(REDIRECT_URI, (String) authentication.getContext().get(REDIRECT_URI)));
        urlParameters.add(new BasicNameValuePair(CODE, authorizationCode));
        String bodyRequest = URLEncodedUtils.format(urlParameters);

        return client
            .postAbs(configuration.getAccessTokenUri())
            .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyRequest.length()))
            .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
            .rxSendBuffer(Buffer.buffer(bodyRequest))
            .toMaybe()
            .map(
                httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpResponse.statusMessage());
                    }

                    Map<String, String> bodyResponse = URLEncodedUtils.format(httpResponse.bodyAsString());
                    return bodyResponse.get("access_token");
                }
            );
    }

    private Maybe<User> profile(String accessToken) {
        return client
            .getAbs(configuration.getUserProfileUri())
            .putHeader(HttpHeaders.AUTHORIZATION, "token " + accessToken)
            .rxSend()
            .toMaybe()
            .map(
                httpClientResponse -> {
                    if (httpClientResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpClientResponse.statusMessage());
                    }

                    return createUser(httpClientResponse.bodyAsJsonObject().getMap());
                }
            );
    }

    private User createUser(Map<String, Object> attributes) {
        User user = new DefaultUser(String.valueOf(attributes.get(GithubUser.LOGIN)));
        ((DefaultUser) user).setId(String.valueOf(attributes.get(GithubUser.ID)));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        // Standard claims
        additionalInformation.put(StandardClaims.SUB, attributes.get(GithubUser.ID));
        additionalInformation.put(StandardClaims.NAME, attributes.get(GithubUser.NAME));
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, attributes.get(GithubUser.LOGIN));
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

    private Map<String, Object> applyUserMapping(Map<String, Object> attributes) {
        if (!mappingEnabled()) {
            return defaultClaims(attributes);
        }

        Map<String, Object> claims = new HashMap<>();
        this.mapper.getMappers()
            .forEach(
                (k, v) -> {
                    if (attributes.containsKey(v)) {
                        claims.put(k, attributes.get(v));
                    }
                }
            );
        return claims;
    }

    private List<String> applyRoleMapping(Map<String, Object> attributes) {
        if (!roleMappingEnabled()) {
            return Collections.emptyList();
        }

        Set<String> roles = new HashSet<>();
        roleMapper
            .getRoles()
            .forEach(
                (role, users) -> {
                    Arrays
                        .asList(users)
                        .forEach(
                            u -> {
                                // role mapping have the following syntax userAttribute=userValue
                                String[] roleMapping = u.split("=", 2);
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
                            }
                        );
                }
            );

        return new ArrayList<>(roles);
    }

    private Map<String, Object> defaultClaims(Map<String, Object> attributes) {
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
            LOGGER.debug("Unable to resolve Github user full name : {}", attributes.get(GithubUser.NAME), e);
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

    private boolean mappingEnabled() {
        return this.mapper != null && this.mapper.getMappers() != null && !this.mapper.getMappers().isEmpty();
    }

    private boolean roleMappingEnabled() {
        return this.roleMapper != null && this.roleMapper.getRoles() != null && !this.roleMapper.getRoles().isEmpty();
    }
}
