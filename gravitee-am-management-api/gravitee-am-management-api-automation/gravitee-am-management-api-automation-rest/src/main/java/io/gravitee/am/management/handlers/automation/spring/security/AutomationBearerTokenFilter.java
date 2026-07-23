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

import io.gravitee.am.management.handlers.management.api.authentication.BearerTokenAuthenticator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;

/**
 * Lightweight Bearer token filter for the Automation API.
 * <p>
 * Extends {@link OncePerRequestFilter} instead of {@code AbstractAuthenticationProcessingFilter}
 * to avoid the complex request-matching, session management, and mutable state issues of the
 * management REST {@link io.gravitee.am.management.handlers.management.api.authentication.filter.BearerAuthenticationFilter}.
 * <p>
 * Accepts either JWT bearer tokens or opaque user service-account access tokens
 * ({@code Base64(tokenId + "." + value)}) in the {@code Authorization} header. Cookie-based JWT
 * is intentionally out of scope for this machine-oriented API surface.
 * <p>
 * When no Bearer token is present, or when authentication fails, the security context is left
 * unauthenticated and the request continues; Spring Security's authorization layer
 * ({@code .authenticated()}) returns 401 via the configured
 * {@link io.gravitee.am.management.handlers.management.api.authentication.web.Http401UnauthorizedEntryPoint}.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@CustomLog
public class AutomationBearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final BearerTokenAuthenticator bearerTokenAuthenticator;

    public AutomationBearerTokenFilter(BearerTokenAuthenticator bearerTokenAuthenticator) {
        this.bearerTokenAuthenticator = bearerTokenAuthenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);
        if (token != null) {
            try {
                var authentication = bearerTokenAuthenticator.authenticate(token, BearerTokenAuthenticator.Context.from(request));
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
}
