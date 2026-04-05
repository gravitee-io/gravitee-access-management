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
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.common.http.HttpHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;
import static java.util.stream.Collectors.toList;

/**
 * Lightweight Bearer token filter for the Automation API.
 * <p>
 * Extends {@link OncePerRequestFilter} instead of {@code AbstractAuthenticationProcessingFilter}
 * to avoid the complex request-matching, session management, and mutable state issues of the
 * latter. Simply extracts a JWT Bearer token from the Authorization header, validates it,
 * and sets the {@link SecurityContextHolder} context. If no token is present, the request
 * proceeds unauthenticated and Spring Security's authorization layer handles the 401.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
public class AutomationBearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${http.blockingGet.timeoutMillis:120000}")
    private long blockingGetTimeoutMillis;

    private final JWTParser jwtParser;
    private final OrganizationUserService userService;

    public AutomationBearerTokenFilter(
            @Qualifier("managementJwtParser") JWTParser jwtParser,
            OrganizationUserService userService
    ) {
        this.jwtParser = jwtParser;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);
        if (token != null) {
            try {
                var authentication = authenticate(token, request);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Clear any partial auth state — let the chain continue unauthenticated.
                // The ExceptionTranslationFilter + AuthenticationEntryPoint will handle the 401.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private UsernamePasswordAuthenticationToken authenticate(String authToken, HttpServletRequest request) {
        JWT jwt = jwtParser.parse(authToken);

        Map<String, Object> claims = new HashMap<>(jwt);
        claims.put(Claims.IP_ADDRESS, remoteAddress(request));
        claims.put(Claims.USER_AGENT, request.getHeader(HttpHeaders.USER_AGENT));

        var orgUser = userService.findById(
                        ReferenceType.ORGANIZATION,
                        (String) jwt.get("org"),
                        (String) claims.get(StandardClaims.SUB))
                .blockingGet();

        var dates = List.of(
                Optional.ofNullable(orgUser.getLastLogoutAt()),
                Optional.ofNullable(orgUser.getLastUsernameReset())
        );

        boolean isSessionExpired = dates.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(time -> time.after(new Date(jwt.getIat() * 1000)));

        if (isSessionExpired) {
            throw new RuntimeException("Session expired");
        }

        DefaultUser user = new DefaultUser((String) claims.get(StandardClaims.PREFERRED_USERNAME));
        user.setId((String) claims.get(StandardClaims.SUB));
        user.setAdditionalInformation(claims);
        user.setRoles((List<String>) claims.get(CustomClaims.ROLES));
        user.setGroups((List<String>) claims.get(CustomClaims.GROUPS));

        return new UsernamePasswordAuthenticationToken(user, null, getAuthorities(user));
    }

    private List<GrantedAuthority> getAuthorities(DefaultUser user) {
        if (user.getRoles() == null) {
            return AuthorityUtils.NO_AUTHORITIES;
        }
        return user.getRoles().stream().map(SimpleGrantedAuthority::new).collect(toList());
    }

    private String remoteAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader(HttpHeaders.X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            int idx = xForwardedFor.indexOf(',');
            String remoteAddress = (idx != -1) ? xForwardedFor.substring(0, idx) : xForwardedFor;
            idx = remoteAddress.indexOf(':');
            return (idx != -1) ? remoteAddress.substring(0, idx).trim() : remoteAddress.trim();
        }
        return request.getRemoteAddr();
    }
}
