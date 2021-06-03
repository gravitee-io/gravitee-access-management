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
package io.gravitee.am.identityprovider.facebook.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.facebook.FacebookIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.facebook.model.FacebookUser;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.observers.TestObserver;
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
import java.util.HashMap;
import java.util.HashSet;

import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class FacebookAuthenticationProviderTest {

    @Spy
    protected WebClient client = WebClient.wrap(Vertx.vertx().createHttpClient());

    @Mock
    protected HttpResponse httpResponse;

    @Mock
    private FacebookIdentityProviderConfiguration configuration;

    @Mock
    private DefaultIdentityProviderMapper mapper;

    @Mock
    private DefaultIdentityProviderRoleMapper roleMapper;

    @InjectMocks
    private FacebookAuthenticationProvider cut;


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

        when(configuration.getUserAuthorizationUri()).thenReturn("https://www.facebook.com/v7.0/dialog/oauth");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getResponseType()).thenReturn("code");
        when(configuration.getScopes()).thenReturn(Collections.emptySet());

        final String state = RandomString.generate();
        Request request = cut.signInUrl("https://gravitee.io", state);

        assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("https://www.facebook.com/v7.0/dialog/oauth?client_id=testClientId&redirect_uri=https://gravitee.io&response_type=code&state=" + state, request.getUri());
        assertNull(request.getHeaders());
    }

    @Test
    public void shouldGenerateAsyncSignInUrl() {

        when(configuration.getUserAuthorizationUri()).thenReturn("https://www.facebook.com/v7.0/dialog/oauth");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getResponseType()).thenReturn("code");
        when(configuration.getScopes()).thenReturn(Collections.emptySet());

        final String state = RandomString.generate();
        Request request = (Request)cut.asyncSignInUrl("https://gravitee.io", state).blockingGet();

        assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("https://www.facebook.com/v7.0/dialog/oauth?client_id=testClientId&redirect_uri=https://gravitee.io&response_type=code&state=" + state, request.getUri());
        assertNull(request.getHeaders());
    }

    @Test
    public void shouldGenerateSignInUrl_withScopes() {

        when(configuration.getUserAuthorizationUri()).thenReturn("https://www.facebook.com/v7.0/dialog/oauth");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getResponseType()).thenReturn("code");
        when(configuration.getScopes()).thenReturn(new HashSet<>(Arrays.asList("scope1", "scope2", "scope3")));

        final String state = RandomString.generate();
        Request request = cut.signInUrl("https://gravitee.io", state);

        assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("https://www.facebook.com/v7.0/dialog/oauth?client_id=testClientId&redirect_uri=https://gravitee.io&response_type=code&scope=scope1%20scope2%20scope3&state=" + state, request.getUri());
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
        parameters.set("code", "facebookAuthorizationCode");

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(configuration.getAccessTokenUri()).thenReturn("https://graph.facebook.com/v7.0/oauth/access_token");
        when(configuration.getUserProfileUri()).thenReturn("https://graph.facebook.com/v7.0/me");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200)
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject().put("access_token", "facebookGeneratedAccessToken"))
                .thenReturn(new JsonObject().put(FacebookUser.ID, "facebookID")
                        .put(FacebookUser.EMAIL, "email@facebook.com")
                        .put(FacebookUser.NAME, "facebookName")
                        .put(FacebookUser.LOCATION, new JsonObject().put("location", new JsonObject()
                                .put(LOCATION_STREET_FIELD, "facebookStreet")
                                .put(LOCATION_CITY_FIELD, "facebookCity")
                                .put(LOCATION_REGION_FIELD, "facebookRegion")
                                .put(LOCATION_ZIP_FIELD, 123456)
                                .put(LOCATION_COUNTRY_FIELD, "facebookCountry")))
                        .put(FacebookUser.BIRTHDAY, "10/24")
                        .put(FacebookUser.ABOUT, "facebook about me"));


        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertValue(user -> {
            assertEquals("facebookID", user.getId());
            assertEquals("facebookID", user.getId());
            assertEquals("facebookID", user.getAdditionalInformation().get(StandardClaims.SUB));
            assertEquals("facebookName", user.getAdditionalInformation().get(StandardClaims.NAME));
            assertEquals("facebookID", user.getAdditionalInformation().get(StandardClaims.PREFERRED_USERNAME));
            HashMap<String, Object> address = (HashMap<String, Object>) user.getAdditionalInformation().get(StandardClaims.ADDRESS);
            assertNotNull(address);
            assertEquals("facebookStreet", address.get("street_address"));
            assertEquals("facebookCity", address.get("locality"));
            assertEquals("facebookRegion", address.get("region"));
            assertEquals(123456, address.get("postal_code"));
            assertEquals("facebookCountry", address.get("country"));
            assertEquals("0000-10-24", user.getAdditionalInformation().get(StandardClaims.BIRTHDATE));
            assertNotNull(user.getAdditionalInformation().get(FacebookUser.ABOUT));
            return true;
        });

        verify(client, times(1)).postAbs("https://graph.facebook.com/v7.0/oauth/access_token");
        verify(client, times(1)).postAbs("https://graph.facebook.com/v7.0/me");
    }

    @Test
    public void shouldAuthenticate_badCredentials() {

        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("code", "facebookAuthorizationCode");

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(configuration.getAccessTokenUri()).thenReturn("https://graph.facebook.com/v7.0/oauth/access_token");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.UNAUTHORIZED_401);
        when(httpResponse.bodyAsString())
                .thenReturn("not authorized");


        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertError(BadCredentialsException.class);

        verify(client, times(1)).postAbs("https://graph.facebook.com/v7.0/oauth/access_token");
    }

    @Test
    public void shouldAuthenticate_profileBadCredentials() {

        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("code", "facebookAuthorizationCode");

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(configuration.getAccessTokenUri()).thenReturn("https://graph.facebook.com/v7.0/oauth/access_token");
        when(configuration.getUserProfileUri()).thenReturn("https://graph.facebook.com/v7.0/me");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200)
                .thenReturn(HttpStatusCode.UNAUTHORIZED_401);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject().put("access_token", "badFacebookGeneratedAccessToken"));
        when(httpResponse.bodyAsString())
                .thenReturn("not authorized");

        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertError(BadCredentialsException.class);

        verify(client, times(1)).postAbs("https://graph.facebook.com/v7.0/oauth/access_token");
        verify(client, times(1)).postAbs("https://graph.facebook.com/v7.0/me");
    }
}