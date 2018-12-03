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

import io.gravitee.am.common.oidc.StandardClaims;
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
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
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
@Import(OAuth2GenericAuthenticationProviderConfiguration.class)
public class OAuth2GenericAuthenticationProvider implements OAuth2AuthenticationProvider {

    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CODE = "code";
    private static final String GRANT_TYPE = "grant_type";

    @Autowired
    private WebClient client;

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
        urlParameters.add(new BasicNameValuePair(REDIRECT_URI, (String) authentication.getAdditionalInformation().get(REDIRECT_URI)));
        urlParameters.add(new BasicNameValuePair(CODE, (String) authentication.getCredentials()));
        urlParameters.add(new BasicNameValuePair(GRANT_TYPE, "authorization_code"));
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

                    return httpResponse.bodyAsJsonObject().getString("access_token");
                });

    }

    private Maybe<User> profile(String accessToken) {
        return client.getAbs(configuration.getUserProfileUri())
                .putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .rxSend()
                .toMaybe()
                .map(httpClientResponse -> {
                    if (httpClientResponse.statusCode() != 200) {
                        throw new BadCredentialsException(httpClientResponse.statusMessage());
                    }

                    return createUser(httpClientResponse.bodyAsJsonObject());
                });
    }

    private User createUser(JsonObject jsonNode) {
        String username = jsonNode.containsKey(StandardClaims.PREFERRED_USERNAME) ? jsonNode.getString(StandardClaims.PREFERRED_USERNAME) : jsonNode.getString(StandardClaims.SUB);
        User user = new DefaultUser(username);
        ((DefaultUser) user).setId(jsonNode.getString(StandardClaims.SUB));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, jsonNode.getValue(StandardClaims.SUB));
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, username);
        if (this.mapper.getMappers() != null && !this.mapper.getMappers().isEmpty()) {
            this.mapper.getMappers().forEach((k, v) -> {
                if (jsonNode.getValue(v) != null) {
                    additionalInformation.put(k, jsonNode.getValue(v));
                }
            });
        } else {
            // set default standard claims
            StandardClaims.claims().stream()
                    .filter(claimName -> jsonNode.containsKey(claimName))
                    .forEach(claimName -> additionalInformation.put(claimName, jsonNode.getValue(claimName)));
        }
        ((DefaultUser) user).setAdditionalInformation(additionalInformation);
        return user;
    }

}
