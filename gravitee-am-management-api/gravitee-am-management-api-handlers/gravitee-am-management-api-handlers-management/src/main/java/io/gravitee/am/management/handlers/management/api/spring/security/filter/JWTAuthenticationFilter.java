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
package io.gravitee.am.management.handlers.management.api.spring.security.filter;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.common.http.HttpHeaders;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Key;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class JWTAuthenticationFilter extends AbstractAuthenticationProcessingFilter implements InitializingBean {

    @Value("${jwt.secret:s3cR3t4grAv1t3310AMS1g1ingDftK3y}")
    private String jwtSecret;
    @Value("${jwt.cookie-path:/}")
    private String jwtCookiePath;
    @Value("${jwt.cookie-name:Auth-Graviteeio-AM}")
    private String authCookieName;
    @Value("${jwt.cookie-secure:false}")
    private boolean jwtCookieSecure;
    @Value("${jwt.cookie-domain:}")
    private String jwtCookieDomain;
    private Key key;

    public JWTAuthenticationFilter(RequestMatcher requiresAuthenticationRequestMatcher) {
        super(requiresAuthenticationRequestMatcher);
        setAuthenticationManager(new NoopAuthenticationManager());
        setAuthenticationSuccessHandler(new JWTAuthenticationSuccessHandler());
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        String authToken;

        // first check Authorization request header
        final String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authToken = authorization.substring(7);
        } else {
            // if no authorization header found, check authorization cookie
            final Optional<Cookie> optionalStringToken;

            if (request.getCookies() == null) {
                optionalStringToken = Optional.empty();
            } else {
                optionalStringToken = Arrays.stream(request.getCookies())
                        .filter(cookie -> authCookieName.equals(cookie.getName()))
                        .findAny();
            }

            if (!optionalStringToken.isPresent() || !optionalStringToken.get().getValue().startsWith("Bearer ")) {
                throw new BadCredentialsException("No JWT token found");
            }
            authToken = optionalStringToken.get().getValue().substring(7);
        }

        try {
            JwtParser jwtParser = Jwts.parser().setSigningKey(key);
            Map<String, Object> claims = new HashMap<>(jwtParser.parseClaimsJws(authToken).getBody());
            claims.put(Claims.ip_address, remoteAddress(request));
            claims.put(Claims.user_agent, userAgent(request));
            DefaultUser user = new DefaultUser((String) claims.get(StandardClaims.PREFERRED_USERNAME));
            user.setId((String) claims.get(StandardClaims.SUB));
            user.setAdditionalInformation(claims);
            return new UsernamePasswordAuthenticationToken(user, null, AuthorityUtils.NO_AUTHORITIES);
        } catch (Exception ex) {
            removeJWTAuthenticationCookie(response);
            throw new BadCredentialsException("Error occurs while attempting authentication", ex);
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);

        // As this is a REST authentication, after success we need to continue the request normally
        // and return the response as if the resource was not secured at all
        chain.doFilter(request, response);
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();

        // init JWT signing key
        key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    private void removeJWTAuthenticationCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(authCookieName, null);
        cookie.setSecure(jwtCookieSecure);
        cookie.setPath(jwtCookiePath);
        cookie.setDomain(jwtCookieDomain);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private static class NoopAuthenticationManager implements AuthenticationManager {

        @Override
        public Authentication authenticate(Authentication authentication)
                throws AuthenticationException {
            // We only use JWT library to authenticate the user, authentication manager is not required
            throw new UnsupportedOperationException("No authentication should be done with this AuthenticationManager");
        }

    }

    private static class JWTAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
            // We do not need to do anything extra on REST authentication success, because there is no page to redirect to
        }

    }

    private String remoteAddress(HttpServletRequest httpServerRequest) {
        String xForwardedFor = httpServerRequest.getHeader(HttpHeaders.X_FORWARDED_FOR);
        String remoteAddress;

        if(xForwardedFor != null && xForwardedFor.length() > 0) {
            int idx = xForwardedFor.indexOf(',');

            remoteAddress = (idx != -1) ? xForwardedFor.substring(0, idx) : xForwardedFor;

            idx = remoteAddress.indexOf(':');

            remoteAddress = (idx != -1) ? remoteAddress.substring(0, idx).trim() : remoteAddress.trim();
        } else {
            remoteAddress = httpServerRequest.getRemoteAddr();
        }

        return remoteAddress;
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
