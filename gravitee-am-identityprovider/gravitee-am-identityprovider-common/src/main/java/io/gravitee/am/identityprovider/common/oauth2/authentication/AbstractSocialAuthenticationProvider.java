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
package io.gravitee.am.identityprovider.common.oauth2.authentication;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.identityprovider.api.social.SocialIdentityProviderConfiguration;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.Maybe;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractSocialAuthenticationProvider<T extends SocialIdentityProviderConfiguration> implements SocialAuthenticationProvider {

    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    protected abstract T getConfiguration();
    protected abstract IdentityProviderMapper getIdentityProviderMapper();
    protected abstract IdentityProviderRoleMapper getIdentityProviderRoleMapper();
    protected abstract WebClient getClient();

    @Override
    public Request signInUrl(String redirectUri, String state) {
        try {
            UriBuilder builder = UriBuilder.fromHttpUrl(getConfiguration().getUserAuthorizationUri());
            builder.addParameter(Parameters.CLIENT_ID, getConfiguration().getClientId());
            builder.addParameter(Parameters.REDIRECT_URI, redirectUri);
            builder.addParameter(Parameters.RESPONSE_TYPE, getConfiguration().getResponseType());
            if (getConfiguration().getScopes() != null && !getConfiguration().getScopes().isEmpty()) {
                builder.addParameter(Parameters.SCOPE, String.join(SCOPE_DELIMITER, getConfiguration().getScopes()));
            }

            if(!StringUtils.isEmpty(state)) {
                builder.addParameter(Parameters.STATE, state);
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
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return authenticate(authentication)
                .flatMap(accessToken -> profile(accessToken, authentication));
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return Maybe.empty();
    }


    protected Map<String, Object> applyUserMapping(Map<String, Object> attributes) {
        if (!mappingEnabled()) {
            return defaultClaims(attributes);
        }

        Map<String, Object> claims = new HashMap<>();
        this.getIdentityProviderMapper().getMappers().forEach((k, v) -> {
            if (attributes.containsKey(v)) {
                claims.put(k, attributes.get(v));
            }
        });
        return claims;
    }

    protected List<String> applyRoleMapping(Map<String, Object> attributes) {
        if (!roleMappingEnabled()) {
            return Collections.emptyList();
        }

        Set<String> roles = new HashSet<>();
        getIdentityProviderRoleMapper().getRoles().forEach((role, users) -> {
            Arrays.asList(users).forEach(u -> {
                // role mapping have the following syntax userAttribute=userValue
                String[] roleMapping = u.split("=",2);
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
            });
        });

        return new ArrayList<>(roles);
    }

    protected abstract Maybe<Token> authenticate(Authentication authentication);

    protected abstract Maybe<User> profile(Token token, Authentication authentication);

    protected abstract Map<String, Object> defaultClaims(Map<String, Object> attributes);

    private boolean mappingEnabled() {
        return this.getIdentityProviderMapper() != null
                && this.getIdentityProviderMapper().getMappers() != null
                && !this.getIdentityProviderMapper().getMappers().isEmpty();
    }

    private boolean roleMappingEnabled() {
        return this.getIdentityProviderRoleMapper() != null
                && this.getIdentityProviderRoleMapper().getRoles() != null
                && !this.getIdentityProviderRoleMapper().getRoles().isEmpty();
    }


    protected final class Token {
        private String value;
        private String secret;
        private TokenTypeHint typeHint;

        public Token(String value, TokenTypeHint typeHint) {
            this(value, null, typeHint);
        }

        public Token(String value, String secret, TokenTypeHint typeHint) {
            this.value = value;
            this.secret = secret;
            this.typeHint = typeHint;
        }

        public String getValue() {
            return value;
        }

        public String getSecret() {
            return secret;
        }

        public TokenTypeHint getTypeHint() {
            return typeHint;
        }
    }
}
