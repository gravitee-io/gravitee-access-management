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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CookieClearingLogoutHandler implements LogoutHandler {

    private static final boolean DEFAULT_JWT_COOKIE_SECURE = false;
    private static final String DEFAULT_JWT_COOKIE_NAME = "Auth-Graviteeio-AM";
    private static final String DEFAULT_JWT_COOKIE_PATH = "/";
    private static final String DEFAULT_JWT_COOKIE_DOMAIN = "";

    @Autowired
    private Environment environment;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        Cookie cookie = new Cookie(environment.getProperty("jwt.cookie-name", DEFAULT_JWT_COOKIE_NAME), null);
        cookie.setSecure(environment.getProperty("jwt.cookie-secure", Boolean.class, DEFAULT_JWT_COOKIE_SECURE));
        cookie.setPath(environment.getProperty("jwt.cookie-path", DEFAULT_JWT_COOKIE_PATH));
        cookie.setDomain(environment.getProperty("jwt.cookie-domain", DEFAULT_JWT_COOKIE_DOMAIN));
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
