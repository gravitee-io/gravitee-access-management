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

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.handlers.management.api.authentication.BearerTokenAuthenticator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationBearerTokenFilterTest {

    @Mock
    private BearerTokenAuthenticator bearerTokenAuthenticator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private AutomationBearerTokenFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AutomationBearerTokenFilter(bearerTokenAuthenticator);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueChainWithoutAuthWhenNoAuthorizationHeader() throws Exception {
        when(request.getHeader(AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(bearerTokenAuthenticator, never()).authenticate(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldContinueChainWithoutAuthWhenNonBearerHeader() throws Exception {
        when(request.getHeader(AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(bearerTokenAuthenticator, never()).authenticate(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldDelegateToBearerTokenAuthenticatorAndSetContext() throws Exception {
        String token = "any.jwt.token";
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);

        DefaultUser principal = new DefaultUser("u");
        principal.setId("id");
        var delegated = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        when(bearerTokenAuthenticator.authenticate(any(String.class), any(BearerTokenAuthenticator.Context.class)))
                .thenReturn(delegated);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertSame(delegated, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldClearContextAndContinueChainWhenAuthenticatorThrows() throws Exception {
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer anything");
        when(bearerTokenAuthenticator.authenticate(any(String.class), any(BearerTokenAuthenticator.Context.class)))
                .thenThrow(new BadCredentialsException("nope"));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
