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
import io.gravitee.am.service.exception.authentication.UsernameNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.net.URI;
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

    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CODE = "code";
    private static final String HTTPS_SCHEME = "https";
    private static final String DEFAULT_USER_AGENT = "Vert.x-WebClient/3.5.1";

    @Autowired
    private HttpClient client;

    @Autowired
    private OAuth2IdentityProviderConfiguration configuration;

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return authenticate(authentication)
                .flatMap(accessToken -> profile(accessToken));
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return null;
    }

    @Override
    public OAuth2IdentityProviderConfiguration configuration() {
        return configuration;
    }

    private Maybe<String> authenticate(Authentication authentication) {
        return io.reactivex.Maybe.create(emitter -> {
            // prepare body request parameters
            List<NameValuePair> urlParameters = new ArrayList<>();
            urlParameters.add(new BasicNameValuePair(CLIENT_ID, configuration.getClientId()));
            urlParameters.add(new BasicNameValuePair(CLIENT_SECRET, configuration.getClientSecret()));
            urlParameters.add(new BasicNameValuePair(REDIRECT_URI, (String) authentication.getAdditionalInformation().get(REDIRECT_URI)));
            urlParameters.add(new BasicNameValuePair(CODE, (String) authentication.getCredentials()));
            String bodyRequest = URLEncodedUtils.format(urlParameters);

            URI requestUri = URI.create(configuration.getAccessTokenUri());
            final int port = requestUri.getPort() != -1 ? requestUri.getPort() : (HTTPS_SCHEME.equals(requestUri.getScheme()) ? 443 : 80);
            boolean ssl = HTTPS_SCHEME.equalsIgnoreCase(requestUri.getScheme());

            HttpClientRequest request = client.post(
                    new RequestOptions()
                            .setHost(requestUri.getHost())
                            .setPort(port)
                            .setSsl(ssl)
                            .setURI(requestUri.toString()), response -> {
                if (response.statusCode() != 200) {
                    emitter.onError(new BadCredentialsException(response.statusMessage()));
                } else {
                    response.bodyHandler(body -> {
                        Map<String, String> bodyResponse = URLEncodedUtils.format(body.toString());
                        emitter.onSuccess(bodyResponse.get("access_token"));
                    });
                }
            }).setChunked(true).putHeader(HttpHeaders.CONTENT_TYPE, URLEncodedUtils.CONTENT_TYPE);

            request.write(bodyRequest);
            request.end();
        });
    }

    private Maybe<User> profile(String accessToken) {
        URI requestUri = URI.create(configuration.getUserProfileUri());
        final int port = requestUri.getPort() != -1 ? requestUri.getPort() : (HTTPS_SCHEME.equals(requestUri.getScheme()) ? 443 : 80);
        boolean ssl = HTTPS_SCHEME.equalsIgnoreCase(requestUri.getScheme());
        HttpClientRequest request = client.get(
                new RequestOptions()
                        .setHost(requestUri.getHost())
                        .setPort(port)
                        .setSsl(ssl)
                        .setURI(requestUri.toString()))
                        // https://developer.github.com/v3/#user-agent-required
                        .putHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT)
                        .putHeader(HttpHeaders.AUTHORIZATION, "token " + accessToken);

        return request
                .toObservable()
                .flatMap(httpClientResponse -> {
                    if (httpClientResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpClientResponse.statusMessage());
                    }
                    return Observable.just(httpClientResponse);
                })
                .flatMap(HttpClientResponse::toObservable)
                // we reduce the response chunks into a single one to have access to the full JSON object
                .reduce(Buffer.buffer(), Buffer::appendBuffer)
                .map(buffer -> createUser(buffer.toJsonObject()))
                .toMaybe()
                // Vert.x requires the HttpClientRequest.end() method to be invoked to signaling that the request can be sent
                .doOnSubscribe(subscription -> request.end());
    }

    private User createUser(JsonObject jsonObject) {
        User user = new DefaultUser(jsonObject.getString(GithubUser.LOGIN));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("sub", jsonObject.getValue(GithubUser.LOGIN));
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
        additionalInformation.put(GithubUser.EMAIL, jsonObject.getValue(GithubUser.EMAIL));
        additionalInformation.put(GithubUser.PUBLIC_REPOS, jsonObject.getValue(GithubUser.PUBLIC_REPOS));
        additionalInformation.put(GithubUser.PUBLIC_GISTS, jsonObject.getValue(GithubUser.PUBLIC_GISTS));
        additionalInformation.put(GithubUser.FOLLOWERS, jsonObject.getValue(GithubUser.FOLLOWERS));
        additionalInformation.put(GithubUser.FOLLOWING, jsonObject.getValue(GithubUser.FOLLOWING));
        additionalInformation.put(GithubUser.LOCATION, jsonObject.getValue(GithubUser.LOCATION));
        additionalInformation.put(GithubUser.EMAIL, jsonObject.getValue(GithubUser.EMAIL));
        ((DefaultUser) user).setAdditonalInformation(additionalInformation);
        return user;
    }
}
