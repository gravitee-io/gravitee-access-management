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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.handlers.management.api.authentication.web.Http401UnauthorizedEntryPoint;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JWTAuthenticationFilter extends AbstractAuthenticationProcessingFilter implements InitializingBean {

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.cookie-path:/}")
    private String jwtCookiePath;

    @Value("${jwt.cookie-name:Auth-Graviteeio-AM}")
    private String authCookieName;

    @Value("${jwt.cookie-secure:false}")
    private boolean jwtCookieSecure;

    @Value("${jwt.cookie-domain:}")
    private String jwtCookieDomain;

    @Autowired
    @Qualifier("managementJwtParser")
    private JWTParser jwtParser;

    @Autowired
    private OrganizationUserService userService;

    @Autowired
    private Http401UnauthorizedEntryPoint http401UnauthorizedEntryPoint;

    public JWTAuthenticationFilter(RequestMatcher requiresAuthenticationRequestMatcher) {
        super(requiresAuthenticationRequestMatcher);
        setAuthenticationManager(new NoopAuthenticationManager());
        setAuthenticationSuccessHandler(new JWTAuthenticationSuccessHandler());
        setAuthenticationFailureHandler(new JWTAuthenticationFailureHandler());
        super.setAllowSessionCreation(false);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        String authToken;

        // first check Authorization request header
        final String authorization = request.getHeader(AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            authToken = authorization.substring(BEARER_PREFIX.length());
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

            if (optionalStringToken.isEmpty() || !optionalStringToken.get().getValue().startsWith(BEARER_PREFIX)) {
                throw new BadCredentialsException("No JWT token found");
            }
            authToken = optionalStringToken.get().getValue().substring(7);
        }

        try {
            JWT payload = jwtParser.parse(authToken);
            Map<String, Object> claims = new HashMap<>(payload);
            claims.put(Claims.ip_address, remoteAddress(request));
            claims.put(Claims.user_agent, userAgent(request));
            User orgUser = userService.findById(ReferenceType.ORGANIZATION, (String) payload.get("org"), (String) claims.get(StandardClaims.SUB)).blockingGet();

            var dates = List.of(
                    // We check the last logout of the user
                    ofNullable(orgUser.getLastLogoutAt()),
                    // We check if the username has been reset
                    ofNullable(orgUser.getLastUsernameReset())
            );

            var isSessionException = dates.stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .anyMatch(time -> time.after(new Date(payload.getIat() * 1000)));

            if (isSessionException) {
                throw new SessionAuthenticationException("Session expired");
            }

            DefaultUser user = new DefaultUser((String) claims.get(StandardClaims.PREFERRED_USERNAME));
            user.setId((String) claims.get(StandardClaims.SUB));
            user.setAdditionalInformation(claims);
            user.setRoles((List<String>) claims.get(CustomClaims.ROLES));
            // check for roles
            List<GrantedAuthority> authorities;
            if (user.getRoles() != null) {
                authorities = user.getRoles().stream().map(SimpleGrantedAuthority::new).collect(toList());
            } else {
                authorities = AuthorityUtils.NO_AUTHORITIES;
            }
            return new UsernamePasswordAuthenticationToken(user, null, authorities);
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

    private String remoteAddress(HttpServletRequest httpServerRequest) {
        String xForwardedFor = httpServerRequest.getHeader(HttpHeaders.X_FORWARDED_FOR);
        String remoteAddress;

        if (xForwardedFor != null && xForwardedFor.length() > 0) {
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
