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

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.handlers.management.api.authentication.BearerTokenAuthenticator;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.List;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class BuiltInAuthenticationFilterTest {

    private static final String AUTH_GRAVITEEIO_AM = "Auth-Graviteeio-AM";
    private static final String BEARER_PREFIX = "Bearer ";

    @Mock
    private BearerTokenAuthenticator bearerTokenAuthenticator;

    @InjectMocks
    private BearerAuthenticationFilter filter = new BearerAuthenticationFilter(new AntPathRequestMatcher("/**"));


    @BeforeEach
    public void setup() {
        setField(filter, "jwtCookiePath", "/");
        setField(filter, "authCookieName", AUTH_GRAVITEEIO_AM);
        setField(filter, "jwtCookieSecure", false);
        setField(filter, "jwtCookieDomain", "");
    }

    @Test
    public void must_throw_bad_credential_exception() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            filter.attemptAuthentication(mock(HttpServletRequest.class), mock(HttpServletResponse.class));
        });
    }

    @Test
    public void must_throw_bad_credential_empty_cookie() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            when(request.getCookies()).thenReturn(new Cookie[]{mock(Cookie.class)});
            filter.attemptAuthentication(request, mock(HttpServletResponse.class));
        });
    }

    @Test
    public void must_throw_bad_credential_exception_with_non_matching_authorization() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            when(request.getHeader(AUTHORIZATION)).thenReturn("Something not matching");
            filter.attemptAuthentication(request, mock(HttpServletResponse.class));
        });
    }

    @Test
    public void must_throw_bad_credential_jwt_badly_parse_cookie() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            when(request.getCookies()).thenReturn(new Cookie[]{mock(Cookie.class)});
            filter.attemptAuthentication(request, mock(HttpServletResponse.class));
        });
    }

    @Test
    public void must_throw_bad_credential_jwt_badly_parse_cookie_not_found_cookie() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            final Cookie cookieMock = mock(Cookie.class);
            when(cookieMock.getName()).thenReturn("Unknown-Cookie");
            when(request.getCookies()).thenReturn(new Cookie[]{cookieMock});
            filter.attemptAuthentication(request, mock(HttpServletResponse.class));
        });
    }

    @Test
    public void must_throw_bad_credential_jwt_badly_parse_cookie_not_malformed_cookie_value() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            final Cookie cookieMock = mock(Cookie.class);
            when(cookieMock.getName()).thenReturn(AUTH_GRAVITEEIO_AM);
            when(cookieMock.getName()).thenReturn("Malformed Cookie Value");
            when(request.getCookies()).thenReturn(new Cookie[]{cookieMock});
            filter.attemptAuthentication(request, mock(HttpServletResponse.class));
        });
    }

    @Test
    public void must_throw_bad_credential_jwt_badly_parse_cookie_not_malformed_cookie_token() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            final Cookie cookieMock = mock(Cookie.class);

            final String aWrongBadlyMadeToken = "header.body.signature";
            when(cookieMock.getName()).thenReturn(AUTH_GRAVITEEIO_AM);
            when(cookieMock.getValue()).thenReturn(BEARER_PREFIX + aWrongBadlyMadeToken);
            when(request.getCookies()).thenReturn(new Cookie[]{cookieMock});

            when(bearerTokenAuthenticator.authenticate(any(String.class), any(BearerTokenAuthenticator.Context.class)))
                    .thenThrow(new IllegalArgumentException("Wrongly parsed token"));

            final HttpServletResponse response = mock(HttpServletResponse.class);
            doNothing().when(response).addCookie(any());

            filter.attemptAuthentication(request, response);
        });
    }

    @Test
    public void must_throw_BadCredentialsException_when_authenticator_throws_for_authorization_header() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + "anActualGreatToken");
            when(bearerTokenAuthenticator.authenticate(any(String.class), any(BearerTokenAuthenticator.Context.class)))
                    .thenThrow(new RuntimeException("user not found"));

            final HttpServletResponse response = mock(HttpServletResponse.class);
            doNothing().when(response).addCookie(any());

            filter.attemptAuthentication(request, response);
        });
    }

    @Test
    public void must_return_authenticator_result_when_delegation_succeeds_without_roles() {
        var request = mock(HttpServletRequest.class);
        when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + "header.body.signature");

        DefaultUser principal = new DefaultUser("u");
        principal.setId("id");
        var delegated = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        when(bearerTokenAuthenticator.authenticate(any(String.class), any(BearerTokenAuthenticator.Context.class)))
                .thenReturn(delegated);

        final Authentication authentication = filter.attemptAuthentication(request, mock(HttpServletResponse.class));
        assertNotNull(authentication);
        assertEquals(0, authentication.getAuthorities().size());
    }

    @Test
    public void must_return_authenticator_result_when_delegation_succeeds_with_roles() {
        var request = mock(HttpServletRequest.class);
        when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + "header.body.signature");

        DefaultUser principal = new DefaultUser("u");
        principal.setId("id");
        var delegated = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER")));
        when(bearerTokenAuthenticator.authenticate(any(String.class), any(BearerTokenAuthenticator.Context.class)))
                .thenReturn(delegated);

        final Authentication authentication = filter.attemptAuthentication(request, mock(HttpServletResponse.class));
        assertNotNull(authentication);
        assertEquals(2, authentication.getAuthorities().size());
    }

}
