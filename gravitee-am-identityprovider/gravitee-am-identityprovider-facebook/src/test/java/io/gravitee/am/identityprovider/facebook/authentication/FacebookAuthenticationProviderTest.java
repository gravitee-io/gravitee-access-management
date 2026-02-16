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
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.facebook.FacebookIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.facebook.model.FacebookUser;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.impl.ClientPhase;
import io.vertx.ext.web.client.impl.WebClientInternal;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.LOCATION_CITY_FIELD;
import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.LOCATION_COUNTRY_FIELD;
import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.LOCATION_REGION_FIELD;
import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.LOCATION_STREET_FIELD;
import static io.gravitee.am.identityprovider.facebook.model.FacebookUser.LOCATION_ZIP_FIELD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private DefaultIdentityProviderGroupMapper groupMapper;

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

        when(configuration.getUserAuthorizationUri()).thenReturn("https://www.facebook.com/v24.0/dialog/oauth");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getResponseType()).thenReturn("code");
        when(configuration.getScopes()).thenReturn(Collections.emptySet());

        final String state = RandomString.generate();
        Request request = cut.signInUrl("https://gravitee.io", state);

        assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("https://www.facebook.com/v24.0/dialog/oauth?client_id=testClientId&redirect_uri=https://gravitee.io&response_type=code&state=" + state, request.getUri());
        assertNull(request.getHeaders());
    }

    @Test
    public void shouldGenerateAsyncSignInUrl() {

        when(configuration.getUserAuthorizationUri()).thenReturn("https://www.facebook.com/v24.0/dialog/oauth");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getResponseType()).thenReturn("code");
        when(configuration.getScopes()).thenReturn(Collections.emptySet());

        final String state = RandomString.generate();
        Request request = (Request)cut.asyncSignInUrl("https://gravitee.io", state).blockingGet();

        assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("https://www.facebook.com/v24.0/dialog/oauth?client_id=testClientId&redirect_uri=https://gravitee.io&response_type=code&state=" + state, request.getUri());
        assertNull(request.getHeaders());
    }

    @Test
    public void shouldGenerateSignInUrl_withScopes() {

        when(configuration.getUserAuthorizationUri()).thenReturn("https://www.facebook.com/v24.0/dialog/oauth");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getResponseType()).thenReturn("code");
        when(configuration.getScopes()).thenReturn(new HashSet<>(Arrays.asList("scope1", "scope2", "scope3")));

        final String state = RandomString.generate();
        Request request = cut.signInUrl("https://gravitee.io", state);

        assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("https://www.facebook.com/v24.0/dialog/oauth?client_id=testClientId&redirect_uri=https://gravitee.io&response_type=code&scope=scope1%20scope2%20scope3&state=" + state, request.getUri());
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

        when(groupMapper.apply(Mockito.any(), Mockito.any())).thenReturn(List.of("Group1"));

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(configuration.getAccessTokenUri()).thenReturn("https://graph.facebook.com/v24.0/oauth/access_token");
        when(configuration.getUserProfileUri()).thenReturn("https://graph.facebook.com/v24.0/me");
        when(configuration.getApiVersion()).thenReturn("v24.0");

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
                        .put(FacebookUser.BIRTHDAY, "10/24"));


        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
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
            assertTrue(user.getGroups().contains("Group1"));
            return true;
        });

        verify(client, times(1)).postAbs("https://graph.facebook.com/v24.0/oauth/access_token");
        verify(client, times(1)).postAbs("https://graph.facebook.com/v24.0/me");
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
        when(configuration.getAccessTokenUri()).thenReturn("https://graph.facebook.com/v24.0/oauth/access_token");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.UNAUTHORIZED_401);
        when(httpResponse.bodyAsString())
                .thenReturn("not authorized");


        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(BadCredentialsException.class);

        verify(client, times(1)).postAbs("https://graph.facebook.com/v24.0/oauth/access_token");
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
        when(configuration.getAccessTokenUri()).thenReturn("https://graph.facebook.com/v24.0/oauth/access_token");
        when(configuration.getUserProfileUri()).thenReturn("https://graph.facebook.com/v24.0/me");
        when(configuration.getApiVersion()).thenReturn("v24.0");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200)
                .thenReturn(HttpStatusCode.UNAUTHORIZED_401);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject().put("access_token", "badFacebookGeneratedAccessToken"));
        when(httpResponse.bodyAsString())
                .thenReturn("not authorized");

        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(BadCredentialsException.class);

        verify(client, times(1)).postAbs("https://graph.facebook.com/v24.0/oauth/access_token");
        verify(client, times(1)).postAbs("https://graph.facebook.com/v24.0/me");
    }

    @Test
    public void shouldAuthenticate_withLegacyApiVersion() {

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
        when(configuration.getAccessTokenUri()).thenReturn("https://graph.facebook.com/v8.0/oauth/access_token");
        when(configuration.getUserProfileUri()).thenReturn("https://graph.facebook.com/v8.0/me");
        when(configuration.getApiVersion()).thenReturn("v8.0");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200)
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject().put("access_token", "facebookGeneratedAccessToken"))
                .thenReturn(new JsonObject().put(FacebookUser.ID, "facebookID")
                        .put(FacebookUser.NAME, "facebookName"));

        TestObserver<User> obs = cut.loadUserByUsername(authentication).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(user -> {
            assertEquals("facebookID", user.getId());
            return true;
        });

        verify(client, times(1)).postAbs("https://graph.facebook.com/v8.0/oauth/access_token");
        verify(client, times(1)).postAbs("https://graph.facebook.com/v8.0/me");
    }

    @Test
    public void shouldComputeCorrectAppSecretProof() throws Exception {
        String accessToken = "test_access_token";
        String clientSecret = "test_client_secret";

        // Compute expected HMAC-SHA256 independently.
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));
        String expected = HexFormat.of().formatHex(hmac);

        String actual = FacebookAuthenticationProvider.computeAppSecretProof(accessToken, clientSecret);

        assertEquals(expected, actual);
        // SHA-256 produces 32 bytes = 64 hex characters.
        assertEquals(64, actual.length());
    }
}
