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
package io.gravitee.am.identityprovider.linkedin.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.linkedin.LinkedinIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.linkedin.authentication.model.LinkedinUser;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.observers.TestObserver;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.impl.ClientPhase;
import io.vertx.ext.web.client.impl.WebClientInternal;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LinkedinAuthenticationProviderTest {

    @Spy
    protected WebClient client = WebClient.wrap(Vertx.vertx().createHttpClient());

    @Mock
    protected HttpResponse httpResponse;

    @Mock
    private LinkedinIdentityProviderConfiguration configuration;

    @Mock
    private DefaultIdentityProviderMapper mapper;

    @Mock
    private DefaultIdentityProviderRoleMapper roleMapper;

    @InjectMocks
    private LinkedinAuthenticationProvider cut;


    @Before
    public void init() {
        ((WebClientInternal) client.getDelegate()).addInterceptor(event -> {

            if (event.phase() == ClientPhase.PREPARE_REQUEST) {
                // By pass send request and jump directly to dispatch phase with the mocked http response.
                event.dispatchResponse(httpResponse);
            }

            event.next();
        });
    }

    @Test
    public void shouldGenerateSignInUrl() {

        when(configuration.getUserAuthorizationUri()).thenReturn("https://www.linkedin.com/oauth/v2/authorization");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getResponseType()).thenReturn("code");
        when(configuration.getScopes()).thenReturn(Collections.emptySet());

        final String state = RandomString.generate();
        Request request = cut.signInUrl("https://gravitee.io", state);

        assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("https://www.linkedin.com/oauth/v2/authorization?client_id=testClientId&redirect_uri=https://gravitee.io&response_type=code&state=" + state, request.getUri());
        assertNull(request.getHeaders());
    }

    @Test
    public void shouldGenerateSignInUrl_withScope() {

        when(configuration.getUserAuthorizationUri()).thenReturn("https://www.linkedin.com/oauth/v2/authorization");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getResponseType()).thenReturn("code");
        when(configuration.getScopes()).thenReturn(Collections.emptySet());
        when(configuration.getScopes()).thenReturn(new LinkedHashSet<>(Arrays.asList("scope1", "scope2", "scope3")));

        final String state = RandomString.generate();
        Request request = cut.signInUrl("https://gravitee.io", state);

        assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("https://www.linkedin.com/oauth/v2/authorization?client_id=testClientId&redirect_uri=https://gravitee.io&response_type=code&scope=scope1%20scope2%20scope3&state=" + state, request.getUri());
        assertNull(request.getHeaders());
    }


    @Test
    public void shouldAuthenticate() {

        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("code", "code");

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(configuration.getAccessTokenUri()).thenReturn("https://www.linkedin.com/oauth/v2/accessToken");
        when(configuration.getUserProfileUri()).thenReturn("https://api.linkedin.com/v2/me?projection=(*,profilePicture(displayImage~:playableStreams))");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200)
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject().put("access_token", "an_access_token"))
                .thenReturn(new JsonObject()
                        .put(LinkedinUser.ID, "LINKEDINID")
                        .put(LinkedinUser.FIRSTNAME, "John")
                        .put(LinkedinUser.LASTNAME, "Doe")
                        .put(LinkedinUser.MAIDENNAME, "not applicable")
                        .put(LinkedinUser.PROFILE_URL, "http://profile.linkedin/")
                        .put(LinkedinUser.HEADLINE, "My HEADLINE")
                        .put("profilePicture",
                                new JsonObject()
                                        .put("displayImage~", new JsonObject().
                                                put("elements", new JsonArray().add(new JsonObject()
                                                        .put("identifiers", new JsonArray()
                                                                .add(new JsonObject().put("mediaType", "image/png")
                                                                        .put("identifierType", "EXTERNAL_URL")
                                                                        .put("identifier", "http://picture"))))))));


        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertValue(user -> {
            assertEquals("LINKEDINID", user.getId());
            assertEquals("John", user.getFirstName());
            assertEquals("Doe", user.getLastName());
            assertEquals("LINKEDINID", user.getAdditionalInformation().get(StandardClaims.SUB));
            assertEquals("Doe", user.getAdditionalInformation().get(StandardClaims.FAMILY_NAME));
            assertEquals("John", user.getAdditionalInformation().get(StandardClaims.GIVEN_NAME));
            assertEquals("not applicable", user.getAdditionalInformation().get("MAIDEN_NAME"));
            assertEquals("My HEADLINE", user.getAdditionalInformation().get("HEADLINE"));
            assertEquals("http://profile.linkedin/", user.getAdditionalInformation().get(StandardClaims.PROFILE));
            assertEquals("http://picture", user.getAdditionalInformation().get(StandardClaims.PICTURE));
            return true;
        });

        verify(client, times(1)).postAbs("https://www.linkedin.com/oauth/v2/accessToken");
        verify(client, times(1)).getAbs("https://api.linkedin.com/v2/me?projection=(*,profilePicture(displayImage~:playableStreams))");
    }

    @Test
    public void shouldAuthenticate_badCredentials() {

        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("code", "code");

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(configuration.getAccessTokenUri()).thenReturn("https://www.linkedin.com/oauth/v2/accessToken");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.UNAUTHORIZED_401);


        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertError(BadCredentialsException.class);

        verify(client, times(1)).postAbs("https://www.linkedin.com/oauth/v2/accessToken");
    }

    @Test
    public void shouldAuthenticate_profileBadCredentials() {

        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("code", "code");

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(configuration.getAccessTokenUri()).thenReturn("https://www.linkedin.com/oauth/v2/accessToken");
        when(configuration.getUserProfileUri()).thenReturn("https://api.linkedin.com/v2/me?projection=(*,profilePicture(displayImage~:playableStreams))");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200)
                .thenReturn(HttpStatusCode.UNAUTHORIZED_401);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject().put("access_token", "myaccesstoken"));

        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertError(BadCredentialsException.class);

        verify(client, times(1)).postAbs("https://www.linkedin.com/oauth/v2/accessToken");
        verify(client, times(1)).getAbs("https://api.linkedin.com/v2/me?projection=(*,profilePicture(displayImage~:playableStreams))");
    }
}
