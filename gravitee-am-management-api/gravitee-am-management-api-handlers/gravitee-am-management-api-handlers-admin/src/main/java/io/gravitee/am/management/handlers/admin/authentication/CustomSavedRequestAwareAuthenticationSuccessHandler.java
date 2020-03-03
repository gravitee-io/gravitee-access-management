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
package io.gravitee.am.management.handlers.admin.authentication;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.admin.provider.jwt.JWTGenerator;
import io.gravitee.am.management.handlers.admin.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.HashSet;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomSavedRequestAwareAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    protected final Logger logger = LoggerFactory.getLogger(CustomSavedRequestAwareAuthenticationSuccessHandler.class);
    private static final String SAVED_REQUEST = "GRAVITEEIO_AM_SAVED_REQUEST";
    private RequestCache requestCache = new HttpSessionRequestCache();

    @Autowired
    private JWTGenerator jwtGenerator;

    @Autowired
    private AuthenticationService authenticationService;

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

        // finish authentication
        io.gravitee.am.model.User endUser = authenticationService.onAuthenticationSuccess(authentication);

        // store jwt authentication cookie to secure management restricted operations
        Cookie jwtAuthenticationCookie = createJWTAuthenticationCookie(authentication, endUser);
        response.addCookie(jwtAuthenticationCookie);

        String targetUrlParameter = getTargetUrlParameter();
        if (isAlwaysUseDefaultTargetUrl() || (targetUrlParameter != null && StringUtils.hasText(request.getParameter(targetUrlParameter)))) {
            requestCache.removeRequest(request, response);
            super.onAuthenticationSuccess(request, response, authentication);

            return;
        }

        clearAuthenticationAttributes(request);

        // Use the DefaultSavedRequest URL
        String targetUrl = savedRequest.getRedirectUrl();
        logger.debug("Redirecting to DefaultSavedRequest Url: " + targetUrl);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private Cookie createJWTAuthenticationCookie(Authentication authentication, io.gravitee.am.model.User endUser) {
        final User principal = (User) authentication.getPrincipal();
        ((DefaultUser) principal).setId(endUser.getId());
        principal.getAdditionalInformation().put(StandardClaims.SUB, endUser.getId());
        principal.getAdditionalInformation().put(Claims.domain, endUser.getReferenceId());

        // set roles
        Set<String> roles = endUser.getRoles() != null ? new HashSet<>(endUser.getRoles()) : new HashSet<>();
        if (principal.getRoles() != null) {
            roles.addAll(principal.getRoles());
        }
        principal.getAdditionalInformation().put(CustomClaims.ROLES, roles);

        return jwtGenerator.generateCookie(principal);
    }
}
