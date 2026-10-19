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
package io.gravitee.am.management.handlers.management.api.authentication.filter;

import io.gravitee.am.management.handlers.management.api.authentication.BearerTokenAuthenticator;
import io.gravitee.am.management.handlers.management.api.authentication.web.Http401UnauthorizedEntryPoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;

/**
 * Bearer authentication filter for the management REST API. Accepts JWT bearer tokens (header or
 * cookie) and opaque user service-account access tokens ({@code Base64(tokenId.value)}).
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BearerAuthenticationFilter extends AbstractAuthenticationProcessingFilter implements InitializingBean {

    protected static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.cookie-path:/}")
    private String jwtCookiePath;

    @Value("${jwt.cookie-name:Auth-Graviteeio-AM}")
    private String authCookieName;

    @Value("${jwt.cookie-secure:false}")
    private boolean jwtCookieSecure;

    @Value("${jwt.cookie-domain:}")
    private String jwtCookieDomain;

    @Autowired
    private Http401UnauthorizedEntryPoint http401UnauthorizedEntryPoint;

    @Autowired
    private BearerTokenAuthenticator bearerTokenAuthenticator;

    public BearerAuthenticationFilter(RequestMatcher requiresAuthenticationRequestMatcher) {
        super(requiresAuthenticationRequestMatcher);
        setAuthenticationManager(new NoopAuthenticationManager());
        setAuthenticationSuccessHandler(new JWTAuthenticationSuccessHandler());
        setAuthenticationFailureHandler(new JWTAuthenticationFailureHandler());
        super.setAllowSessionCreation(false);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        final String token = extractToken(request);
        try {
            return getAuthentication(token, request);
        } catch (Exception ex) {
            clearAuthenticationCookie(response);
            throw new BadCredentialsException("Error occured while attempting authentication", ex);
        }
    }

    private String extractToken(HttpServletRequest request) {
        final String authorization = request.getHeader(AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        } else {
            // if no authorization header found, check authorization cookie
            return Optional.ofNullable(request.getCookies())
                    .stream()
                    .flatMap(Arrays::stream)
                    .filter(cookie -> authCookieName.equals(cookie.getName()))
                    .filter(cookie -> cookie.getValue().startsWith(BEARER_PREFIX))
                    .findAny()
                    .map(authCookie -> authCookie.getValue().substring(BEARER_PREFIX.length()))
                    .orElseThrow(() -> new BadCredentialsException("No Bearer token found"));
        }
    }

    protected Authentication getAuthentication(String authToken, HttpServletRequest request) {
        return bearerTokenAuthenticator.authenticate(authToken, BearerTokenAuthenticator.Context.from(request));
    }

    private void clearAuthenticationCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(authCookieName, null);
        cookie.setSecure(jwtCookieSecure);
        cookie.setPath(jwtCookiePath);
        cookie.setDomain(jwtCookieDomain);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);

        // As this is a REST authentication, after success we need to continue the request normally
        // and return the response as if the resource was not secured at all
        chain.doFilter(request, response);
    }

    private static class NoopAuthenticationManager implements AuthenticationManager {

        @Override
        public Authentication authenticate(Authentication authentication)
                throws AuthenticationException {
            // We only use JWT library to authenticate the user, authentication manager is not required
            throw new UnsupportedOperationException("No authentication should be done with this AuthenticationManager");
        }

    }

    private class JWTAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
            // We do not need to do anything extra on REST authentication success, because there is no page to redirect to
        }
    }

    private class JWTAuthenticationFailureHandler implements AuthenticationFailureHandler {


        public JWTAuthenticationFailureHandler() {
            super();
            setAllowSessionCreation(false);
        }

        @Override
        public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
            http401UnauthorizedEntryPoint.commence(request, response, exception);
        }
    }
}
