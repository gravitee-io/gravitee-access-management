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

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Single;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private JWTParser jwtParser;

    @Mock
    private OrganizationUserService organizationUserService;

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
    public void must_throw_bad_credential_jwt_badly_parse_authorization() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            final String aWrongBadlyMadeToken = "aWrongBadlyMadeToken";

            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + aWrongBadlyMadeToken);

            final HttpServletResponse response = mock(HttpServletResponse.class);
            doNothing().when(response).addCookie(any());

            filter.attemptAuthentication(request, response);
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

            when(jwtParser.parse(eq(aWrongBadlyMadeToken))).thenThrow(new IllegalArgumentException("Wrongly parsed token"));

            final HttpServletResponse response = mock(HttpServletResponse.class);
            doNothing().when(response).addCookie(any());

            filter.attemptAuthentication(request, response);
        });
    }

    @Test
    public void must_throw_BadCredentialsException_jwt_org_user_not_present_authorization() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            final String anActualGreatToken = "anActualGreatToken";

            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + anActualGreatToken);

            final HttpServletResponse response = mock(HttpServletResponse.class);
            doNothing().when(response).addCookie(any());

            filter.attemptAuthentication(request, response);
        });
    }

    @Test
    public void must_throw_BadCredentialsException_last_logout() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            final String anActualGreatToken = "anActualGreatToken";

            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + anActualGreatToken);
            final JWT jwt = new JWT();
            jwt.setIat(System.currentTimeMillis() / (2L * 1000L));
            jwt.put("org", "10");
            jwt.setSub(UUID.randomUUID().toString());
            final User user = new User();
            user.setId(jwt.getSub());
            user.setLastLogoutAt(new Date());

            final HttpServletResponse response = mock(HttpServletResponse.class);
            doNothing().when(response).addCookie(any());

            filter.attemptAuthentication(request, response);
        });
    }

    @Test
    public void must_throw_BadCredentialsException_username_reset() {
        Assertions.assertThrows(BadCredentialsException.class, () -> {
            var request = mock(HttpServletRequest.class);
            final String anActualGreatToken = "anActualGreatToken";

            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + anActualGreatToken);
            final JWT jwt = new JWT();
            jwt.setIat(System.currentTimeMillis() / (2L * 1000L));
            jwt.put("org", "10");
            jwt.setSub(UUID.randomUUID().toString());
            final User user = new User();
            user.setId(jwt.getSub());
            user.setLastUsernameReset(new Date());

            final HttpServletResponse response = mock(HttpServletResponse.class);
            doNothing().when(response).addCookie(any());

            filter.attemptAuthentication(request, response);
        });
    }

    @Test
    public void must_return_UsernamePasswordAuthenticationToken_without_roles() {
        var request = mock(HttpServletRequest.class);
        final String anActualGreatToken = "header.body.signature";

        when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + anActualGreatToken);
        final JWT jwt = new JWT();
        jwt.setIat(System.currentTimeMillis());
        jwt.put("org", "10");
        jwt.setSub(UUID.randomUUID().toString());
        when(jwtParser.parse(eq(anActualGreatToken))).thenReturn(jwt);
        final User user = new User();
        user.setId(jwt.getSub());
        when(organizationUserService.findById(eq(ReferenceType.ORGANIZATION), eq("10"), eq(jwt.getSub())))
                .thenReturn(Single.just(user));

        final HttpServletResponse response = mock(HttpServletResponse.class);

        final Authentication authentication = filter.attemptAuthentication(request, response);
        assertNotNull(authentication);
        assertEquals(0, authentication.getAuthorities().size());
    }

    @Test
    public void must_return_UsernamePasswordAuthenticationToken_with_roles() {
        var request = mock(HttpServletRequest.class);
        final String anActualGreatToken = "header.body.signature";

        when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + anActualGreatToken);
        final JWT jwt = new JWT();
        jwt.setIat(System.currentTimeMillis());
        jwt.put("org", "10");
        jwt.setSub(UUID.randomUUID().toString());
        jwt.put(CustomClaims.ROLES, List.of("ROLE_ADMIN", "ROLE_USER"));
        when(jwtParser.parse(eq(anActualGreatToken))).thenReturn(jwt);
        final User user = new User();
        user.setId(jwt.getSub());
        when(organizationUserService.findById(eq(ReferenceType.ORGANIZATION), eq("10"), eq(jwt.getSub())))
                .thenReturn(Single.just(user));
        final HttpServletResponse response = mock(HttpServletResponse.class);

        final Authentication authentication = filter.attemptAuthentication(request, response);
        assertNotNull(authentication);
        assertEquals(2, authentication.getAuthorities().size());
    }

}
