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

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Single;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationBearerTokenFilterTest {

    private static final String ORG_ID = "DEFAULT";
    private static final String USER_ID = "user-123";
    private static final String USERNAME = "admin";

    @Mock
    private JWTParser jwtParser;

    @Mock
    private OrganizationUserService userService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private AutomationBearerTokenFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AutomationBearerTokenFilter(jwtParser, userService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateWithValidBearerToken() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);

        JWT jwt = new JWT();
        jwt.put("org", ORG_ID);
        jwt.put(StandardClaims.SUB, USER_ID);
        jwt.put(StandardClaims.PREFERRED_USERNAME, USERNAME);
        jwt.setIat(System.currentTimeMillis() / 1000);
        when(jwtParser.parse(token)).thenReturn(jwt);

        User orgUser = new User();
        orgUser.setId(USER_ID);
        orgUser.setUsername(USERNAME);
        when(userService.findById(eq(ReferenceType.ORGANIZATION), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(Single.just(orgUser));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(USERNAME, ((io.gravitee.am.identityprovider.api.User) auth.getPrincipal()).getUsername());
    }

    @Test
    void shouldContinueChainWithoutAuthWhenNoAuthorizationHeader() throws Exception {
        when(request.getHeader(AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldContinueChainWithoutAuthWhenNonBearerHeader() throws Exception {
        when(request.getHeader(AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldClearContextOnInvalidToken() throws Exception {
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer invalid.token");
        when(jwtParser.parse("invalid.token")).thenThrow(new RuntimeException("Invalid JWT"));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldClearContextOnExpiredSession() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);

        JWT jwt = new JWT();
        jwt.put("org", ORG_ID);
        jwt.put(StandardClaims.SUB, USER_ID);
        jwt.put(StandardClaims.PREFERRED_USERNAME, USERNAME);
        jwt.setIat(1000L); // very old token
        when(jwtParser.parse(token)).thenReturn(jwt);

        User orgUser = new User();
        orgUser.setId(USER_ID);
        orgUser.setUsername(USERNAME);
        orgUser.setLastLogoutAt(new Date()); // logged out after token issued
        when(userService.findById(eq(ReferenceType.ORGANIZATION), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(Single.just(orgUser));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAuthenticateRegardlessOfHttpMethod() throws Exception {
        // OncePerRequestFilter processes ALL methods — no method-based filtering.
        // This is the key difference from AbstractAuthenticationProcessingFilter.
        assertAuthenticatesSuccessfully();
    }

    private void assertAuthenticatesSuccessfully() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);

        JWT jwt = new JWT();
        jwt.put("org", ORG_ID);
        jwt.put(StandardClaims.SUB, USER_ID);
        jwt.put(StandardClaims.PREFERRED_USERNAME, USERNAME);
        jwt.setIat(System.currentTimeMillis() / 1000);
        when(jwtParser.parse(token)).thenReturn(jwt);

        User orgUser = new User();
        orgUser.setId(USER_ID);
        orgUser.setUsername(USERNAME);
        when(userService.findById(eq(ReferenceType.ORGANIZATION), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(Single.just(orgUser));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
