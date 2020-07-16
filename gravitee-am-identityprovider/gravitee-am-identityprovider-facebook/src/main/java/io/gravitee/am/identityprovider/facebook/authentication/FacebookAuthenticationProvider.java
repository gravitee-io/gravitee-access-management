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
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.identityprovider.facebook.FacebookIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.facebook.FacebookIdentityProviderMapper;
import io.gravitee.am.identityprovider.facebook.FacebookIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.facebook.authentication.spring.FacebookAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.facebook.model.FacebookUser;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.util.*;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;
import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(FacebookAuthenticationProviderConfiguration.class)
public class FacebookAuthenticationProvider implements SocialAuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FacebookAuthenticationProvider.class);
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CODE = "code";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String FIELDS = "fields";

    @Autowired
    @Qualifier("facebookWebClient")
    private WebClient client;

    @Autowired
    private FacebookIdentityProviderConfiguration configuration;

    @Autowired
    private FacebookIdentityProviderMapper mapper;

    @Autowired
    private FacebookIdentityProviderRoleMapper roleMapper;

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
            LOGGER.error("An error occurs while building Facebook Sign In URL", e);
            return null;
        }
    }

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return authenticate(authentication)
                .flatMap(this::profile);
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return Maybe.empty();
    }

    private Maybe<String> authenticate(Authentication authentication) {

        // Prepare body request parameters.
        final String authorizationCode = authentication.getContext().request().parameters().getFirst(configuration.getCodeParameter());

        if (authorizationCode == null || authorizationCode.isEmpty()) {
            LOGGER.debug("Authorization code is missing, skip authentication");
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
                        return Maybe.error(new BadCredentialsException(httpResponse.bodyAsString()));
                    }

                    return Maybe.just(httpResponse.bodyAsJsonObject().getString(ACCESS_TOKEN));
                });
    }

    private Maybe<User> profile(String accessToken) {

        return client.postAbs(configuration.getUserProfileUri())
                .rxSendForm(MultiMap.caseInsensitiveMultiMap()
                        .set(ACCESS_TOKEN, accessToken)
                        .set(FIELDS, ALL_FIELDS_PARAM))
                .toMaybe()
                .flatMap(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                       return Maybe.error(new BadCredentialsException(httpResponse.bodyAsString()));
                    }

                    return Maybe.just(convert(httpResponse.bodyAsJsonObject()));
                });
    }

    private User convert(JsonObject facebookUser) {

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
        additionalInformation.putAll(applyUserMapping(facebookUser));

        // Update username if user mapping has changed.
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
            user.setUsername((String) additionalInformation.get(StandardClaims.PREFERRED_USERNAME));
        }

        user.setAdditionalInformation(additionalInformation);

        // Set user roles.
        user.setRoles(applyRoleMapping(facebookUser));

        return user;
    }

    /**
     * TODO: Should be mutualized across idps.
     */
    private Map<String, Object> applyUserMapping(JsonObject attributes) {
        if (!mappingEnabled()) {
            return defaultClaims(attributes);
        }

        Map<String, Object> claims = new HashMap<>();
        this.mapper.getMappers().forEach((k, v) -> {
            if (attributes.containsKey(v)) {
                claims.put(k, attributes.getValue(v));
            }
        });
        return claims;
    }

    /**
     * TODO: Should be mutualized across idps.
     */
    private List<String> applyRoleMapping(JsonObject attributes) {
        if (!roleMappingEnabled()) {
            return Collections.emptyList();
        }

        Set<String> roles = new HashSet<>();
        roleMapper.getRoles().forEach((role, users) -> {
            Arrays.asList(users).forEach(u -> {
                // role mapping have the following syntax userAttribute=userValue
                String[] roleMapping = u.split("=", 2);
                String userAttribute = roleMapping[0];
                String userValue = roleMapping[1];
                if (attributes.containsKey(userAttribute)) {
                    Object attribute = attributes.getValue(userAttribute);
                    // attribute is a list
                    if (attribute instanceof Collection && ((Collection) attribute).contains(userValue)) {
                        roles.add(role);
                    } else if (userValue.equals(attributes.getValue(userAttribute))) {
                        roles.add(role);
                    }
                }
            });
        });

        return new ArrayList<>(roles);
    }

    private Map<String, Object> defaultClaims(JsonObject attributes) {
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

    /**
     * TODO: Should be mutualized across idps.
     */
    private boolean mappingEnabled() {
        return this.mapper != null && this.mapper.getMappers() != null && !this.mapper.getMappers().isEmpty();
    }

    /**
     * TODO: Should be mutualized across idps.
     */
    private boolean roleMappingEnabled() {
        return this.roleMapper != null && this.roleMapper.getRoles() != null && !this.roleMapper.getRoles().isEmpty();
    }

    private void convertClaim(String sourceName, JsonObject source, String destName, Map<String, Object> dest) {

        if (dest.containsKey(destName)) {
            // Skip if already present in the dest.
            return;
        }

        Object value = source.getValue(sourceName);
        if (value != null) {
            if (value instanceof JsonObject) {
                dest.put(destName, ((JsonObject) value).getMap());
            } else if (value instanceof JsonArray) {
                dest.put(destName, ((JsonArray) value).getList());
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
