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
import java.awt.EventQueue;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static io.gravitee.gateway.api.http.HttpHeaderNames.AUTHORIZATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class BuiltInAuthenticationFilterTest {

    private static final String AUTH_GRAVITEEIO_AM = "Auth-Graviteeio-AM";
    private static final String BEARER_PREFIX = "Bearer ";

    @Mock
    private JWTParser jwtParser;

    @Mock
    private OrganizationUserService organizationUserService;

    @InjectMocks
    private JWTAuthenticationFilter filter = new JWTAuthenticationFilter(new AntPathRequestMatcher("/**"));


    @Before
    public void setup() {
        setField(filter, "jwtCookiePath", "/");
        setField(filter, "authCookieName", AUTH_GRAVITEEIO_AM);
        setField(filter, "jwtCookieSecure", false);
        setField(filter, "jwtCookieDomain", "");
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_bad_credential_exception() {
        filter.attemptAuthentication(mock(HttpServletRequest.class), mock(HttpServletResponse.class));
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_bad_credential_empty_cookie() {
        var request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{mock(Cookie.class)});
        filter.attemptAuthentication(request, mock(HttpServletResponse.class));
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_bad_credential_exception_with_non_matching_authorization() {
        var request = mock(HttpServletRequest.class);
        when(request.getHeader(AUTHORIZATION)).thenReturn("Something not matching");
        filter.attemptAuthentication(request, mock(HttpServletResponse.class));
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_bad_credential_jwt_badly_parse_cookie() {
        var request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{mock(Cookie.class)});
        filter.attemptAuthentication(request, mock(HttpServletResponse.class));
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_bad_credential_jwt_badly_parse_cookie_not_found_cookie() {
        var request = mock(HttpServletRequest.class);
        final Cookie cookieMock = mock(Cookie.class);
        when(cookieMock.getName()).thenReturn("Unknown-Cookie");
        when(request.getCookies()).thenReturn(new Cookie[]{cookieMock});
        filter.attemptAuthentication(request, mock(HttpServletResponse.class));
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_bad_credential_jwt_badly_parse_cookie_not_malformed_cookie_value() {
        var request = mock(HttpServletRequest.class);
        final Cookie cookieMock = mock(Cookie.class);
        when(cookieMock.getName()).thenReturn(AUTH_GRAVITEEIO_AM);
        when(cookieMock.getName()).thenReturn("Malformed Cookie Value");
        when(request.getCookies()).thenReturn(new Cookie[]{cookieMock});
        filter.attemptAuthentication(request, mock(HttpServletResponse.class));
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_bad_credential_jwt_badly_parse_authorization() {
        var request = mock(HttpServletRequest.class);
        final String aWrongBadlyMadeToken = "aWrongBadlyMadeToken";

        when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + aWrongBadlyMadeToken);
        when(jwtParser.parse(eq(aWrongBadlyMadeToken))).thenThrow(new IllegalArgumentException("Wrongly parsed token"));

        final HttpServletResponse response = mock(HttpServletResponse.class);
        doNothing().when(response).addCookie(any());

        filter.attemptAuthentication(request, response);
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_bad_credential_jwt_badly_parse_cookie_not_malformed_cookie_token() {
        var request = mock(HttpServletRequest.class);
        final Cookie cookieMock = mock(Cookie.class);

        final String aWrongBadlyMadeToken = "aWrongBadlyMadeToken";
        when(cookieMock.getName()).thenReturn(AUTH_GRAVITEEIO_AM);
        when(cookieMock.getValue()).thenReturn(BEARER_PREFIX + aWrongBadlyMadeToken);
        when(request.getCookies()).thenReturn(new Cookie[]{cookieMock});

        when(jwtParser.parse(eq(aWrongBadlyMadeToken))).thenThrow(new IllegalArgumentException("Wrongly parsed token"));

        final HttpServletResponse response = mock(HttpServletResponse.class);
        doNothing().when(response).addCookie(any());

        filter.attemptAuthentication(request, response);
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_BadCredentialsException_jwt_org_user_not_present_authorization() {
        var request = mock(HttpServletRequest.class);
        final String anActualGreatToken = "anActualGreatToken";

        when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + anActualGreatToken);
        when(jwtParser.parse(eq(anActualGreatToken))).thenReturn(new JWT());

        final HttpServletResponse response = mock(HttpServletResponse.class);
        doNothing().when(response).addCookie(any());

        filter.attemptAuthentication(request, response);
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_BadCredentialsException_last_logout() {
        var request = mock(HttpServletRequest.class);
        final String anActualGreatToken = "anActualGreatToken";

        when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + anActualGreatToken);
        final JWT jwt = new JWT();
        jwt.setIat(System.currentTimeMillis() / (2L * 1000L));
        jwt.put("org", "10");
        jwt.setSub(UUID.randomUUID().toString());
        when(jwtParser.parse(eq(anActualGreatToken))).thenReturn(jwt);
        final User user = new User();
        user.setId(jwt.getSub());
        user.setLastLogoutAt(new Date());
        when(organizationUserService.findById(eq(ReferenceType.ORGANIZATION), eq("10"), eq(jwt.getSub())))
                .thenReturn(Single.just(user));

        final HttpServletResponse response = mock(HttpServletResponse.class);
        doNothing().when(response).addCookie(any());

        filter.attemptAuthentication(request, response);
    }

    @Test(expected = BadCredentialsException.class)
    public void must_throw_BadCredentialsException_username_reset() {
        var request = mock(HttpServletRequest.class);
        final String anActualGreatToken = "anActualGreatToken";

        when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_PREFIX + anActualGreatToken);
        final JWT jwt = new JWT();
        jwt.setIat(System.currentTimeMillis() / (2L * 1000L));
        jwt.put("org", "10");
        jwt.setSub(UUID.randomUUID().toString());
        when(jwtParser.parse(eq(anActualGreatToken))).thenReturn(jwt);
        final User user = new User();
        user.setId(jwt.getSub());
        user.setLastUsernameReset(new Date());
        when(organizationUserService.findById(eq(ReferenceType.ORGANIZATION), eq("10"), eq(jwt.getSub())))
                .thenReturn(Single.just(user));

        final HttpServletResponse response = mock(HttpServletResponse.class);
        doNothing().when(response).addCookie(any());

        filter.attemptAuthentication(request, response);
    }

    @Test
    public void must_return_UsernamePasswordAuthenticationToken_without_roles() {
        var request = mock(HttpServletRequest.class);
        final String anActualGreatToken = "anActualGreatToken";

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
        final String anActualGreatToken = "anActualGreatToken";

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
