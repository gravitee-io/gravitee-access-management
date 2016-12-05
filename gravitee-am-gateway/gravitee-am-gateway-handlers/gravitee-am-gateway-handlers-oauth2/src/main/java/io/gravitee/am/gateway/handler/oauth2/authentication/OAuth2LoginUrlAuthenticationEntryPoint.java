package io.gravitee.am.gateway.handler.oauth2.authentication;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2LoginUrlAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {

    public OAuth2LoginUrlAuthenticationEntryPoint(String loginFormUrl) {
        super(loginFormUrl);
    }

    @Override
    protected String determineUrlToUseForThisRequest(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        String url = super.determineUrlToUseForThisRequest(request, response, exception);
        // Does not work because ant pattern matcher does not allow to pass query parameters
    //    return UriComponentsBuilder.fromPath(url).queryParam("client_id", request.getParameter("client_id")).toUriString();
        return url;
    }
}
