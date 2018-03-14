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
package io.gravitee.am.management.handlers.admin.provider.jwt;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.common.http.HttpHeaders;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.servlet.http.Cookie;
import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JWTCookieGenerator {

    private static final boolean DEFAULT_JWT_COOKIE_SECURE = false;
    private static final String DEFAULT_JWT_COOKIE_PATH = "/";
    private static final String DEFAULT_JWT_COOKIE_DOMAIN = "";
    private static final String DEFAULT_JWT_SECRET = "myJWT4Gr4v1t33_S3cr3t";
    private static final int DEFAULT_JWT_EXPIRE_AFTER = 604800;

    @Autowired
    private Environment environment;

    public Cookie generate(final User user) {
        String compactJws = Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + JWTCookieGenerator.DEFAULT_JWT_EXPIRE_AFTER))
                .setClaims(user.getAdditionalInformation())
                .signWith(SignatureAlgorithm.HS512, environment.getProperty("jwt.secret", DEFAULT_JWT_SECRET))
                .compact();
        return generate("Bearer " + compactJws);
    }

    private Cookie generate(final String value) {
        final Cookie cookie = new Cookie(HttpHeaders.AUTHORIZATION, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(environment.getProperty("jwt.cookie-secure", Boolean.class, DEFAULT_JWT_COOKIE_SECURE));
        cookie.setPath(environment.getProperty("jwt.cookie-path", DEFAULT_JWT_COOKIE_PATH));
        cookie.setDomain(environment.getProperty("jwt.cookie-domain", DEFAULT_JWT_COOKIE_DOMAIN));
        cookie.setMaxAge(environment.getProperty("jwt.expire-after", Integer.class, DEFAULT_JWT_EXPIRE_AFTER));
        return cookie;
    }
}
