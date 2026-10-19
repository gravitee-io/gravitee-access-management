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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import io.gravitee.am.model.Organization;
import io.gravitee.node.api.configuration.Configuration;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class CockpitAuthenticationFilterTest {

    private static final String TOKEN = "cockpit-token";

    @InjectMocks
    private CockpitAuthenticationFilter filter;

    @Mock
    private Configuration configuration;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private JWTParser jwtParser;

    @Test
    void shouldReject_disabledUser() throws Exception {
        ReflectionTestUtils.setField(filter, "jwtParser", jwtParser);
        when(configuration.getProperty("cockpit.enabled", Boolean.class)).thenReturn(true);
        JWT jwt = new JWT();
        jwt.setSub("cockpit-user-id");
        jwt.put(StandardClaims.PREFERRED_USERNAME, "john");
        jwt.put(Claims.ORGANIZATION, Organization.DEFAULT);
        when(jwtParser.parse(TOKEN)).thenReturn(jwt);
        doThrow(new DisabledException("User is disabled")).when(authenticationService)
                .checkUserEnabled(argThat(user -> "cockpit-user-id".equals(user.getId())), eq(Organization.DEFAULT), eq("cockpit"), any(), any());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/cockpit");
        request.setPathInfo("/cockpit");
        request.setParameter("token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertEquals(403, response.getStatus());
        verify(authenticationService, never()).onAuthenticationSuccess(any());
    }
}
