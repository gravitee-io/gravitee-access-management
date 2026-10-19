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
<<<<<<< HEAD

=======
>>>>>>> ebe62ad (fix(management): end the access of disabled organization users (#8918))
package io.gravitee.am.management.handlers.management.api.authentication.filter;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
<<<<<<< HEAD
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.JWTGenerator;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import io.gravitee.am.model.Environment;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.node.api.configuration.Configuration;
import io.reactivex.rxjava3.core.Single;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
=======
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
>>>>>>> ebe62ad (fix(management): end the access of disabled organization users (#8918))
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
<<<<<<< HEAD
@MockitoSettings(strictness = Strictness.LENIENT)
public class CockpitAuthenticationFilterTest {

    private static final String ORGANIZATION_ID = "cockpit-org";
    private static final String ENVIRONMENT_ID = "cockpit-env";

    @Mock
    private Configuration configuration;
    @Mock
    private JWTGenerator jwtGenerator;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private EnvironmentService environmentService;
    @Mock
    private JWTParser jwtParser;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private CockpitAuthenticationFilter cut;

    @BeforeEach
    public void before() {
        cut = new CockpitAuthenticationFilter();
        ReflectionTestUtils.setField(cut, "configuration", configuration);
        ReflectionTestUtils.setField(cut, "jwtGenerator", jwtGenerator);
        ReflectionTestUtils.setField(cut, "authenticationService", authenticationService);
        ReflectionTestUtils.setField(cut, "environmentService", environmentService);
        // pre-set so initialize() does not try to load the cockpit keystore
        ReflectionTestUtils.setField(cut, "jwtParser", jwtParser);

        when(configuration.getProperty("cockpit.enabled", Boolean.class)).thenReturn(true);
        when(request.getPathInfo()).thenReturn("/cockpit");
        when(request.getParameter("token")).thenReturn("a-cockpit-token");
        when(jwtParser.parse(anyString())).thenReturn(new JWT(Map.of(
                Claims.ORGANIZATION, ORGANIZATION_ID,
                Claims.ENVIRONMENT, ENVIRONMENT_ID,
                "sub", "cockpit-user-id")));
    }

    @Test
    public void shouldNotAuthenticateWhenTheEnvironmentDoesNotExist() throws Exception {
        // the organization or environment named in the token has not been provisioned (yet)
        when(environmentService.findById(ENVIRONMENT_ID, ORGANIZATION_ID))
                .thenReturn(Single.error(new EnvironmentNotFoundException(ENVIRONMENT_ID)));

        cut.doFilter(request, response, filterChain);

        // onAuthenticationSuccess creates the organization user when its lookup misses, so running it
        // before the environment is resolved would leave an orphan referencing a missing organization
        verify(authenticationService, never()).onAuthenticationSuccess(any());
        verify(jwtGenerator, never()).generateCookie(any(io.gravitee.am.identityprovider.api.User.class));
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void shouldAuthenticateWhenTheEnvironmentExists() throws Exception {
        Environment environment = new Environment();
        environment.setId(ENVIRONMENT_ID);
        environment.setHrids(List.of(ENVIRONMENT_ID));
        when(environmentService.findById(ENVIRONMENT_ID, ORGANIZATION_ID)).thenReturn(Single.just(environment));
        when(authenticationService.onAuthenticationSuccess(any())).thenReturn(new io.gravitee.am.identityprovider.api.DefaultUser("cockpit-user"));
        when(jwtGenerator.generateCookie(any(io.gravitee.am.identityprovider.api.User.class)))
                .thenReturn(new jakarta.servlet.http.Cookie("Auth-Graviteeio-AM", "Bearer token"));

        cut.doFilter(request, response, filterChain);

        verify(authenticationService).onAuthenticationSuccess(any());
        verify(response).sendRedirect(eq("/environments/" + ENVIRONMENT_ID));
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
=======
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
>>>>>>> ebe62ad (fix(management): end the access of disabled organization users (#8918))
    }
}
