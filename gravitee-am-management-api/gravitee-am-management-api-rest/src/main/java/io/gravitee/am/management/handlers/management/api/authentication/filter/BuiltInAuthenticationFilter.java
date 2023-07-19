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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * The built-in authentication filter rely on the {@link JWTAuthenticationFilter} to avoid code duplication and is exclusively used for the management user login phase.
 * This filter tries to extract the JWT Token and, if succeed, feeds the {@link SecurityContextHolder}.
 * Unlike {@link JWTAuthenticationFilter} which is made for REST calls and can throw {@link org.springframework.security.authentication.BadCredentialsException},
 * this filter ignores authentication errors and always processes the filter chain normally.
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BuiltInAuthenticationFilter extends JWTAuthenticationFilter {

    public BuiltInAuthenticationFilter(RequestMatcher requiresAuthenticationRequestMatcher) {
        super(requiresAuthenticationRequestMatcher);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        try {
            Authentication authentication = super.attemptAuthentication((HttpServletRequest) request, (HttpServletResponse) response);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            // Do nothing, really.
        }

        chain.doFilter(request, response);
    }
}