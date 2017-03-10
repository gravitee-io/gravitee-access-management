package io.gravitee.am.gateway.handler.oauth2.provider.token;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;

import java.util.Collection;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultIntrospectionAccessTokenConverter extends DefaultAccessTokenConverter {

    @Override
    public Map<String, ?> convertAccessToken(OAuth2AccessToken token, OAuth2Authentication authentication) {
        Map<String, Object> response = (Map<String, Object>) super.convertAccessToken(token, authentication);

        response.put("active", !token.isExpired());

        Authentication userAuth = authentication.getUserAuthentication();
        if (userAuth != null) {
            response.remove("user_name");
            response.put("username", userAuth.getName());
        }

        Object rawScopes = response.remove("scope");
        StringBuilder sb = new StringBuilder();
        if (rawScopes != null && rawScopes instanceof Collection) {
            Collection scopes = (Collection)rawScopes;
            for (Object scope : scopes) {
                sb.append(scope);
                sb.append(" ");
            }
        }

        response.put("scope", sb.toString().trim());

        return response;
    }
}