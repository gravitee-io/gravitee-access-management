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

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import javax.servlet.http.Cookie;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JWTGenerator implements InitializingBean {

    private static final boolean DEFAULT_JWT_COOKIE_SECURE = false;
    private static final String DEFAULT_JWT_COOKIE_PATH = "/";
    private static final String DEFAULT_JWT_COOKIE_DOMAIN = "";
    private static final int DEFAULT_JWT_EXPIRE_AFTER = 604800;
    private Key key;

    @Value("${jwt.secret:s3cR3t4grAv1t3310AMS1g1ingDftK3y}")
    private String signingKeySecret;

    @Value("${jwt.cookie-name:Auth-Graviteeio-AM}")
    private String authCookieName;

    @Autowired
    private Environment environment;

    public Cookie generateCookie(final User user) {
        int expiresAfter = environment.getProperty("jwt.expire-after", Integer.class, DEFAULT_JWT_EXPIRE_AFTER);
        Date expirationDate = new Date(System.currentTimeMillis() + expiresAfter * 1000);
        String jwtToken  = generateToken(user, expirationDate);

        final Cookie cookie = new Cookie(authCookieName, "Bearer " + jwtToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(environment.getProperty("jwt.cookie-secure", Boolean.class, DEFAULT_JWT_COOKIE_SECURE));
        cookie.setPath(environment.getProperty("jwt.cookie-path", DEFAULT_JWT_COOKIE_PATH));
        cookie.setDomain(environment.getProperty("jwt.cookie-domain", DEFAULT_JWT_COOKIE_DOMAIN));
        cookie.setMaxAge(expiresAfter);

        return cookie;
    }

    public Map<String, Object> generateToken(final User user) {
        int expiresAfter = environment.getProperty("jwt.expire-after", Integer.class, DEFAULT_JWT_EXPIRE_AFTER);
        Date expirationDate = new Date(System.currentTimeMillis() + expiresAfter * 1000);
        String jwtToken  = generateToken(user, expirationDate);

        Map<String, Object> token = new HashMap<>();
        token.put("access_token", jwtToken);
        token.put("token_type", "bearer");
        token.put("expires_at", expirationDate.toString());

        return token;
    }

    private String generateToken(final User user, Date expirationDate) {
        String compactJws = Jwts.builder()
                .setSubject(user.getId())
                .setIssuedAt(new Date())
                .setExpiration(expirationDate)
                .claim(StandardClaims.PREFERRED_USERNAME, user.getUsername())
                .addClaims(user.getAdditionalInformation())
                .signWith(key)
                .compact();

        return compactJws;
    }

    @Override
    public void afterPropertiesSet() {
        // init JWT signing key
        key = Keys.hmacShaKeyFor(signingKeySecret.getBytes());
    }
}
