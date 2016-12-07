package io.gravitee.am.gateway.handler.oauth2.handler;

import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

    private static final String LOGOUT_URL_PARAMETER = "target_url";

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
        String logoutRedirectUrl = request.getParameter(LOGOUT_URL_PARAMETER);
        if (logoutRedirectUrl != null && !logoutRedirectUrl.isEmpty()) {
            setTargetUrlParameter(LOGOUT_URL_PARAMETER);
        }
        return super.determineTargetUrl(request, response);
    }
}
