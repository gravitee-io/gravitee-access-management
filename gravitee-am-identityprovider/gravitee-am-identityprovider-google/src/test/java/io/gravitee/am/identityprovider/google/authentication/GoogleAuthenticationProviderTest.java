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
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.hmac.MACJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.HMACKeyProcessor;
import io.gravitee.am.identityprovider.google.GoogleIdentityProviderConfiguration;
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

            if (event.phase() == ClientPhase.SEND_REQUEST) {
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

        Request request = provider.signInUrl("https://gravitee.io");

        Assert.assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals(GoogleIdentityProviderConfiguration.AUTHORIZATION_URL + "?client_id=testClientId&response_type=code&scope=openid profile email&redirect_uri=https://gravitee.io", request.getUri());
        assertNull(request.getHeaders());
    }

    @Test
    public void shouldGenerateAsyncSignInUrl() throws Exception {
        forceProviderInfoForTest();

        // openid scope will be added by default
        when(configuration.getClientId()).thenReturn("testClientId");

        Request request = (Request) provider.asyncSignInUrl("https://gravitee.io").blockingGet();

        Assert.assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals(GoogleIdentityProviderConfiguration.AUTHORIZATION_URL + "?client_id=testClientId&response_type=code&scope=openid profile email&redirect_uri=https://gravitee.io", request.getUri());
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

        Request request = provider.signInUrl("https://gravitee.io");

        Assert.assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals(GoogleIdentityProviderConfiguration.AUTHORIZATION_URL + "?client_id=testClientId&response_type=code&scope=other_scope other_scope2 openid profile email&redirect_uri=https://gravitee.io", request.getUri());
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
        //:"eyJ0eXAiOiJKV1QiLCJub25jZSI6IkVKRG5GakxSd3FvYVBMdmJwV2FqWkdWNGZSTkh6eXpoMkU4YzdueHJLWVEiLCJhbGciOiJSUzI1NiIsIng1dCI6Imh1Tjk1SXZQZmVocTM0R3pCRFoxR1hHaXJuTSIsImtpZCI6Imh1Tjk1SXZQZmVocTM0R3pCRFoxR1hHaXJuTSJ9.eyJhdWQiOiIwMDAwMDAwMy0wMDAwLTAwMDAtYzAwMC0wMDAwMDAwMDAwMDAiLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC9iNzM4OTY2NS01OGRmLTRmNGMtYTNhMy1lZDVhZGYwYWFmZDgvIiwiaWF0IjoxNTk0OTEyMjUzLCJuYmYiOjE1OTQ5MTIyNTMsImV4cCI6MTU5NDkxNjE1MywiYWNjdCI6MCwiYWNyIjoiMSIsImFpbyI6IkFTUUEyLzhRQUFBQTMxUE85ODNVUkpZVTQwS0ZWWjJYVWhSa0RWdkdUTGZLcTczV21oeVpZdlE9IiwiYW1yIjpbInB3ZCJdLCJhcHBfZGlzcGxheW5hbWUiOiJUZXN0LUVMRS1JRFAtQVpVUkUiLCJhcHBpZCI6ImU0YmQwNmJkLTBhOGItNGFlOS05ZjYzLWNmNWZiODgwMDdhMSIsImFwcGlkYWNyIjoiMSIsImZhbWlseV9uYW1lIjoiTGVsZXUiLCJnaXZlbl9uYW1lIjoiRXJpYyIsImlwYWRkciI6IjgyLjIzOC4yNTUuMTQzIiwibmFtZSI6IkVyaWMgTGVsZXUiLCJvaWQiOiI0YWUwMjM1My0yOWJlLTQyZDMtODQ5OS1kOGMzNTUyYTVjOTIiLCJwbGF0ZiI6IjE0IiwicHVpZCI6IjEwMDMyMDAwQ0Y0MDg0MDUiLCJzY3AiOiJEaXJlY3RvcnkuUmVhZC5BbGwgZW1haWwgb3BlbmlkIHByb2ZpbGUgVXNlci5SZWFkIiwic3ViIjoidmNzdmFfb1Q5M1Z0TnloZngtQXFtT1RwUG9EdzZ0ZFFkVzV1M1N5N1EtZyIsInRlbmFudF9yZWdpb25fc2NvcGUiOiJFVSIsInRpZCI6ImI3Mzg5NjY1LTU4ZGYtNGY0Yy1hM2EzLWVkNWFkZjBhYWZkOCIsInVuaXF1ZV9uYW1lIjoiZXJpYy5sZWxldUBncmF2aXRlZXNvdXJjZS5jb20iLCJ1cG4iOiJlcmljLmxlbGV1QGdyYXZpdGVlc291cmNlLmNvbSIsInV0aSI6Inl5bUdwZF9ZU0VHMklOTEVBaGZzQVEiLCJ2ZXIiOiIxLjAiLCJ4bXNfc3QiOnsic3ViIjoiQmowM3BnWUx1V3MxOXVXVUJpOXhxU21IN1JvS1A3bWpRUURtZHVWUEdoWSJ9LCJ4bXNfdGNkdCI6MTU0MDU1Njc0NH0.BsDjR_rYQAc0NjdgQLtCNc3cAsflhlOZnNdrnbEeIBrDO-VWsasrcIYACzyES611NQmPG3NTj1LR1bs5OYe8IYpgoRoVdLLirE788lpLMGudlMVi7CNuUntPZn6ca5iqlRs2PSpxrdp56BpdQcnYvTru3KEC-IKN5BLgykwo_pmMxSnsgQRyQL_38Z20ClA3IZwLW-TFQ93hLSCZxcZmpZIKhTKsseDobuif2Eq2U-uEPqYINbF38QUcW6QsCDzs3PUN6aeWV-Gr6KhxLjghTKi30EOmsY7QGU-342QFu-iq45_WC3_zU-sceFGT0ZL-97jpoXaqERWIbJVRbTeuXQ","id_token":"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Imh1Tjk1SXZQZmVocTM0R3pCRFoxR1hHaXJuTSJ9.eyJhdWQiOiJlNGJkMDZiZC0wYThiLTRhZTktOWY2My1jZjVmYjg4MDA3YTEiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vYjczODk2NjUtNThkZi00ZjRjLWEzYTMtZWQ1YWRmMGFhZmQ4L3YyLjAiLCJpYXQiOjE1OTQ5MTIyNTMsIm5iZiI6MTU5NDkxMjI1MywiZXhwIjoxNTk0OTE2MTUzLCJmYW1pbHlfbmFtZSI6IkxlbGV1IiwiZ2l2ZW5fbmFtZSI6IkVyaWMiLCJuYW1lIjoiRXJpYyBMZWxldSIsIm9pZCI6IjRhZTAyMzUzLTI5YmUtNDJkMy04NDk5LWQ4YzM1NTJhNWM5MiIsInByZWZlcnJlZF91c2VybmFtZSI6ImVyaWMubGVsZXVAZ3Jhdml0ZWVzb3VyY2UuY29tIiwic3ViIjoiQmowM3BnWUx1V3MxOXVXVUJpOXhxU21IN1JvS1A3bWpRUURtZHVWUEdoWSIsInRpZCI6ImI3Mzg5NjY1LTU4ZGYtNGY0Yy1hM2EzLWVkNWFkZjBhYWZkOCIsInV0aSI6Inl5bUdwZF9ZU0VHMklOTEVBaGZzQVEiLCJ2ZXIiOiIyLjAifQ.c3u2k8L9fE4eQ8uM5UON1BRkqsTCEIF8jULtNC4mjN7eqncOKxtodFMsPZQeHm8P5aBYWL_LxHJfndQt9XOIReQmD57KHpdzJP4ZKacdDml5uJh30_sOx09Vm5uaEc7zos52Y8xfpyRXWLIDo-oQBwBEVnQQhFN6zgI1uvMP6Gl3y0hu-iGEwZYFkRShoaljA-k18mHbLBmllyxHvf4K2oNvftK0z9sWAwpw4beHcM2k9v2fBrQv9Ygwga1w0PsrD9U62cQnQ43f9EOIugChRRdReyTI2zoJvOeyKjo9nvxDbsz47WgpzTR7YzLhE931lF2aj6gih_4IRqBBsukpjQ"}
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

        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);
        when(authenticationContext.get("id_token")).thenReturn(jwt);

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
        //:"eyJ0eXAiOiJKV1QiLCJub25jZSI6IkVKRG5GakxSd3FvYVBMdmJwV2FqWkdWNGZSTkh6eXpoMkU4YzdueHJLWVEiLCJhbGciOiJSUzI1NiIsIng1dCI6Imh1Tjk1SXZQZmVocTM0R3pCRFoxR1hHaXJuTSIsImtpZCI6Imh1Tjk1SXZQZmVocTM0R3pCRFoxR1hHaXJuTSJ9.eyJhdWQiOiIwMDAwMDAwMy0wMDAwLTAwMDAtYzAwMC0wMDAwMDAwMDAwMDAiLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC9iNzM4OTY2NS01OGRmLTRmNGMtYTNhMy1lZDVhZGYwYWFmZDgvIiwiaWF0IjoxNTk0OTEyMjUzLCJuYmYiOjE1OTQ5MTIyNTMsImV4cCI6MTU5NDkxNjE1MywiYWNjdCI6MCwiYWNyIjoiMSIsImFpbyI6IkFTUUEyLzhRQUFBQTMxUE85ODNVUkpZVTQwS0ZWWjJYVWhSa0RWdkdUTGZLcTczV21oeVpZdlE9IiwiYW1yIjpbInB3ZCJdLCJhcHBfZGlzcGxheW5hbWUiOiJUZXN0LUVMRS1JRFAtQVpVUkUiLCJhcHBpZCI6ImU0YmQwNmJkLTBhOGItNGFlOS05ZjYzLWNmNWZiODgwMDdhMSIsImFwcGlkYWNyIjoiMSIsImZhbWlseV9uYW1lIjoiTGVsZXUiLCJnaXZlbl9uYW1lIjoiRXJpYyIsImlwYWRkciI6IjgyLjIzOC4yNTUuMTQzIiwibmFtZSI6IkVyaWMgTGVsZXUiLCJvaWQiOiI0YWUwMjM1My0yOWJlLTQyZDMtODQ5OS1kOGMzNTUyYTVjOTIiLCJwbGF0ZiI6IjE0IiwicHVpZCI6IjEwMDMyMDAwQ0Y0MDg0MDUiLCJzY3AiOiJEaXJlY3RvcnkuUmVhZC5BbGwgZW1haWwgb3BlbmlkIHByb2ZpbGUgVXNlci5SZWFkIiwic3ViIjoidmNzdmFfb1Q5M1Z0TnloZngtQXFtT1RwUG9EdzZ0ZFFkVzV1M1N5N1EtZyIsInRlbmFudF9yZWdpb25fc2NvcGUiOiJFVSIsInRpZCI6ImI3Mzg5NjY1LTU4ZGYtNGY0Yy1hM2EzLWVkNWFkZjBhYWZkOCIsInVuaXF1ZV9uYW1lIjoiZXJpYy5sZWxldUBncmF2aXRlZXNvdXJjZS5jb20iLCJ1cG4iOiJlcmljLmxlbGV1QGdyYXZpdGVlc291cmNlLmNvbSIsInV0aSI6Inl5bUdwZF9ZU0VHMklOTEVBaGZzQVEiLCJ2ZXIiOiIxLjAiLCJ4bXNfc3QiOnsic3ViIjoiQmowM3BnWUx1V3MxOXVXVUJpOXhxU21IN1JvS1A3bWpRUURtZHVWUEdoWSJ9LCJ4bXNfdGNkdCI6MTU0MDU1Njc0NH0.BsDjR_rYQAc0NjdgQLtCNc3cAsflhlOZnNdrnbEeIBrDO-VWsasrcIYACzyES611NQmPG3NTj1LR1bs5OYe8IYpgoRoVdLLirE788lpLMGudlMVi7CNuUntPZn6ca5iqlRs2PSpxrdp56BpdQcnYvTru3KEC-IKN5BLgykwo_pmMxSnsgQRyQL_38Z20ClA3IZwLW-TFQ93hLSCZxcZmpZIKhTKsseDobuif2Eq2U-uEPqYINbF38QUcW6QsCDzs3PUN6aeWV-Gr6KhxLjghTKi30EOmsY7QGU-342QFu-iq45_WC3_zU-sceFGT0ZL-97jpoXaqERWIbJVRbTeuXQ","id_token":"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Imh1Tjk1SXZQZmVocTM0R3pCRFoxR1hHaXJuTSJ9.eyJhdWQiOiJlNGJkMDZiZC0wYThiLTRhZTktOWY2My1jZjVmYjg4MDA3YTEiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vYjczODk2NjUtNThkZi00ZjRjLWEzYTMtZWQ1YWRmMGFhZmQ4L3YyLjAiLCJpYXQiOjE1OTQ5MTIyNTMsIm5iZiI6MTU5NDkxMjI1MywiZXhwIjoxNTk0OTE2MTUzLCJmYW1pbHlfbmFtZSI6IkxlbGV1IiwiZ2l2ZW5fbmFtZSI6IkVyaWMiLCJuYW1lIjoiRXJpYyBMZWxldSIsIm9pZCI6IjRhZTAyMzUzLTI5YmUtNDJkMy04NDk5LWQ4YzM1NTJhNWM5MiIsInByZWZlcnJlZF91c2VybmFtZSI6ImVyaWMubGVsZXVAZ3Jhdml0ZWVzb3VyY2UuY29tIiwic3ViIjoiQmowM3BnWUx1V3MxOXVXVUJpOXhxU21IN1JvS1A3bWpRUURtZHVWUEdoWSIsInRpZCI6ImI3Mzg5NjY1LTU4ZGYtNGY0Yy1hM2EzLWVkNWFkZjBhYWZkOCIsInV0aSI6Inl5bUdwZF9ZU0VHMklOTEVBaGZzQVEiLCJ2ZXIiOiIyLjAifQ.c3u2k8L9fE4eQ8uM5UON1BRkqsTCEIF8jULtNC4mjN7eqncOKxtodFMsPZQeHm8P5aBYWL_LxHJfndQt9XOIReQmD57KHpdzJP4ZKacdDml5uJh30_sOx09Vm5uaEc7zos52Y8xfpyRXWLIDo-oQBwBEVnQQhFN6zgI1uvMP6Gl3y0hu-iGEwZYFkRShoaljA-k18mHbLBmllyxHvf4K2oNvftK0z9sWAwpw4beHcM2k9v2fBrQv9Ygwga1w0PsrD9U62cQnQ43f9EOIugChRRdReyTI2zoJvOeyKjo9nvxDbsz47WgpzTR7YzLhE931lF2aj6gih_4IRqBBsukpjQ"}
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
        //:"eyJ0eXAiOiJKV1QiLCJub25jZSI6IkVKRG5GakxSd3FvYVBMdmJwV2FqWkdWNGZSTkh6eXpoMkU4YzdueHJLWVEiLCJhbGciOiJSUzI1NiIsIng1dCI6Imh1Tjk1SXZQZmVocTM0R3pCRFoxR1hHaXJuTSIsImtpZCI6Imh1Tjk1SXZQZmVocTM0R3pCRFoxR1hHaXJuTSJ9.eyJhdWQiOiIwMDAwMDAwMy0wMDAwLTAwMDAtYzAwMC0wMDAwMDAwMDAwMDAiLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC9iNzM4OTY2NS01OGRmLTRmNGMtYTNhMy1lZDVhZGYwYWFmZDgvIiwiaWF0IjoxNTk0OTEyMjUzLCJuYmYiOjE1OTQ5MTIyNTMsImV4cCI6MTU5NDkxNjE1MywiYWNjdCI6MCwiYWNyIjoiMSIsImFpbyI6IkFTUUEyLzhRQUFBQTMxUE85ODNVUkpZVTQwS0ZWWjJYVWhSa0RWdkdUTGZLcTczV21oeVpZdlE9IiwiYW1yIjpbInB3ZCJdLCJhcHBfZGlzcGxheW5hbWUiOiJUZXN0LUVMRS1JRFAtQVpVUkUiLCJhcHBpZCI6ImU0YmQwNmJkLTBhOGItNGFlOS05ZjYzLWNmNWZiODgwMDdhMSIsImFwcGlkYWNyIjoiMSIsImZhbWlseV9uYW1lIjoiTGVsZXUiLCJnaXZlbl9uYW1lIjoiRXJpYyIsImlwYWRkciI6IjgyLjIzOC4yNTUuMTQzIiwibmFtZSI6IkVyaWMgTGVsZXUiLCJvaWQiOiI0YWUwMjM1My0yOWJlLTQyZDMtODQ5OS1kOGMzNTUyYTVjOTIiLCJwbGF0ZiI6IjE0IiwicHVpZCI6IjEwMDMyMDAwQ0Y0MDg0MDUiLCJzY3AiOiJEaXJlY3RvcnkuUmVhZC5BbGwgZW1haWwgb3BlbmlkIHByb2ZpbGUgVXNlci5SZWFkIiwic3ViIjoidmNzdmFfb1Q5M1Z0TnloZngtQXFtT1RwUG9EdzZ0ZFFkVzV1M1N5N1EtZyIsInRlbmFudF9yZWdpb25fc2NvcGUiOiJFVSIsInRpZCI6ImI3Mzg5NjY1LTU4ZGYtNGY0Yy1hM2EzLWVkNWFkZjBhYWZkOCIsInVuaXF1ZV9uYW1lIjoiZXJpYy5sZWxldUBncmF2aXRlZXNvdXJjZS5jb20iLCJ1cG4iOiJlcmljLmxlbGV1QGdyYXZpdGVlc291cmNlLmNvbSIsInV0aSI6Inl5bUdwZF9ZU0VHMklOTEVBaGZzQVEiLCJ2ZXIiOiIxLjAiLCJ4bXNfc3QiOnsic3ViIjoiQmowM3BnWUx1V3MxOXVXVUJpOXhxU21IN1JvS1A3bWpRUURtZHVWUEdoWSJ9LCJ4bXNfdGNkdCI6MTU0MDU1Njc0NH0.BsDjR_rYQAc0NjdgQLtCNc3cAsflhlOZnNdrnbEeIBrDO-VWsasrcIYACzyES611NQmPG3NTj1LR1bs5OYe8IYpgoRoVdLLirE788lpLMGudlMVi7CNuUntPZn6ca5iqlRs2PSpxrdp56BpdQcnYvTru3KEC-IKN5BLgykwo_pmMxSnsgQRyQL_38Z20ClA3IZwLW-TFQ93hLSCZxcZmpZIKhTKsseDobuif2Eq2U-uEPqYINbF38QUcW6QsCDzs3PUN6aeWV-Gr6KhxLjghTKi30EOmsY7QGU-342QFu-iq45_WC3_zU-sceFGT0ZL-97jpoXaqERWIbJVRbTeuXQ","id_token":"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Imh1Tjk1SXZQZmVocTM0R3pCRFoxR1hHaXJuTSJ9.eyJhdWQiOiJlNGJkMDZiZC0wYThiLTRhZTktOWY2My1jZjVmYjg4MDA3YTEiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vYjczODk2NjUtNThkZi00ZjRjLWEzYTMtZWQ1YWRmMGFhZmQ4L3YyLjAiLCJpYXQiOjE1OTQ5MTIyNTMsIm5iZiI6MTU5NDkxMjI1MywiZXhwIjoxNTk0OTE2MTUzLCJmYW1pbHlfbmFtZSI6IkxlbGV1IiwiZ2l2ZW5fbmFtZSI6IkVyaWMiLCJuYW1lIjoiRXJpYyBMZWxldSIsIm9pZCI6IjRhZTAyMzUzLTI5YmUtNDJkMy04NDk5LWQ4YzM1NTJhNWM5MiIsInByZWZlcnJlZF91c2VybmFtZSI6ImVyaWMubGVsZXVAZ3Jhdml0ZWVzb3VyY2UuY29tIiwic3ViIjoiQmowM3BnWUx1V3MxOXVXVUJpOXhxU21IN1JvS1A3bWpRUURtZHVWUEdoWSIsInRpZCI6ImI3Mzg5NjY1LTU4ZGYtNGY0Yy1hM2EzLWVkNWFkZjBhYWZkOCIsInV0aSI6Inl5bUdwZF9ZU0VHMklOTEVBaGZzQVEiLCJ2ZXIiOiIyLjAifQ.c3u2k8L9fE4eQ8uM5UON1BRkqsTCEIF8jULtNC4mjN7eqncOKxtodFMsPZQeHm8P5aBYWL_LxHJfndQt9XOIReQmD57KHpdzJP4ZKacdDml5uJh30_sOx09Vm5uaEc7zos52Y8xfpyRXWLIDo-oQBwBEVnQQhFN6zgI1uvMP6Gl3y0hu-iGEwZYFkRShoaljA-k18mHbLBmllyxHvf4K2oNvftK0z9sWAwpw4beHcM2k9v2fBrQv9Ygwga1w0PsrD9U62cQnQ43f9EOIugChRRdReyTI2zoJvOeyKjo9nvxDbsz47WgpzTR7YzLhE931lF2aj6gih_4IRqBBsukpjQ"}
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
