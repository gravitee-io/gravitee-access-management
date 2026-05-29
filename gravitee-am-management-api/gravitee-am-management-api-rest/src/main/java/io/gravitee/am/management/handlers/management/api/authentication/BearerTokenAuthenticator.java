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
package io.gravitee.am.management.handlers.management.api.authentication;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.core.Single;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;

/**
 * Authenticates a bearer token, routing between JWT and opaque user service-account access tokens.
 * 
 * @author GraviteeSource Team
 */
public class BearerTokenAuthenticator {

    /**
     * Per-request context carried into the authenticator from a filter. Servlet-agnostic apart
     * from the {@link #from(HttpServletRequest)} factory, which centralises X-Forwarded-For
     * parsing so the rule lives in one place.
     */
    public record Context(String ipAddress, String userAgent) {

        public static Context from(HttpServletRequest request) {
            return new Context(extractRemoteAddress(request), request.getHeader(HttpHeaders.USER_AGENT));
        }

        private static String extractRemoteAddress(HttpServletRequest request) {
            String xForwardedFor = request.getHeader(HttpHeaders.X_FORWARDED_FOR);
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                int idx = xForwardedFor.indexOf(',');
                String addr = (idx != -1) ? xForwardedFor.substring(0, idx) : xForwardedFor;
                idx = addr.indexOf(':');
                return (idx != -1) ? addr.substring(0, idx).trim() : addr.trim();
            }
            return request.getRemoteAddr();
        }
    }

    private final JWTParser jwtParser;
    private final OrganizationUserService userService;
    private final AccountAccessTokenAuthenticator accountAccessTokenAuthenticator;
    private final long blockingGetTimeoutMillis;

    public BearerTokenAuthenticator(
            JWTParser jwtParser,
            OrganizationUserService userService,
            AccountAccessTokenAuthenticator accountAccessTokenAuthenticator,
            long blockingGetTimeoutMillis
    ) {
        this.jwtParser = jwtParser;
        this.userService = userService;
        this.accountAccessTokenAuthenticator = accountAccessTokenAuthenticator;
        this.blockingGetTimeoutMillis = blockingGetTimeoutMillis;
    }

    /**
     * Route a bearer value and produce an {@link UsernamePasswordAuthenticationToken}. JWT-shaped
     * inputs go through the in-house JWT flow; opaque inputs delegate to
     * {@link AccountAccessTokenAuthenticator}.
     */
    public UsernamePasswordAuthenticationToken authenticate(String bearer, Context ctx) {
        if (AccountAccessToken.hasJwtShape(bearer)) {
            return jwtAuthenticate(bearer, ctx);
        }
        return accountAccessTokenAuthenticator.authenticate(bearer);
    }

    @SuppressWarnings("unchecked")
    private UsernamePasswordAuthenticationToken jwtAuthenticate(String bearer, Context ctx) {
        JWT jwt = jwtParser.parse(bearer);

        Map<String, Object> claims = new HashMap<>(jwt);
        claims.put(Claims.IP_ADDRESS, ctx.ipAddress());
        claims.put(Claims.USER_AGENT, ctx.userAgent());

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

        return new UsernamePasswordAuthenticationToken(user, null, authoritiesFromRoles(user.getRoles()));
    }

    private Single<User> applyTimeout(Single<User> src) {
        return blockingGetTimeoutMillis > 0
                ? src.timeout(blockingGetTimeoutMillis, TimeUnit.MILLISECONDS)
                : src;
    }

    /**
     * Null-safe map of role names to Spring Security authorities.
     */
    static List<GrantedAuthority> authoritiesFromRoles(List<String> roles) {
        if (roles == null) {
            return AuthorityUtils.NO_AUTHORITIES;
        }
        return roles.stream().map(SimpleGrantedAuthority::new).collect(toList());
    }
}
