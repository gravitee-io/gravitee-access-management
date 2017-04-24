package io.gravitee.am.gateway.handler.oauth2.provider.security.web.authentication;

import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAwareAuthenticationDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, Map<String, String>> {

    public ClientAwareAuthenticationDetailsSource() {
    }

    public Map<String, String> buildDetails(HttpServletRequest context) {
        WebAuthenticationDetails details = new WebAuthenticationDetails(context);
        Map<String, String> mapDetails = new HashMap<>();
        mapDetails.put("remote_address", details.getRemoteAddress());
        mapDetails.put("session_id", details.getSessionId());
        mapDetails.put(OAuth2Utils.CLIENT_ID, context.getParameter(OAuth2Utils.CLIENT_ID));

        return mapDetails;
    }
}
