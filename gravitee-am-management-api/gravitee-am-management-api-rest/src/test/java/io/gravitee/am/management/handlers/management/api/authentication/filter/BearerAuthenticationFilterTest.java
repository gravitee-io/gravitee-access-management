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
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class BearerAuthenticationFilterTest {

    private static final String ORG = "DEFAULT";
    private static final String USER_ID = "user-id";
    private static final String JWT_VALUE = "header.payload.signature";
    private static final String ACCOUNT_TOKEN = Base64.getEncoder().encodeToString("token-id.token-value".getBytes(StandardCharsets.UTF_8));

    @Mock
    private JWTParser jwtParser;

    @Mock
    private OrganizationUserService userService;

    private BearerAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BearerAuthenticationFilter(AnyRequestMatcher.INSTANCE);
        ReflectionTestUtils.setField(filter, "jwtParser", jwtParser);
        ReflectionTestUtils.setField(filter, "userService", userService);
        ReflectionTestUtils.setField(filter, "authCookieName", "Auth-Graviteeio-AM");
    }

    @Test
    void shouldAuthenticate_jwt_enabledUser() {
        givenJwtIssuedAt(new Date());
        when(userService.findById(ReferenceType.ORGANIZATION, ORG, USER_ID)).thenReturn(Single.just(orgUser(true)));

        Authentication authentication = filter.attemptAuthentication(request(JWT_VALUE), new MockHttpServletResponse());

        assertEquals(USER_ID, ((DefaultUser) authentication.getPrincipal()).getId());
    }

    @Test
    void shouldReject_jwt_disabledUser() {
        givenJwtIssuedAt(new Date());
        when(userService.findById(ReferenceType.ORGANIZATION, ORG, USER_ID)).thenReturn(Single.just(orgUser(false)));

        var exception = assertThrows(BadCredentialsException.class, () -> filter.attemptAuthentication(request(JWT_VALUE), new MockHttpServletResponse()));

        assertInstanceOf(DisabledException.class, exception.getCause());
    }

    @Test
    void shouldAuthenticate_accountToken_enabledUser() {
        when(userService.findByAccessToken("token-id", "token-value")).thenReturn(Single.just(orgUser(true)));

        Authentication authentication = filter.attemptAuthentication(request(ACCOUNT_TOKEN), new MockHttpServletResponse());

        assertEquals(USER_ID, ((DefaultUser) authentication.getPrincipal()).getId());
    }

    @Test
    void shouldReject_accountToken_disabledUser() {
        when(userService.findByAccessToken("token-id", "token-value")).thenReturn(Single.just(orgUser(false)));

        var exception = assertThrows(BadCredentialsException.class, () -> filter.attemptAuthentication(request(ACCOUNT_TOKEN), new MockHttpServletResponse()));

        assertInstanceOf(DisabledException.class, exception.getCause());
    }

    private void givenJwtIssuedAt(Date issuedAt) {
        JWT jwt = new JWT();
        jwt.setSub(USER_ID);
        jwt.put("org", ORG);
        jwt.setIat(issuedAt.getTime() / 1000);
        when(jwtParser.parse(JWT_VALUE)).thenReturn(jwt);
    }

    private static User orgUser(boolean enabled) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("john");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);
        user.setEnabled(enabled);
        return user;
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
