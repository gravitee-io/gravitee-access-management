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
package io.gravitee.am.management.handlers.management.api.authentication.csrf;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.JWTGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at gravitesource.com)
 * @author GraviteeSource Team
 */
public class CookieCsrfSignedTokenRepository implements CsrfTokenRepository {

    private final Logger LOGGER = LoggerFactory.getLogger(CookieCsrfSignedTokenRepository.class);

    public static final String TOKEN_CLAIM = "token";

    private static final String DEFAULT_CSRF_COOKIE_NAME = "XSRF-Graviteeio-AM-API-TOKEN";

    private static final String DEFAULT_CSRF_PARAMETER_NAME = "_csrf";

    public static final String DEFAULT_CSRF_HEADER_NAME = "X-Xsrf-Token";

    @Autowired
    private JWTGenerator jwtGenerator;

    @Autowired
    @Qualifier("managementJwtBuilder")
    private JWTBuilder jwtBuilder;

    @Autowired
    @Qualifier("managementJwtParser")
    private JWTParser jwtParser;

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {

        CsrfToken csrfToken = loadToken(request);
        if (csrfToken != null) {
            return csrfToken;
        }

        UUID token = UUID.randomUUID();
        return new DefaultCsrfToken(DEFAULT_CSRF_HEADER_NAME, DEFAULT_CSRF_PARAMETER_NAME, token.toString());
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request,
                          HttpServletResponse response) {

        if(request.getAttribute(DEFAULT_CSRF_COOKIE_NAME) != null) {
            // Token already persisted in cookie.
            return;
        }

        if(token == null) {
            // Null token means delete it.
            response.addCookie(jwtGenerator.generateCookie(DEFAULT_CSRF_COOKIE_NAME, null, true));
            return;
        }

        String tokenValue = token.getToken();

        try {
            JWT jwt = new JWT();
            jwt.setJti(SecureRandomString.generate());
            jwt.setIat(Instant.now().getEpochSecond());
            jwt.put(TOKEN_CLAIM, tokenValue);
            String encodedToken = jwtBuilder.sign(jwt);

            Cookie cookie = jwtGenerator.generateCookie(DEFAULT_CSRF_COOKIE_NAME, encodedToken, true);
            response.addCookie(cookie);
            request.setAttribute(DEFAULT_CSRF_COOKIE_NAME, true);
        } catch (Exception ex) {
            LOGGER.error("Unable to generate CSRF token", ex);
        }
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {

        Cookie cookie = WebUtils.getCookie(request, DEFAULT_CSRF_COOKIE_NAME);
        if (cookie == null) {
            return null;
        }
        String cookieValue = cookie.getValue();
        if (!StringUtils.hasLength(cookieValue)) {
            return null;
        }

        try {
            JWT jwt = jwtParser.parse(cookieValue);
            String token = jwt.get(TOKEN_CLAIM).toString();
            if (!StringUtils.hasLength(token)) {
                return null;
            }
            return new DefaultCsrfToken(DEFAULT_CSRF_HEADER_NAME, DEFAULT_CSRF_PARAMETER_NAME, token);
        } catch (Exception ex) {
            LOGGER.error("Unable to verify CSRF token", ex);
        }
        return null;
    }
}
