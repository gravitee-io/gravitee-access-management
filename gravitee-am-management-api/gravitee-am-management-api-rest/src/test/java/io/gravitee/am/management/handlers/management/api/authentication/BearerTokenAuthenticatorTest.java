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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BearerTokenAuthenticatorTest {

    private static final long BLOCKING_GET_TIMEOUT_MILLIS = 120_000L;
    private static final String ORG_ID = "DEFAULT";
    private static final String USER_ID = "user-123";
    private static final String USERNAME = "admin";
    private static final String IP = "10.0.0.1";
    private static final String UA = "JUnit/1.0";

    @Mock
    private JWTParser jwtParser;

    @Mock
    private OrganizationUserService userService;

    @Mock
    private AccountAccessTokenAuthenticator accountAccessTokenAuthenticator;

    private BearerTokenAuthenticator authenticator(long timeout) {
        return new BearerTokenAuthenticator(jwtParser, userService, accountAccessTokenAuthenticator, timeout);
    }

    private BearerTokenAuthenticator authenticator() {
        return authenticator(BLOCKING_GET_TIMEOUT_MILLIS);
    }

    private static BearerTokenAuthenticator.Context ctx() {
        return new BearerTokenAuthenticator.Context(IP, UA);
    }

    private static JWT baseJwt() {
        JWT jwt = new JWT();
        jwt.put("org", ORG_ID);
        jwt.put(StandardClaims.SUB, USER_ID);
        jwt.put(StandardClaims.PREFERRED_USERNAME, USERNAME);
        jwt.setIat(System.currentTimeMillis() / 1000);
        return jwt;
    }

    @Test
    void jwt_validToken_returnsPrincipalWithClaimsRolesAndGroups() {
        String bearer = "valid.jwt.token";
        JWT jwt = baseJwt();
        jwt.put(CustomClaims.ROLES, List.of("ORGANIZATION_OWNER"));
        jwt.put(CustomClaims.GROUPS, List.of("g1", "g2"));
        when(jwtParser.parse(bearer)).thenReturn(jwt);
        User orgUser = new User();
        orgUser.setId(USER_ID);
        orgUser.setUsername(USERNAME);
        when(userService.findById(eq(ReferenceType.ORGANIZATION), eq(ORG_ID), eq(USER_ID))).thenReturn(Single.just(orgUser));

        var auth = authenticator().authenticate(bearer, ctx());

        assertNotNull(auth);
        DefaultUser principal = (DefaultUser) auth.getPrincipal();
        assertEquals(USERNAME, principal.getUsername());
        assertEquals(USER_ID, principal.getId());
        assertEquals(List.of("ORGANIZATION_OWNER"), principal.getRoles());
        assertEquals(List.of("g1", "g2"), principal.getGroups());
        // IP + UA must be enriched into the claims map
        assertEquals(IP, principal.getAdditionalInformation().get(Claims.IP_ADDRESS));
        assertEquals(UA, principal.getAdditionalInformation().get(Claims.USER_AGENT));
        // credentials are intentionally null for JWT (see BearerAuthenticationFilter heritage)
        assertEquals(null, auth.getCredentials());
        assertEquals(1, auth.getAuthorities().size());
    }

    @Test
    void jwt_sessionExpiredViaLastLogout_throwsSessionAuthenticationException() {
        String bearer = "valid.jwt.token";
        JWT jwt = baseJwt();
        jwt.setIat(1000L); // ancient token
        when(jwtParser.parse(bearer)).thenReturn(jwt);
        User orgUser = new User();
        orgUser.setId(USER_ID);
        orgUser.setUsername(USERNAME);
        orgUser.setLastLogoutAt(new Date()); // logged out after IAT
        when(userService.findById(eq(ReferenceType.ORGANIZATION), eq(ORG_ID), eq(USER_ID))).thenReturn(Single.just(orgUser));

        assertThrows(SessionAuthenticationException.class, () -> authenticator().authenticate(bearer, ctx()));
    }

    @Test
    void jwt_sessionExpiredViaLastUsernameReset_throwsSessionAuthenticationException() {
        String bearer = "valid.jwt.token";
        JWT jwt = baseJwt();
        jwt.setIat(1000L);
        when(jwtParser.parse(bearer)).thenReturn(jwt);
        User orgUser = new User();
        orgUser.setId(USER_ID);
        orgUser.setUsername(USERNAME);
        orgUser.setLastUsernameReset(new Date());
        when(userService.findById(eq(ReferenceType.ORGANIZATION), eq(ORG_ID), eq(USER_ID))).thenReturn(Single.just(orgUser));

        assertThrows(SessionAuthenticationException.class, () -> authenticator().authenticate(bearer, ctx()));
    }

    @Test
    void jwt_lookupTimesOut_propagatesException() {
        String bearer = "valid.jwt.token";
        when(jwtParser.parse(bearer)).thenReturn(baseJwt());
        when(userService.findById(eq(ReferenceType.ORGANIZATION), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(Single.<User>never().subscribeOn(Schedulers.io()));

        assertThrows(RuntimeException.class, () -> authenticator(50L).authenticate(bearer, ctx()));
    }

    @Test
    void jwt_parseFailure_propagatesException() {
        when(jwtParser.parse("bad.jwt.token")).thenThrow(new RuntimeException("bad signature"));

        assertThrows(RuntimeException.class, () -> authenticator().authenticate("bad.jwt.token", ctx()));
    }

    @Test
    void opaque_delegatesToAccountAccessTokenAuthenticator() {
        // Base64("ABC.DEF") — no '.' in encoded form, so the discriminator routes opaque.
        String opaque = Base64.getEncoder().encodeToString("ABC.DEF".getBytes(StandardCharsets.UTF_8));
        DefaultUser principal = new DefaultUser("svc");
        principal.setId("svc-id");
        var delegated = new UsernamePasswordAuthenticationToken(principal, "ABC", List.of());
        when(accountAccessTokenAuthenticator.authenticate(opaque)).thenReturn(delegated);

        var auth = authenticator().authenticate(opaque, ctx());

        assertSame(delegated, auth);
        // JWT path is not touched.
        verify(jwtParser, never()).parse(opaque);
        verify(userService, never()).findById(eq(ReferenceType.ORGANIZATION), eq(ORG_ID), eq(USER_ID));
    }

    @Test
    void context_fromRequestWithXForwardedFor_extractsIpAndUserAgent() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(HttpHeaders.X_FORWARDED_FOR)).thenReturn("203.0.113.10:51234, 198.51.100.7");
        when(req.getHeader(HttpHeaders.USER_AGENT)).thenReturn("curl/8");

        var ctx = BearerTokenAuthenticator.Context.from(req);

        // First entry in XFF, port stripped.
        assertEquals("203.0.113.10", ctx.ipAddress());
        assertEquals("curl/8", ctx.userAgent());
    }

    @Test
    void context_fromRequestWithoutXForwardedFor_fallsBackToRemoteAddr() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(HttpHeaders.X_FORWARDED_FOR)).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getHeader(HttpHeaders.USER_AGENT)).thenReturn(null);

        var ctx = BearerTokenAuthenticator.Context.from(req);

        assertEquals("127.0.0.1", ctx.ipAddress());
        assertEquals(null, ctx.userAgent());
    }
}
