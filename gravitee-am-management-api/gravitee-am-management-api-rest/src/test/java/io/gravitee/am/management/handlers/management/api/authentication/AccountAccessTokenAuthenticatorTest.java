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

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAccessTokenAuthenticatorTest {

    private static final long BLOCKING_GET_TIMEOUT_MILLIS = 120_000L;
    private static final String USER_ID = "user-123";
    private static final String USERNAME = "svc-account";
    private static final String TOKEN_ID = "tok-1";
    private static final String TOKEN_VALUE = "raw-secret-value";

    @Mock
    private OrganizationUserService userService;

    private static String encode(String tokenId, String tokenValue) {
        return Base64.getEncoder().encodeToString((tokenId + "." + tokenValue).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldAuthenticateValidTokenAndProduceExpectedPrincipal() {
        var authenticator = new AccountAccessTokenAuthenticator(userService, BLOCKING_GET_TIMEOUT_MILLIS);
        User orgUser = new User();
        orgUser.setId(USER_ID);
        orgUser.setUsername(USERNAME);
        orgUser.setRoles(List.of("ORGANIZATION_OWNER"));
        when(userService.findByAccessToken(eq(TOKEN_ID), eq(TOKEN_VALUE))).thenReturn(Single.just(orgUser));

        var auth = authenticator.authenticate(encode(TOKEN_ID, TOKEN_VALUE));

        assertNotNull(auth);
        DefaultUser principal = (DefaultUser) auth.getPrincipal();
        assertEquals(USERNAME, principal.getUsername());
        assertEquals(USER_ID, principal.getId());
        assertEquals(TOKEN_ID, principal.getAdditionalInformation().get("accountTokenId"));
        // credentials = token id (not the value, which is the secret)
        assertEquals(TOKEN_ID, auth.getCredentials());
        assertEquals(1, auth.getAuthorities().size());
        assertEquals("ORGANIZATION_OWNER", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void shouldGrantNoAuthoritiesWhenRolesAreNull() {
        var authenticator = new AccountAccessTokenAuthenticator(userService, BLOCKING_GET_TIMEOUT_MILLIS);
        User orgUser = new User();
        orgUser.setId(USER_ID);
        orgUser.setUsername(USERNAME);
        // roles intentionally null
        when(userService.findByAccessToken(eq(TOKEN_ID), eq(TOKEN_VALUE))).thenReturn(Single.just(orgUser));

        var auth = authenticator.authenticate(encode(TOKEN_ID, TOKEN_VALUE));

        assertEquals(0, auth.getAuthorities().size());
    }

    @Test
    void shouldThrowBadCredentialsWhenInputIsNotValidBase64() {
        var authenticator = new AccountAccessTokenAuthenticator(userService, BLOCKING_GET_TIMEOUT_MILLIS);

        assertThrows(BadCredentialsException.class,
                () -> authenticator.authenticate("not-base64-and-no-dot!!!"));

        // We never reach the lookup.
        verify(userService, never()).findByAccessToken(eq(TOKEN_ID), eq(TOKEN_VALUE));
    }

    @Test
    void shouldThrowBadCredentialsWhenDecodedPayloadIsNotTwoParts() {
        var authenticator = new AccountAccessTokenAuthenticator(userService, BLOCKING_GET_TIMEOUT_MILLIS);
        // single part: Base64("singlepart") decodes cleanly but split on "\\." yields one element
        String singlePart = Base64.getEncoder().encodeToString("singlepart".getBytes(StandardCharsets.UTF_8));

        assertThrows(BadCredentialsException.class, () -> authenticator.authenticate(singlePart));
    }

    @Test
    void shouldPropagateLookupExceptionWhenTokenNotFound() {
        var authenticator = new AccountAccessTokenAuthenticator(userService, BLOCKING_GET_TIMEOUT_MILLIS);
        when(userService.findByAccessToken(eq(TOKEN_ID), eq(TOKEN_VALUE)))
                .thenReturn(Single.error(new RuntimeException("token not found")));

        assertThrows(RuntimeException.class,
                () -> authenticator.authenticate(encode(TOKEN_ID, TOKEN_VALUE)));
    }

    @Test
    void shouldRespectBlockingGetTimeout() {
        // 50 ms timeout against a Single that never completes — must throw.
        var authenticator = new AccountAccessTokenAuthenticator(userService, 50L);
        when(userService.findByAccessToken(eq(TOKEN_ID), eq(TOKEN_VALUE)))
                .thenReturn(Single.<User>never().subscribeOn(Schedulers.io()));

        assertThrows(RuntimeException.class,
                () -> authenticator.authenticate(encode(TOKEN_ID, TOKEN_VALUE)));
    }

    @Test
    void encodedTokenFromToCreateResponseShouldNotHaveJwtShape() {
        // Round-trip guard: the wire format produced by toCreateResponse must never be
        // misidentified as a JWT by the discriminator that filters use to route tokens.
        var account = AccountAccessToken.builder().tokenId(TOKEN_ID).build();
        var encoded = account.toCreateResponse(TOKEN_VALUE).token();

        assertFalse(AccountAccessToken.hasJwtShape(encoded),
                "Base64-encoded account tokens must never contain '.'");
        // And decode must round-trip the parts.
        var decoded = AccountAccessToken.decode(encoded);
        assertEquals(TOKEN_ID, decoded.tokenId());
        assertEquals(TOKEN_VALUE, decoded.tokenValue());
    }
}
