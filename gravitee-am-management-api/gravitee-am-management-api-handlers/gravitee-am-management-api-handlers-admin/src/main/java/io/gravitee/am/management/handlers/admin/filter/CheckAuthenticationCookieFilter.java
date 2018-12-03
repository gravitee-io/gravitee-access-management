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
package io.gravitee.am.management.handlers.admin.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Check if authentication cookie is present, if not and the user session is still active, clear the session
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CheckAuthenticationCookieFilter extends GenericFilterBean {

    @Value("${jwt.cookie-name:Auth-Graviteeio-AM}")
    private String authCookieName;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        // user is not authenticated, continue
        if (!isUserAuthenticated()) {
            chain.doFilter(req, resp);
            return;
        }

        // check if authentication has been deleted
        if (request.getCookies() == null) {
            chain.doFilter(req, resp);
            return;
        }

        // get authentication cookie
        Optional<Cookie> authCookie =  Arrays.stream(request.getCookies())
                    .filter(cookie -> authCookieName.equals(cookie.getName()))
                    .findAny();

        // cookie is still present, continue
        if (authCookie.isPresent()) {
            chain.doFilter(req, resp);
            return;
        }

        // cookie is gone, clear session
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        chain.doFilter(req, resp);
    }

    private boolean isUserAuthenticated() {
        return SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated();
    }
}
