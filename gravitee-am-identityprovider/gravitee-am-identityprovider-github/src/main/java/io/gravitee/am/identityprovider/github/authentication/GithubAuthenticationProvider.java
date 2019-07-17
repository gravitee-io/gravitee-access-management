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

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.github.authentication.spring.GithubAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.github.model.GithubUser;
import io.gravitee.am.identityprovider.github.utils.URLEncodedUtils;
import io.gravitee.am.model.http.BasicNameValuePair;
import io.gravitee.am.model.http.NameValuePair;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(GithubAuthenticationProviderConfiguration.class)
public class GithubAuthenticationProvider implements OAuth2AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubAuthenticationProvider.class);
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CODE = "code";

    @Autowired
    private WebClient client;

    @Autowired
    private OAuth2IdentityProviderConfiguration configuration;

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return authenticate(authentication)
                .flatMap(accessToken -> profile(accessToken));
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return Maybe.empty();
    }

    @Override
    public OAuth2IdentityProviderConfiguration configuration() {
        return configuration;
    }

    private Maybe<String> authenticate(Authentication authentication) {
        // prepare body request parameters
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair(CLIENT_ID, configuration.getClientId()));
        urlParameters.add(new BasicNameValuePair(CLIENT_SECRET, configuration.getClientSecret()));
        urlParameters.add(new BasicNameValuePair(REDIRECT_URI, (String) authentication.getContext().get(REDIRECT_URI)));
        urlParameters.add(new BasicNameValuePair(CODE, (String) authentication.getCredentials()));
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

                    Map<String, String> bodyResponse = URLEncodedUtils.format(httpResponse.bodyAsString());
                    return bodyResponse.get("access_token");
                });
    }

    private Maybe<User> profile(String accessToken) {
        return client.getAbs(configuration.getUserProfileUri())
                .putHeader(HttpHeaders.AUTHORIZATION, "token " + accessToken)
                .rxSend()
                .toMaybe()
                .map(httpClientResponse -> {
                    if (httpClientResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpClientResponse.statusMessage());
                    }

                    return createUser(httpClientResponse.bodyAsJsonObject());
                });
    }

    private User createUser(JsonObject jsonObject) {
        User user = new DefaultUser(jsonObject.getString(GithubUser.LOGIN));
        ((DefaultUser) user).setId(String.valueOf(jsonObject.getValue(GithubUser.ID)));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();

        // Standard claims
        additionalInformation.put(StandardClaims.SUB, jsonObject.getValue(GithubUser.ID));
        additionalInformation.put(StandardClaims.NAME, jsonObject.getValue(GithubUser.NAME));
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, jsonObject.getValue(GithubUser.LOGIN));

        // try to get the first name and last name
        try {
            String[] fullName = jsonObject.getString(GithubUser.NAME).split("\\s+");
            if (fullName.length > 0) {
                additionalInformation.put(StandardClaims.GIVEN_NAME, fullName[0]);
            }
            if (fullName.length > 1) {
                additionalInformation.put(StandardClaims.FAMILY_NAME, fullName[1]);
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to resolve Github user full name : {}", jsonObject.getValue(GithubUser.NAME), e);
        }

        additionalInformation.put(StandardClaims.PROFILE, jsonObject.getValue(GithubUser.HTML_URL));
        additionalInformation.put(StandardClaims.PICTURE, jsonObject.getValue(GithubUser.AVATAR_URL));
        additionalInformation.put(StandardClaims.WEBSITE, jsonObject.getValue(GithubUser.BLOG));
        additionalInformation.put(StandardClaims.EMAIL, jsonObject.getValue(GithubUser.EMAIL));
        additionalInformation.put(StandardClaims.ZONEINFO, jsonObject.getValue(GithubUser.LOCATION));
        additionalInformation.put(StandardClaims.UPDATED_AT, jsonObject.getValue(GithubUser.UPDATED_AT));

        // custom GitHub claims
        additionalInformation.put(GithubUser.AVATAR_URL, jsonObject.getValue(GithubUser.AVATAR_URL));
        additionalInformation.put(GithubUser.GRAVATAR_ID, jsonObject.getValue(GithubUser.GRAVATAR_ID));
        additionalInformation.put(GithubUser.URL, jsonObject.getValue(GithubUser.URL));
        additionalInformation.put(GithubUser.HTML_URL, jsonObject.getValue(GithubUser.HTML_URL));
        additionalInformation.put(GithubUser.FOLLOWERS_URL, jsonObject.getValue(GithubUser.FOLLOWERS_URL));
        additionalInformation.put(GithubUser.FOLLOWING_URL, jsonObject.getValue(GithubUser.FOLLOWING_URL));
        additionalInformation.put(GithubUser.GISTS_URL, jsonObject.getValue(GithubUser.GISTS_URL));
        additionalInformation.put(GithubUser.STARRED_URL, jsonObject.getValue(GithubUser.STARRED_URL));
        additionalInformation.put(GithubUser.SUBSCRIPTIONS_URL, jsonObject.getValue(GithubUser.SUBSCRIPTIONS_URL));
        additionalInformation.put(GithubUser.ORGANIZATIONS_URL, jsonObject.getValue(GithubUser.ORGANIZATIONS_URL));
        additionalInformation.put(GithubUser.REPOS_URL, jsonObject.getValue(GithubUser.REPOS_URL));
        additionalInformation.put(GithubUser.EVENTS_URL, jsonObject.getValue(GithubUser.EVENTS_URL));
        additionalInformation.put(GithubUser.RECEIVED_EVENTS_URL, jsonObject.getValue(GithubUser.RECEIVED_EVENTS_URL));
        additionalInformation.put(GithubUser.SITE_ADMIN, jsonObject.getBoolean(GithubUser.SITE_ADMIN));
        additionalInformation.put(GithubUser.NAME, jsonObject.getValue(GithubUser.NAME));
        additionalInformation.put(GithubUser.COMPANY, jsonObject.getValue(GithubUser.COMPANY));
        additionalInformation.put(GithubUser.LOCATION, jsonObject.getValue(GithubUser.LOCATION));
        additionalInformation.put(GithubUser.PUBLIC_REPOS, jsonObject.getValue(GithubUser.PUBLIC_REPOS));
        additionalInformation.put(GithubUser.PUBLIC_GISTS, jsonObject.getValue(GithubUser.PUBLIC_GISTS));
        additionalInformation.put(GithubUser.FOLLOWERS, jsonObject.getValue(GithubUser.FOLLOWERS));
        additionalInformation.put(GithubUser.FOLLOWING, jsonObject.getValue(GithubUser.FOLLOWING));
        additionalInformation.put(GithubUser.BIO, jsonObject.getValue(GithubUser.BIO));
        additionalInformation.put(GithubUser.BLOG, jsonObject.getValue(GithubUser.BLOG));
        additionalInformation.put(GithubUser.CREATED_AT, jsonObject.getValue(GithubUser.CREATED_AT));
        ((DefaultUser) user).setAdditionalInformation(additionalInformation);
        return user;
    }
}
