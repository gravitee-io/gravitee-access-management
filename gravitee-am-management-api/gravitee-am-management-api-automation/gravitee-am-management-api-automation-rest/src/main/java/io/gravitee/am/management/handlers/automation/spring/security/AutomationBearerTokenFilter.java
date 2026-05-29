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
package io.gravitee.am.management.handlers.automation.spring.security;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;
import static java.util.stream.Collectors.toList;

/**
 * Lightweight Bearer token filter for the Automation API.
 * <p>
 * Extends {@link OncePerRequestFilter} instead of {@code AbstractAuthenticationProcessingFilter}
 * to avoid the complex request-matching, session management, and mutable state issues of the
 * management REST {@link io.gravitee.am.management.handlers.management.api.authentication.filter.BearerAuthenticationFilter}.
 * <p>
 * Authentication is limited to JWT Bearer tokens in the {@code Authorization} header only.
 * Cookie-based JWT and account access tokens (supported by the management REST filter) are
 * intentionally out of scope for this machine-oriented API surface.
 * <p>
 * When no Bearer token is present, or when token validation fails (invalid JWT, expired session,
 * or repository lookup timeout), the security context is left unauthenticated and the request
 * continues; Spring Security's authorization layer ({@code .authenticated()}) returns 401 via
 * the configured {@link io.gravitee.am.management.handlers.management.api.authentication.web.Http401UnauthorizedEntryPoint}.
 * <p>
 * Repository lookups honour {@code http.blockingGet.timeoutMillis}; set to {@code 0} to
 * disable the timeout.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Slf4j
public class AutomationBearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JWTParser jwtParser;
    private final OrganizationUserService userService;
    private final long blockingGetTimeoutMillis;

    public AutomationBearerTokenFilter(
            JWTParser jwtParser,
            OrganizationUserService userService,
            long blockingGetTimeoutMillis
    ) {
        this.jwtParser = jwtParser;
        this.userService = userService;
        this.blockingGetTimeoutMillis = blockingGetTimeoutMillis;
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
                SecurityContextHolder.clearContext();
                log.debug("Automation API bearer authentication failed: {}", e.getMessage(), e);
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
                .compose(this::applyTimeout)
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
            throw new SessionAuthenticationException("Session expired");
        }

        DefaultUser user = new DefaultUser((String) claims.get(StandardClaims.PREFERRED_USERNAME));
        user.setId((String) claims.get(StandardClaims.SUB));
        user.setAdditionalInformation(claims);
        user.setRoles((List<String>) claims.get(CustomClaims.ROLES));
        user.setGroups((List<String>) claims.get(CustomClaims.GROUPS));

        return new UsernamePasswordAuthenticationToken(user, null, getAuthorities(user));
    }

    private Single<User> applyTimeout(Single<User> src) {
        if (blockingGetTimeoutMillis > 0) {
            return src.timeout(blockingGetTimeoutMillis, TimeUnit.MILLISECONDS);
        }
        return src;
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
