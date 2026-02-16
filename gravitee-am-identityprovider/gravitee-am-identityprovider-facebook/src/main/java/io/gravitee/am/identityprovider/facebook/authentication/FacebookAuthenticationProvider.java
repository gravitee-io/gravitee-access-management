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
package io.gravitee.am.identityprovider.facebook.authentication;

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
import io.gravitee.am.identityprovider.facebook.FacebookIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.facebook.authentication.spring.FacebookAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.facebook.model.FacebookUser;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.ALL_FIELDS_PARAM;
import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.LOCATION;
import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.OTHER_FIELDS_LIST;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Import(FacebookAuthenticationProviderConfiguration.class)
public class FacebookAuthenticationProvider extends AbstractSocialAuthenticationProvider<FacebookIdentityProviderConfiguration> {

    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CODE = "code";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String FIELDS = "fields";
    private static final String APPSECRET_PROOF = "appsecret_proof";

    @Autowired
    @Qualifier("facebookWebClient")
    private WebClient client;

    @Autowired
    private FacebookIdentityProviderConfiguration configuration;

    @Autowired
    private DefaultIdentityProviderMapper mapper;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Autowired
    private DefaultIdentityProviderGroupMapper groupMapper;

    @Override
    protected FacebookIdentityProviderConfiguration getConfiguration() {
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
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return authenticate(authentication)
                .flatMap(token -> this.profile(token, authentication));
    }

    protected Maybe<Token> authenticate(Authentication authentication) {

        // Prepare body request parameters.
        final String authorizationCode = authentication.getContext().request().parameters().getFirst(configuration.getCodeParameter());

        if (authorizationCode == null || authorizationCode.isEmpty()) {
            log.debug("Authorization code is missing, skip authentication");
            return Maybe.error(new BadCredentialsException("Missing authorization code"));
        }

        MultiMap form = MultiMap.caseInsensitiveMultiMap()
                .set(CLIENT_ID, configuration.getClientId())
                .set(CLIENT_SECRET, configuration.getClientSecret())
                .set(REDIRECT_URI, (String) authentication.getContext().get(REDIRECT_URI))
                .set(CODE, authorizationCode);

        return client.postAbs(configuration.getAccessTokenUri())
                .rxSendForm(form)
                .toMaybe()
                .flatMap(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        log.error("HTTP error {} is thrown while exchanging code. The response body is: {} ", httpResponse.statusCode(), httpResponse.bodyAsString());
                        return Maybe.error(new BadCredentialsException(httpResponse.bodyAsString()));
                    }

                    return Maybe.just(new Token(httpResponse.bodyAsJsonObject().getString(ACCESS_TOKEN), TokenTypeHint.ACCESS_TOKEN));
                });
    }

    /**
     * Computes the {@code appsecret_proof} HMAC-SHA256 hash required by the Facebook Graph API.
     * When "Require App Secret" is enabled in a Facebook app's settings, all API calls must
     * include this parameter. It is harmless when not required.
     *
     * @see <a href="https://developers.facebook.com/docs/graph-api/securing-requests/">Securing Graph API Requests</a>
     */
    static String computeAppSecretProof(String accessToken, String clientSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmac);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute appsecret_proof", e);
        }
    }

    private static final String LEGACY_API_VERSION = "v8.0";

    protected Maybe<User> profile(Token accessToken, Authentication auth) {

        MultiMap form = MultiMap.caseInsensitiveMultiMap()
                .set(ACCESS_TOKEN, accessToken.value())
                .set(FIELDS, ALL_FIELDS_PARAM);

        if (!LEGACY_API_VERSION.equals(configuration.getApiVersion())) {
            form.set(APPSECRET_PROOF, computeAppSecretProof(accessToken.value(), configuration.getClientSecret()));
        }

        return client.postAbs(configuration.getUserProfileUri())
                .rxSendForm(form)
                .toMaybe()
                .flatMap(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                       return Maybe.error(new BadCredentialsException(httpResponse.bodyAsString()));
                    }

                    return Maybe.just(convert(auth.getContext(), httpResponse.bodyAsJsonObject()));
                });
    }

    private User convert(AuthenticationContext authContext, JsonObject facebookUser) {

        DefaultUser user = new DefaultUser(String.valueOf(facebookUser.getString(FacebookUser.ID)));
        user.setId(facebookUser.getString(FacebookUser.ID));

        // Set additional information.
        Map<String, Object> additionalInformation = new HashMap<>();

        // Standard claims.
        convertClaim(FacebookUser.ID, facebookUser, StandardClaims.SUB, additionalInformation);
        convertClaim(FacebookUser.NAME, facebookUser, StandardClaims.NAME, additionalInformation);

        // There is no way to retrieve username with facebook api. As it is mandatory for AM, set it to facebook ID.
        convertClaim(FacebookUser.ID, facebookUser, StandardClaims.PREFERRED_USERNAME, additionalInformation);

        // Apply user mapping.
        additionalInformation.putAll(applyUserMapping(authContext, facebookUser.getMap()));

        // Update username if user mapping has changed.
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
            user.setUsername((String) additionalInformation.get(StandardClaims.PREFERRED_USERNAME));
        }

        user.setAdditionalInformation(additionalInformation);

        // Set user roles.
        user.setRoles(applyRoleMapping(authContext, facebookUser.getMap()));
        user.setGroups(applyGroupMapping(authContext, facebookUser.getMap()));

        return user;
    }

    protected Map<String, Object> defaultClaims(Map<String, Object> _attributes) {
        JsonObject attributes = JsonObject.mapFrom(_attributes);
        Map<String, Object> claims = new HashMap<>();

        convertClaim(FacebookUser.ID, attributes, StandardClaims.SUB, claims);
        convertClaim(FacebookUser.NAME, attributes, StandardClaims.NAME, claims);
        convertClaim(FacebookUser.FIRST_NAME, attributes, StandardClaims.GIVEN_NAME, claims);
        convertClaim(FacebookUser.LAST_NAME, attributes, StandardClaims.FAMILY_NAME, claims);
        convertClaim(FacebookUser.MIDDLE_NAME, attributes, StandardClaims.MIDDLE_NAME, claims);

        // Facebook picture has a specific structure we handle specifically.
        if (attributes.containsKey(FacebookUser.PICTURE)) {
            claims.put(StandardClaims.PICTURE, attributes.getJsonObject(FacebookUser.PICTURE).getJsonObject("data").getString("url"));
        }

        convertClaim(FacebookUser.LINK, attributes, StandardClaims.PROFILE, claims);
        convertClaim(FacebookUser.EMAIL, attributes, StandardClaims.EMAIL, claims);
        convertClaim(FacebookUser.GENDER, attributes, StandardClaims.GENDER, claims);

        // Facebook birthday must be parsed and transformed to be oidc compliant.
        convertBirthDateClaim(attributes, claims);

        // Facebook address must be parsed and transformed to be oidc compliant.
        convertAddressClaim(attributes, claims);

        // All others custom Facebook claims.
        for (String field : OTHER_FIELDS_LIST) {
            convertClaim(field, attributes, field, claims);
        }

        return claims;
    }

    private void convertClaim(String sourceName, JsonObject source, String destName, Map<String, Object> dest) {

        if (dest.containsKey(destName)) {
            // Skip if already present in the dest.
            return;
        }

        Object value = source.getValue(sourceName);
        if (value != null) {
            if (value instanceof JsonObject jsonObject) {
                dest.put(destName, jsonObject.getMap());
            } else if (value instanceof JsonArray jsonArray) {
                dest.put(destName, jsonArray.getList());
            } else {
                dest.put(destName, value);
            }
        }
    }

    private void convertBirthDateClaim(JsonObject source, Map<String, Object> dest) {

        if (source.containsKey(FacebookUser.BIRTHDAY)) {
            // Facebook birthday format can be one of the following: 'MM/DD/YYYY', 'MM/DD', 'YYYY'.
            String[] split = source.getString(FacebookUser.BIRTHDAY).split("/");
            String birthdate;

            // OIDC birthdate expected format is ISO8601‑2004 (eg: YYYY-MM-DD). See https://openid.net/specs/openid-connect-core-1_0.html#Claims
            if (split.length == 3) {
                // 'MM/DD/YYYY' -> 'YYYY-MM-DD'
                birthdate = split[2] + "-" + split[0] + "-" + split[1];
            } else if (split.length == 2) {
                // 'MM/DD' -> '0000-MM-DD'
                birthdate = "0000-" + split[0] + "-" + split[1];
            } else {
                // 'YYYY -> YYYY'
                birthdate = split[0];
            }

            dest.put(StandardClaims.BIRTHDATE, birthdate);
        }
    }

    private void convertAddressClaim(JsonObject source, Map<String, Object> dest) {

        if (source.containsKey(FacebookUser.LOCATION)) {
            JsonObject location = source.getJsonObject(FacebookUser.LOCATION).getJsonObject(LOCATION);
            Map<String, Object> address = new HashMap<>();

            convertClaim("street", location, "street_address", address);
            convertClaim("city", location, "locality", address);
            convertClaim("region", location, "region", address);
            convertClaim("zip", location, "postal_code", address);
            convertClaim("country", location, "country", address);

            dest.put(StandardClaims.ADDRESS, address);
        }
    }
}
