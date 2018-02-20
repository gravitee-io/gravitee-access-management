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
package io.gravitee.am.management.handlers.oauth2.provider.token;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.UserAuthenticationConverter;

import java.util.Collection;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultIntrospectionAccessTokenConverter extends DefaultAccessTokenConverter {

    /**
     * Boolean indicator of whether or not the presented token is currently active as described by
     * <a href="http://tools.ietf.org/html/draft-ietf-oauth-v2-22#section-3.3">Section 3.3</a>
     */
    public static String ACTIVE = "active";

    /**
     * Human-readable identifier for the resource owner who
     * authorized this token as described by <a href="https://tools.ietf.org/html/rfc7662#section-2.2">Section 2.2</a>
     */
    public static String USERNAME = "username";

    @Override
    public Map<String, ?> convertAccessToken(OAuth2AccessToken token, OAuth2Authentication authentication) {
        Map<String, Object> response = (Map<String, Object>) super.convertAccessToken(token, authentication);

        response.put(ACTIVE, !token.isExpired());
        response.put(OAuth2AccessToken.TOKEN_TYPE, "bearer");

        Authentication userAuth = authentication.getUserAuthentication();
        if (userAuth != null) {
            response.remove(UserAuthenticationConverter.USERNAME);
            response.put(USERNAME, userAuth.getName());
            response.remove(UserAuthenticationConverter.AUTHORITIES);
        }

        Object rawScopes = response.remove(OAuth2AccessToken.SCOPE);
        StringBuilder sb = new StringBuilder();
        if (rawScopes != null && rawScopes instanceof Collection) {
            Collection scopes = (Collection)rawScopes;
            for (Object scope : scopes) {
                sb.append(scope).append(" ");
            }
        }

        response.put(OAuth2AccessToken.SCOPE, sb.toString().trim());

        return response;
    }
}