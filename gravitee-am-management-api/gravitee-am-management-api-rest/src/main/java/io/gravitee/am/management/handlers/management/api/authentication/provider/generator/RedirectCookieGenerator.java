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
package io.gravitee.am.management.handlers.management.api.authentication.provider.generator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import jakarta.servlet.http.Cookie;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RedirectCookieGenerator {

    public static final boolean DEFAULT_REDIRECT_COOKIE_SECURE = false;
    public static final String DEFAULT_REDIRECT_COOKIE_NAME = "Redirect-Graviteeio-AM";
    public static final String DEFAULT_REDIRECT_COOKIE_PATH = "/";
    public static final String DEFAULT_REDIRECT_COOKIE_DOMAIN = "";
    public static final int DEFAULT_REDIRECT_COOKIE_EXPIRES= 604800;
    public static final String DEFAULT_REDIRECT_URL = "/auth/authorize?redirect_uri=http://localhost:4200/login/callback";

    @Autowired
    private Environment environment;

    public Cookie generateCookie(String redirectUrl) {

        // To keep configuration simple, we reuse same cookie config as for JWT.
        Cookie cookie = new Cookie(DEFAULT_REDIRECT_COOKIE_NAME, redirectUrl);
        cookie.setSecure(environment.getProperty("jwt.cookie-secure", Boolean.class, DEFAULT_REDIRECT_COOKIE_SECURE));
        cookie.setPath(environment.getProperty("jwt.cookie-path", DEFAULT_REDIRECT_COOKIE_PATH));
        cookie.setDomain(environment.getProperty("jwt.cookie-domain", DEFAULT_REDIRECT_COOKIE_DOMAIN));
        cookie.setMaxAge(environment.getProperty("jwt.expire-after", Integer.class, DEFAULT_REDIRECT_COOKIE_EXPIRES));
        cookie.setHttpOnly(true);

        return cookie;
    }

    public Cookie getClearCookie() {

        // To keep configuration simple, we reuse same cookie config as for JWT.
        Cookie cookie = new Cookie(DEFAULT_REDIRECT_COOKIE_NAME, null);
        cookie.setSecure(environment.getProperty("jwt.cookie-secure", Boolean.class, DEFAULT_REDIRECT_COOKIE_SECURE));
        cookie.setPath(environment.getProperty("jwt.cookie-path", DEFAULT_REDIRECT_COOKIE_PATH));
        cookie.setDomain(environment.getProperty("jwt.cookie-domain", DEFAULT_REDIRECT_COOKIE_DOMAIN));
        cookie.setMaxAge(0);

        return cookie;
    }
}
