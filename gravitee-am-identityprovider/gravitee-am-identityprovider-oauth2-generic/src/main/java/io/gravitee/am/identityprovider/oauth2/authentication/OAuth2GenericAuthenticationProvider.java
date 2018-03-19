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

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderMapper;
import io.gravitee.am.identityprovider.oauth2.authentication.spring.OAuth2GenericAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.oauth2.utils.URLEncodedUtils;
import io.gravitee.am.model.http.BasicNameValuePair;
import io.gravitee.am.model.http.NameValuePair;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
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
@Import(OAuth2GenericAuthenticationProviderConfiguration.class)
public class OAuth2GenericAuthenticationProvider implements OAuth2AuthenticationProvider {

    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CODE = "code";
    private static final String GRANT_TYPE = "grant_type";
    private static final String HTTPS_SCHEME = "https";
    private static final String DEFAULT_USER_AGENT = "Vert.x-WebClient/3.5.1";
    private static final String CLAIMS_SUB = "sub";

    @Autowired
    private HttpClient client;

    @Autowired
    private OAuth2IdentityProviderConfiguration configuration;

    @Autowired
    private OAuth2GenericIdentityProviderMapper mapper;

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
            urlParameters.add(new BasicNameValuePair(GRANT_TYPE, "authorization_code"));
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
                                JsonObject bodyResponse = body.toJsonObject();
                                emitter.onSuccess(bodyResponse.getString("access_token"));
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
                .putHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT)
                .putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

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

    private User createUser(JsonObject jsonNode) {
        User user = new DefaultUser(jsonNode.getString(CLAIMS_SUB));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(CLAIMS_SUB, jsonNode.getValue(CLAIMS_SUB));
        if (this.mapper.getMappers() != null) {
            this.mapper.getMappers().forEach((k, v) -> {
                if (jsonNode.getValue(v) != null) {
                    additionalInformation.put(k, jsonNode.getValue(v));
                }
            });
        }
        ((DefaultUser) user).setAdditonalInformation(additionalInformation);
        return user;
    }

}
