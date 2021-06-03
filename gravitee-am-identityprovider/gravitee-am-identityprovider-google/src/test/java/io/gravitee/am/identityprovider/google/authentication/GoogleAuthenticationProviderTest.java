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
package io.gravitee.am.identityprovider.google.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.hmac.MACJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.HMACKeyProcessor;
import io.gravitee.am.identityprovider.google.GoogleIdentityProviderConfiguration;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.el.TemplateEngine;
import io.reactivex.observers.TestObserver;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.impl.ClientPhase;
import io.vertx.ext.web.client.impl.WebClientInternal;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class GoogleAuthenticationProviderTest {

    @Spy
    protected WebClient client = WebClient.wrap(Vertx.vertx().createHttpClient());

    @Mock
    protected HttpResponse httpResponse;

    @Spy
    private GoogleIdentityProviderConfiguration configuration = new GoogleIdentityProviderConfiguration();

    @Mock
    private DefaultIdentityProviderMapper mapper;

    @Mock
    private DefaultIdentityProviderRoleMapper roleMapper;

    @InjectMocks
    private GoogleAuthenticationProvider provider;

    /*
    {
  "sub": "subjohndoe",
  "aud": "audsubjohndoe",
  "auth_time": 1594912553,
  "iss": "http://gravitee.io/domain-test/oidc",
  "name": "John Doe",
  "preferred_username": "john.doe@graviteesource.com",
  "exp": 1594926981,
  "given_name": "John",
  "iat": 1594912581,
  "family_name": "Doe"
    }
     */
    private final String jwt = "eyJraWQiOiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsImFsZyI6IkhTMjU2In0.eyJzdWIiOiJzdWJqb2huZG9lIiwiYXVkIjoiYXVkc3Viam9obmRvZSIsImF1dGhfdGltZSI6MTU5NDkxMjU1MywiaXNzIjoiaHR0cDovL2dyYXZpdGVlLmlvL2RvbWFpbi10ZXN0L29pZGMiLCJuYW1lIjoiSm9obiBEb2UiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJqb2huLmRvZUBncmF2aXRlZXNvdXJjZS5jb20iLCJnaXZlbl9uYW1lIjoiSm9obiIsImlhdCI6MTU5NDkxMjU4MSwiZmFtaWx5X25hbWUiOiJEb2UifQ.V5uLnWoLpee-TvQJ_QB1051CzVOIuVs9h6a665ynvAY";
    private final String secretKey = "02e52785065a9ab489dfd3063a73d31efd5ca196a7a9a00ff070812b0e608fce";

    @Before
    public void init() {
        ((WebClientInternal) client.getDelegate()).addInterceptor(event -> {

            if (event.phase() == ClientPhase.PREPARE_REQUEST) {
                // By pass send request and jump directly to dispatch phase with the mocked http response.
                event.dispatchResponse(httpResponse);
            }

            event.next();
        });

        when(configuration.getClientSecret()).thenReturn("a_secret");
    }

    @Test
    public void shouldGenerateSignInUrl() throws Exception {
        forceProviderInfoForTest();

        // openid scope will be added by default
        when(configuration.getClientId()).thenReturn("testClientId");

        final String state = RandomString.generate();
        Request request = provider.signInUrl("https://gravitee.io", state);

        Assert.assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals(GoogleIdentityProviderConfiguration.AUTHORIZATION_URL + "?client_id=testClientId&response_type=code&scope=openid profile email&state=" + state + "&redirect_uri=https://gravitee.io", request.getUri());
        assertNull(request.getHeaders());
    }

    @Test
    public void shouldGenerateAsyncSignInUrl() throws Exception {
        forceProviderInfoForTest();

        // openid scope will be added by default
        when(configuration.getClientId()).thenReturn("testClientId");

        final String state = RandomString.generate();
        Request request = (Request) provider.asyncSignInUrl("https://gravitee.io", state).blockingGet();

        Assert.assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals(GoogleIdentityProviderConfiguration.AUTHORIZATION_URL + "?client_id=testClientId&response_type=code&scope=openid profile email&state=" + state + "&redirect_uri=https://gravitee.io", request.getUri());
        assertNull(request.getHeaders());
    }

    @Test
    public void shouldGenerateSignInUrl_withScope() throws Exception {

        when(configuration.getClientId()).thenReturn("testClientId");
        LinkedHashSet<String> scopes = new LinkedHashSet<>(); // LinkedHashSet to preserve order of scopes into the URI
        scopes.add("other_scope");
        scopes.add("other_scope2");
        // openid scope will be added by default
        when(configuration.getScopes()).thenReturn(scopes);

        forceProviderInfoForTest();

        final String state = RandomString.generate();
        Request request = provider.signInUrl("https://gravitee.io", state);

        Assert.assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals(GoogleIdentityProviderConfiguration.AUTHORIZATION_URL + "?client_id=testClientId&response_type=code&scope=other_scope other_scope2 openid profile email&state=" + state + "&redirect_uri=https://gravitee.io", request.getUri());
        assertNull(request.getHeaders());
    }

    @Test
    public void shouldAuthenticate() throws Exception {
        forceProviderInfoForTest();

        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set(GoogleIdentityProviderConfiguration.CODE_PARAMETER, "code");

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(authenticationContext.get("id_token")).thenReturn(jwt);

        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject().put("token_type", "Bearer")
                        .put("scope", "openid")
                        .put("expires_in", 3599)
                        .put("access_token", jwt)
                        .put("id_token", jwt));

        TestObserver<User> obs = provider.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertValue(user -> {
            assertEquals("subjohndoe", user.getId());
            assertEquals("john.doe@graviteesource.com", user.getUsername());
            assertEquals("John Doe", user.getAdditionalInformation().get("name"));
            assertEquals("John", user.getAdditionalInformation().get("given_name"));
            assertEquals("Doe", user.getAdditionalInformation().get("family_name"));

            assertTrue(user.getRoles().isEmpty());
            return true;
        });

        verify(authenticationContext, times(1)).set("id_token", jwt);
        verify(client, times(1)).postAbs(GoogleIdentityProviderConfiguration.TOKEN_URL);
    }
    @Test
    public void shouldAuthenticate_RoleMapping() throws Exception {
        forceProviderInfoForTest();
        Map<String, String[]> roles = new HashMap<>();
        roles.put("admin", new String[] { "preferred_username=john.doe@graviteesource.com"});
        when(roleMapper.getRoles()).thenReturn(roles);
        when(roleMapper.apply(any(), anyMap())).thenCallRealMethod();

        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);
        when(authenticationContext.get("id_token")).thenReturn(jwt);
        when(authenticationContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set(GoogleIdentityProviderConfiguration.CODE_PARAMETER, "code");

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject().put("token_type", "Bearer")
                        .put("scope", "openid")
                        .put("expires_in", 3599)
                        .put("access_token", jwt)
                        .put("id_token", jwt));

        TestObserver<User> obs = provider.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertValue(user -> {
            assertEquals("subjohndoe", user.getId());
            assertEquals("john.doe@graviteesource.com", user.getUsername());
            assertEquals("John Doe", user.getAdditionalInformation().get("name"));
            assertEquals("John", user.getAdditionalInformation().get("given_name"));
            assertEquals("Doe", user.getAdditionalInformation().get("family_name"));

            assertTrue(user.getRoles().contains("admin"));
            return true;
        });
        verify(authenticationContext, times(1)).set("id_token", jwt);
        verify(client, times(1)).postAbs(GoogleIdentityProviderConfiguration.TOKEN_URL);
    }

    @Test
    public void shouldAuthenticate_invalidJwt() throws Exception {
        forceProviderInfoForTest();

        final String badJwt = "eyJraWQiOiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsImFsZyI6IkhTMjU2In0.eyJzdWIiOiJzdWJqb2huZG9lIiwiYXVkIjoiYXVkc3Viam9obmRvZSIsImF1dGhfdGltZSI6MTU5NDkxMjU1MywiaXNzIjoiaHR0cDovL2dyYXZpdGVlLmlvL2RvbWFpbi10ZXN0L29pZGMiLCJuYW1lIjoiSm9obiBEb2UiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJqb2huLmRvZUBncmF2aXRlZXNvdXJjZS5jb20iLCJleHAiOjE1OTQ5MjY5ODEsImdpdmVuX25hbWUiOiJKb2huIiwiaWF0IjoxNTk0OTEyNTgxLCJmYW1pbHlfbmFtZSI6IkRvZSJ9.Kgr8PkN9GRtfeASpBF1uvUlK14SEQRIk-XtvwloGzdo";

        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);
        when(authenticationContext.get("id_token")).thenReturn(badJwt);

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set(GoogleIdentityProviderConfiguration.CODE_PARAMETER, "code");

        when(request.parameters()).thenReturn(parameters);
        when(configuration.getCodeParameter()).thenReturn("code");
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");

        when(authentication.getContext().get("redirect_uri")).thenReturn("https://gravitee.io");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject().put("token_type", "Bearer")
                        .put("scope", "openid")
                        .put("expires_in", 3599)
                        .put("access_token", jwt)
                        .put("id_token", badJwt));

        TestObserver<User> obs = provider.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertError(BadCredentialsException.class);

        verify(authenticationContext, times(1)).set("id_token", badJwt);
        verify(client, times(1)).postAbs(GoogleIdentityProviderConfiguration.TOKEN_URL);
    }

    // this method is call inside each test method to avoid override of init value by mock/spy
    private void forceProviderInfoForTest() throws Exception {
        provider.afterPropertiesSet();
        // override the KeyProcessor for test purpose
        HMACKeyProcessor keyProcessor = new HMACKeyProcessor<>();
        keyProcessor.setJwkSourceResolver(new MACJWKSourceResolver(secretKey));
        provider.setJwtProcessor(keyProcessor.create(SignatureAlgorithm.HS256));
    }
}
