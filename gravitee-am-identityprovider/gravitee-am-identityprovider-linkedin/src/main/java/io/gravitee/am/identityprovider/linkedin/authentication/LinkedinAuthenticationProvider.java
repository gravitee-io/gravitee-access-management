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
package io.gravitee.am.identityprovider.linkedin.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.common.oauth2.authentication.AbstractSocialAuthenticationProvider;
import io.gravitee.am.identityprovider.common.oauth2.utils.URLEncodedUtils;
import io.gravitee.am.identityprovider.linkedin.LinkedinIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.linkedin.authentication.model.LinkedinUser;
import io.gravitee.am.identityprovider.linkedin.authentication.spring.LinkedinAuthenticationProviderConfiguration;
import io.gravitee.am.model.http.BasicNameValuePair;
import io.gravitee.am.model.http.NameValuePair;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.util.*;

import static io.gravitee.am.common.oauth2.Parameters.GRANT_TYPE;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(LinkedinAuthenticationProviderConfiguration.class)
public class LinkedinAuthenticationProvider extends AbstractSocialAuthenticationProvider<LinkedinIdentityProviderConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkedinAuthenticationProvider.class);
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CODE = "code";
    // r_liteprofile is available by default with "sign in with linkedin"
    // r_basicprofile is available with enterprise grade linkedin products
    // r_fullprofile seems to be available only if the application owner belong to LinkedIn partner program
    // https://docs.microsoft.com/en-us/linkedin/shared/integrations/people/profile-api?context=linkedin/consumer/context
    private static final String SCOPE_EMAIL = "r_emailaddress";

    @Autowired
    @Qualifier("linkedinWebClient")
    private WebClient client;

    @Autowired
    private LinkedinIdentityProviderConfiguration configuration;

    @Autowired
    private DefaultIdentityProviderMapper mapper;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Override
    protected LinkedinIdentityProviderConfiguration getConfiguration() {
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
        urlParameters.add(new BasicNameValuePair(REDIRECT_URI, (String) authentication.getContext().get(REDIRECT_URI)));
        urlParameters.add(new BasicNameValuePair(CODE, authorizationCode));
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

                    JsonObject response = httpResponse.bodyAsJsonObject();
                    String accessToken = response.getString("access_token");
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

                    return createUser(authentication.getContext(), httpClientResponse.bodyAsJsonObject());
                })
                .flatMap(user -> requestEmailAddress(accessToken).map(address -> {
                    // ask the emailAddress and complete User description using the value
                    address.ifPresent(value -> {
                        ((DefaultUser) user).setEmail(value);
                        ((DefaultUser) user).setUsername(value);
                        user.getAdditionalInformation().put(StandardClaims.EMAIL, value);
                        user.getAdditionalInformation().put(StandardClaims.PREFERRED_USERNAME, value);
                    });
                    return user;
                }));
    }

    private Maybe<Optional<String>> requestEmailAddress(Token accessToken) {
        if (configuration.getScopes().contains(SCOPE_EMAIL)) {
            return client.getAbs(configuration.getUserEmailAddressUri())
                    .putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.getValue())
                    .rxSend()
                    .toMaybe()
                    .map(httpClientResponse -> {
                        if (httpClientResponse.statusCode() == 200) {
                            String email = null;
                            JsonObject payload = httpClientResponse.bodyAsJsonObject();
                            if (payload != null && payload.containsKey("elements")) {
                                JsonArray elements = payload.getJsonArray("elements");
                                for (int i = 0; i < elements.size(); ++i) {
                                    JsonObject emailPayload = elements.getJsonObject(i);
                                    if (emailPayload != null && emailPayload.containsKey("handle~")) {
                                        JsonObject handle = emailPayload.getJsonObject("handle~");
                                        email = handle.getString("emailAddress");
                                        break;
                                    }
                                }
                            }
                            return Optional.ofNullable(email);
                        } else {
                            LOGGER.warn("Unable to retrieve the LinkedIn email address : {}", httpClientResponse.statusMessage());
                            return Optional.empty(); // do not reject the authentication due to missing emailAddress
                        }
                    });
        } else {
            return Maybe.just(Optional.empty());
        }
    }

    private User createUser(AuthenticationContext authContext, JsonObject profileInfo) {
        String userId = profileInfo.getString(LinkedinUser.ID);
        DefaultUser user = new DefaultUser(userId);
        user.setId(userId);
        user.setFirstName(profileInfo.getString(LinkedinUser.FIRSTNAME));
        user.setLastName(profileInfo.getString(LinkedinUser.LASTNAME));

        user.setAdditionalInformation(applyUserMapping(profileInfo.getMap()));
        user.setRoles(applyRoleMapping(authContext, profileInfo.getMap()));

        return user;
    }

    @Override
    protected Map<String, Object> defaultClaims(Map<String, Object> attributes) {
        Map<String, Object> additionalInformation = new HashMap<>();

        JsonObject json = JsonObject.mapFrom(attributes);
        if (json.containsKey(LinkedinUser.ID)) {
            additionalInformation.put(StandardClaims.SUB, json.getString(LinkedinUser.ID));
        }
        if (json.containsKey(LinkedinUser.LASTNAME)) {
            additionalInformation.put(StandardClaims.FAMILY_NAME, json.getString(LinkedinUser.LASTNAME));
        }
        if (json.containsKey(LinkedinUser.FIRSTNAME)) {
            additionalInformation.put(StandardClaims.GIVEN_NAME, json.getString(LinkedinUser.FIRSTNAME));
        }
        if (json.containsKey(LinkedinUser.PROFILE_URL)) {
            additionalInformation.put(StandardClaims.PROFILE, json.getString(LinkedinUser.PROFILE_URL));
        }
        // available with scope r_basicprofile
        if (json.containsKey(LinkedinUser.MAIDENNAME)) {
            additionalInformation.put("MAIDEN_NAME", json.getString(LinkedinUser.MAIDENNAME));
        }
        if (json.containsKey(LinkedinUser.HEADLINE)) {
            additionalInformation.put("HEADLINE", json.getString(LinkedinUser.HEADLINE));
        }

        JsonObject profilePicture = json.getJsonObject("profilePicture");
        if (profilePicture != null) {
            JsonObject displayImage = profilePicture.getJsonObject("displayImage~");
            JsonArray elements = displayImage.getJsonArray("elements");
            if (elements != null && !elements.isEmpty()) {
                JsonArray imgIdentifiers = elements.getJsonObject(0).getJsonArray("identifiers");
                if (imgIdentifiers != null) {
                    for( int i = 0;  i < imgIdentifiers.size(); i++) {
                        JsonObject imgId = imgIdentifiers.getJsonObject(i);
                        String mediaType = imgId.getString("mediaType");
                        if ("EXTERNAL_URL".equalsIgnoreCase(imgId.getString("identifierType")) && mediaType != null && mediaType.startsWith("image")) {
                            additionalInformation.put(StandardClaims.PICTURE, imgIdentifiers.getJsonObject(i).getString("identifier"));
                            break;
                        }
                    }
                }
            }
        }

        return additionalInformation;
    }
}
