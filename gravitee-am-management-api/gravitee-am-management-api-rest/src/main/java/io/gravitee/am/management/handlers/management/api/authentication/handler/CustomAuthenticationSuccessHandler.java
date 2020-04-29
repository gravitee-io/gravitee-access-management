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
package io.gravitee.am.management.handlers.management.api.authentication.handler;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.authentication.provider.jwt.JWTGenerator;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    protected final Logger logger = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);
    private static final String SAVED_REQUEST = "GRAVITEEIO_AM_SAVED_REQUEST";
    private RequestCache requestCache = new HttpSessionRequestCache();

    @Autowired
    private JWTGenerator jwtGenerator;

    @Autowired
    private AuthenticationService authenticationService;

    @Value("${newsletter.enabled:true}")
    private boolean newsletterEnabled;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        SavedRequest savedRequest = requestCache.getRequest(request, response);

        if (savedRequest == null) {
            if (request.getSession(false).getAttribute(SAVED_REQUEST) == null) {
                super.onAuthenticationSuccess(request, response, authentication);

                return;
            } else {
                // fetch saved request from user session
                savedRequest = (SavedRequest) request.getSession(false).getAttribute(SAVED_REQUEST);
            }
        } else {
            // Store the saved HTTP request itself for redirection after successful authentication
            request.getSession(false).setAttribute(SAVED_REQUEST, savedRequest);
        }

        // finish authentication and get an enhanced principal with user information.
        User principal = authenticationService.onAuthenticationSuccess(authentication);

        // store jwt authentication cookie to secure management restricted operations
        Cookie jwtAuthenticationCookie = jwtGenerator.generateCookie(principal);
        response.addCookie(jwtAuthenticationCookie);

        String targetUrlParameter = getTargetUrlParameter();
        if (isAlwaysUseDefaultTargetUrl() || (targetUrlParameter != null && StringUtils.hasText(request.getParameter(targetUrlParameter)))) {
            requestCache.removeRequest(request, response);
            super.onAuthenticationSuccess(request, response, authentication);

            return;
        }

        clearAuthenticationAttributes(request);

        // if first login and newsletter option enabled, go to complete profile step
        if (newsletterEnabled && (long) principal.getAdditionalInformation().get("login_count") == 1) {
            getRedirectStrategy().sendRedirect(request, response, "/auth/completeProfile");
        } else {
            // replay the original request
            String targetUrl = savedRequest.getRedirectUrl();
            logger.debug("Redirecting to DefaultSavedRequest Url: " + targetUrl);
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
    }
}
