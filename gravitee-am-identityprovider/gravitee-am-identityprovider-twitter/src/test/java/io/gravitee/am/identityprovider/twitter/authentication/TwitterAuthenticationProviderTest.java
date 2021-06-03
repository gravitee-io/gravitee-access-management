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
package io.gravitee.am.identityprovider.twitter.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.twitter.TwitterIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.twitter.authentication.utils.SignerUtils;
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
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class TwitterAuthenticationProviderTest {

    @Spy
    protected WebClient client = WebClient.wrap(Vertx.vertx().createHttpClient());

    @Mock
    protected HttpResponse httpResponse;

    @Spy
    private TwitterIdentityProviderConfiguration configuration = new TwitterIdentityProviderConfiguration();

    @Mock
    private DefaultIdentityProviderMapper mapper;

    @Mock
    private DefaultIdentityProviderRoleMapper roleMapper;

    @InjectMocks
    private TwitterAuthenticationProvider provider;

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
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsString())
                .thenReturn("oauth_token=NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0" +
                        "&oauth_token_secret=veNRnAWe6inFuo8o2u8SLLZLjolYDmDP7SzL0YfYI" +
                        "&oauth_callback_confirmed=true");

        Request request = provider.asyncSignInUrl("https://gravitee.io", RandomString.generate()).blockingGet();

        assertNotNull(request);
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("https://api.twitter.com/oauth/authorize?oauth_token=NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0", request.getUri());
    }

    @Test(expected = BadCredentialsException.class)
    public void shouldNotGenerateSignInUrl_BadCredential() {
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.BAD_REQUEST_400);

        provider.asyncSignInUrl("https://gravitee.io", RandomString.generate()).blockingGet();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowIllegalStateException() {
        // An authentication request is required on Twitter before
        // generating the signIn Url, so synchronous method is forbidden.
        provider.signInUrl("https://gravitee.io", RandomString.generate());
    }

    @Test
    public void shouldAuthenticate() {
        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("oauth_token", "NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0");
        parameters.set("oauth_verifier", "temporarytokenverifier");
        when(request.parameters()).thenReturn(parameters);

        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");

        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200)
                .thenReturn(HttpStatusCode.OK_200)
                .thenReturn(HttpStatusCode.OK_200);

        when(httpResponse.bodyAsString())
                .thenReturn("oauth_token=NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0" +
                        "&oauth_token_secret=veNRnAWe6inFuo8o2u8SLLZLjolYDmDP7SzL0YfYI&oauth_callback_confirmed=true")
                .thenReturn("oauth_token=user-NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0" +
                        "&oauth_token_secret=user-veNRnAWe6inFuo8o2u8SLLZLjolYDmDP7SzL0YfYI");

        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject()
                        .put("id_str", "3056585727")
                        .put("name", "John Doe")
                        .put("screen_name", "johndoe")
                        .put("description", "blablabla")
                        .put("friends_count", 45)
                        .put("followers_count", 987)
                        .put("email", "john.doe@domain.net")
                );

        // init token secret
        provider.asyncSignInUrl("https://gravitee.io", RandomString.generate()).blockingGet();

        TestObserver<User> obs = provider.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertValue(user -> {
            assertEquals("Expected same userID", "3056585727", user.getId());
            assertEquals("Expected same userID", "3056585727", user.getAdditionalInformation().get(StandardClaims.SUB));
            assertEquals("Expected same screen_name", "johndoe", user.getAdditionalInformation().get(StandardClaims.PREFERRED_USERNAME));
            assertEquals("Expected same description", "blablabla", user.getAdditionalInformation().get("description"));
            assertEquals("Expected same followers", 987, user.getAdditionalInformation().get("followers"));
            assertEquals("Expected same friends", 45, user.getAdditionalInformation().get("friends"));
            return true;
        });

        verify(client, times(1)).postAbs(matches(configuration.getAccessTokenUri()+"*"));
        verify(client, times(1)).getAbs(configuration.getUserProfileUri()+"?include_email=true");
    }


    @Test
    public void shouldAuthenticate_BadCredential() {
        Authentication authentication = mock(Authentication.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
        when(authentication.getContext()).thenReturn(authenticationContext);

        io.gravitee.gateway.api.Request request = mock(io.gravitee.gateway.api.Request.class);
        when(authenticationContext.request()).thenReturn(request);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("oauth_token", "NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0");
        parameters.set("oauth_verifier", "temporarytokenverifier");
        when(request.parameters()).thenReturn(parameters);

        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");

        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200)
                .thenReturn(HttpStatusCode.BAD_REQUEST_400);

        when(httpResponse.bodyAsString())
                .thenReturn("oauth_token=NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0&oauth_token_secret=veNRnAWe6inFuo8o2u8SLLZLjolYDmDP7SzL0YfYI&oauth_callback_confirmed=true")
                .thenReturn("[ { code: 89, message: 'Invalid or expired token.' } ]");

        // init token secret
        provider.asyncSignInUrl("https://gravitee.io", RandomString.generate()).blockingGet();

        TestObserver<User> obs = provider.loadUserByUsername(authentication).test();

        obs.awaitTerminalEvent();
        obs.assertError(BadCredentialsException.class);

        verify(client, times(1)).postAbs(matches(configuration.getAccessTokenUri()+"*"));
        verify(client, never()).getAbs(configuration.getUserProfileUri()+"?include_email=true");
    }

    @Test
    public void testSignatureBaseString() throws Exception {
        // example from https://developer.twitter.com/en/docs/authentication/oauth-1-0a/creating-a-signature
        final String url = "https://api.twitter.com/1.1/statuses/update.json";

        Map<String, String> parameters = new HashMap<>();
        parameters.put("status", "Hello Ladies + Gentlemen, a signed OAuth request!");
        parameters.put("include_entities", "true");

        Map<String, String> oauthParams = new HashMap<>();
        oauthParams.put("oauth_nonce", "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg");
        oauthParams.put("oauth_consumer_key", "xvz1evFS4wEEPTGEFPHBog");
        oauthParams.put("oauth_timestamp", "1318622958");
        oauthParams.put("oauth_signature_method", "HMAC-SHA1");
        oauthParams.put("oauth_token", "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb");
        oauthParams.put("oauth_version", "1.0");

        final String signatureBasedString = SignerUtils.buildSignatureBaseString("POST",  url, parameters, oauthParams);
        final String expectedSignatureBasedString = "POST&https%3A%2F%2Fapi.twitter.com%2F1.1%2Fstatuses%2Fupdate.json&include_entities%3Dtrue%26oauth_consumer_key%3Dxvz1evFS4wEEPTGEFPHBog%26oauth_nonce%3DkYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg%26oauth_signature_method%3DHMAC-SHA1%26oauth_timestamp%3D1318622958%26oauth_token%3D370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb%26oauth_version%3D1.0%26status%3DHello%2520Ladies%2520%252B%2520Gentlemen%252C%2520a%2520signed%2520OAuth%2520request%2521";

        // ok base string is valid
        Assert.assertEquals(expectedSignatureBasedString, signatureBasedString);
    }
}
