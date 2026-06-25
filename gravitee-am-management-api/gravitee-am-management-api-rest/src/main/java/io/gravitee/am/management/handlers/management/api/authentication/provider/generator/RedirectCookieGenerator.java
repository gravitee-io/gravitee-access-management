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

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

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
    public static final String DEFAULT_UI_URL = "http://localhost:4200";

    @Autowired
    private Environment environment;

    /**
     * Returns the sanitized Console UI base URL (from the {@code console.ui.url} property).
     * Falls back to {@link #DEFAULT_UI_URL} when the property is unset or blank.
     */
    public String getConsoleUiUrl() {
        String uiUrl = environment.getProperty("console.ui.url", DEFAULT_UI_URL);
        if (uiUrl == null || uiUrl.isEmpty()) {
            return DEFAULT_UI_URL;
        }
        return uiUrl.endsWith("/") ? uiUrl.substring(0, uiUrl.length() - 1) : uiUrl;
    }

    public String getDefaultRedirectUrl() {
        return "/auth/authorize?redirect_uri=" + getConsoleUiUrl() + "/login/callback";
    }

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
