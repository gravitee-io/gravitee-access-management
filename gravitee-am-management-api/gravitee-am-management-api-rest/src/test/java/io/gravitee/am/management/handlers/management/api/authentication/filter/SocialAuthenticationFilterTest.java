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

import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.rxjava3.core.Maybe;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class SocialAuthenticationFilterTest {

    private static final String PROVIDER_ID = "social-idp";

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private AuthenticationProvider authenticationProvider;

    @Mock
    private AuthenticationService authenticationService;

    private SocialAuthenticationFilter filter;

    private final DefaultUser user = new DefaultUser("john");

    @BeforeEach
    void setUp() {
        filter = new SocialAuthenticationFilter("/auth/login/callback");
        ReflectionTestUtils.setField(filter, "identityProviderManager", identityProviderManager);
        ReflectionTestUtils.setField(filter, "authenticationService", authenticationService);

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setReferenceType(ReferenceType.ORGANIZATION);
        identityProvider.setReferenceId(Organization.DEFAULT);
        when(identityProviderManager.getIdentityProvider(PROVIDER_ID)).thenReturn(identityProvider);
        when(identityProviderManager.get(PROVIDER_ID)).thenReturn(authenticationProvider);
        when(authenticationProvider.loadUserByUsername(any(io.gravitee.am.identityprovider.api.Authentication.class))).thenReturn(Maybe.just(user));
    }

    @Test
    void shouldAuthenticate_enabledUser() {
        Authentication authentication = filter.attemptAuthentication(request(), new MockHttpServletResponse());

        assertSame(user, authentication.getPrincipal());
    }

    @Test
    void shouldReject_disabledUser() {
        doThrow(new DisabledException("User is disabled")).when(authenticationService).checkUserEnabled(eq(user), eq(Organization.DEFAULT), eq(PROVIDER_ID), any(), any());

        var exception = assertThrows(BadCredentialsException.class, () -> filter.attemptAuthentication(request(), new MockHttpServletResponse()));

        assertInstanceOf(DisabledException.class, exception.getCause());
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/login/callback");
        request.setParameter("provider", PROVIDER_ID);
        return request;
    }
}
