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
package io.gravitee.am.gateway.handler.oauth2.service.granter.refresh;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceConsistencyValidationService;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.RefreshToken;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import net.minidev.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RefreshTokenGranterTest {

    @InjectMocks
    private RefreshTokenGranter granter = new RefreshTokenGranter();

    @Mock
    private TokenRequest tokenRequest;

    @Mock
    private TokenService tokenService;

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private ResourceConsistencyValidationService resourceConsistencyValidationService;

    @Test
    public void shouldGenerateAnAccessToken() {
        String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshToken);

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList(new String[]{"refresh_token"}));

        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId(client.getClientId());
        oAuth2Request.setGrantType(GrantType.REFRESH_TOKEN);
        oAuth2Request.setParameters(parameters);

        Token accessToken = new AccessToken("test-token");

        when(tokenRequest.parameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);

        when(tokenService.create(any(), any(), any())).thenReturn(Single.just(accessToken));
        when(tokenService.refresh(refreshToken, tokenRequest, client)).thenReturn(Single.just(new RefreshToken(refreshToken)));
        when(resourceConsistencyValidationService.resolveFinalResources(any(OAuth2Request.class), any())).thenReturn(java.util.Set.of());
        // PRE_TOKEN request expects response object
        when(rulesEngine.fire(any(), any(), any(), any(), any())).thenReturn(Single.just(executionContext));
        // POST_TOKEN request does not
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(token -> token.getValue().equals("test-token"));
    }

    @Test
    public void shouldNotGenerateAnAccessToken_invalidRequest() {
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        Client client = new Client();
        client.setClientId("my-client-id");

        when(tokenRequest.parameters()).thenReturn(parameters);

        granter.grant(tokenRequest, client).test().assertError(InvalidRequestException.class);
    }

    @Test
    public void shouldNotGenerateAnAccessToken_invalidGrant() {
        String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshToken);

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList(new String[]{"refresh_token"}));

        when(tokenRequest.parameters()).thenReturn(parameters);

        when(tokenService.refresh(refreshToken, tokenRequest, client)).thenReturn(Single.error(new InvalidGrantException("error message")));

        granter.grant(tokenRequest, client).test().assertError(InvalidGrantException.class);
    }

    @Test
    public void shouldGenerateAnAccessToken_DisableRefreshTokenRotation() {
        String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshToken);

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList(new String[]{"refresh_token"}));
        client.setDisableRefreshTokenRotation(true);

        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId(client.getClientId());
        oAuth2Request.setGrantType(GrantType.REFRESH_TOKEN);
        oAuth2Request.setParameters(parameters);

        Token accessToken = new AccessToken("test-token");

        when(tokenRequest.parameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);

        ArgumentCaptor<OAuth2Request> oAuth2RequestArgumentCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
        when(tokenService.create(oAuth2RequestArgumentCaptor.capture(), any(), any())).thenReturn(Single.just(accessToken));
        when(tokenService.refresh(refreshToken, tokenRequest, client)).thenReturn(Single.just(new RefreshToken(refreshToken)));
        // PRE_TOKEN request expects response object
        when(rulesEngine.fire(any(), any(), any(), any(), any())).thenReturn(Single.just(executionContext));
        // POST_TOKEN request does not
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(token -> token.getValue().equals("test-token"));
        testObserver.assertValue(token -> token.getRefreshToken().equals(refreshToken));
        OAuth2Request oAuth2RequestArgumentCaptorValue = oAuth2RequestArgumentCaptor.getValue();
        assertNotNull(oAuth2RequestArgumentCaptorValue);
        assertFalse(oAuth2RequestArgumentCaptorValue.isSupportRefreshToken());
    }

    // RFC 8707 Resource Indicators - Resource Consistency Tests

    @Test
    public void shouldExtractOrigResourcesFromRefreshTokenAndCallValidationService() {
        // Arrange: Refresh token with orig_resources claim to test extraction and validation flow
        String refreshTokenValue = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshTokenValue);
        
        Set<String> origResources = Set.of("https://api.example.com/photos", "https://api.example.com/videos");
        Set<String> requestedResources = Set.of("https://api.example.com/photos"); // Valid subset
        
        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList("refresh_token"));
        
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId(client.getClientId());
        oAuth2Request.setGrantType(GrantType.REFRESH_TOKEN);
        oAuth2Request.setParameters(parameters);
        oAuth2Request.setResources(requestedResources);
        
        // Create refresh token with orig_resources claim
        Map<String, Object> refreshTokenJWT = new HashMap<>();
        JSONArray origResourcesArray = new JSONArray();
        origResourcesArray.addAll(origResources);
        refreshTokenJWT.put(Claims.ORIG_RESOURCES, origResourcesArray);
        refreshTokenJWT.put("sub", "user-123");
        
        RefreshToken refreshToken = new RefreshToken(refreshTokenValue);
        refreshToken.setAdditionalInformation(refreshTokenJWT);
        
        Token accessToken = new AccessToken("test-token");
        
        // Capture the resolver call to verify it's called with correct parameters
        ArgumentCaptor<TokenRequest> validationRequestCaptor = ArgumentCaptor.forClass(TokenRequest.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> validationResourcesCaptor = ArgumentCaptor.forClass((Class<Set<String>>) (Class<?>) Set.class);
        
        when(tokenRequest.parameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);
        when(tokenService.refresh(refreshTokenValue, tokenRequest, client)).thenReturn(Single.just(refreshToken));
        when(resourceConsistencyValidationService.resolveFinalResources(validationRequestCaptor.capture(), validationResourcesCaptor.capture()))
                .thenReturn(requestedResources);
        when(tokenService.create(any(), any(), any())).thenReturn(Single.just(accessToken));
        when(rulesEngine.fire(any(), any(), any(), any(), any())).thenReturn(Single.just(executionContext));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));
        
        // Act
        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        
        // Assert
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        // Verify that validation was called with the correct original resources
        Set<String> capturedOrigResources = validationResourcesCaptor.getValue();
        
        assertNotNull("Original resources should not be null", capturedOrigResources);
        assertEquals("Should validate against original resources extracted from refresh token " + Claims.ORIG_RESOURCES + " claim", 
                    origResources, capturedOrigResources);
        
        // Verify validation service was called (proves the flow reached the validation step)
        assertNotNull("Validation should have been called", validationRequestCaptor.getValue());
    }

    @Test
    public void shouldRejectTokenRequestWhenValidationServiceThrowsInvalidResourceException() {
        // Arrange: Mock validation service to throw InvalidResourceException to test error handling
        String refreshTokenValue = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshTokenValue);
        
        Set<String> origResources = Set.of("https://api.example.com/photos");
        Set<String> requestedResources = Set.of("https://api.example.com/videos"); // NOT in orig_resources
        
        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList("refresh_token"));
        
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId(client.getClientId());
        oAuth2Request.setGrantType(GrantType.REFRESH_TOKEN);
        oAuth2Request.setParameters(parameters);
        oAuth2Request.setResources(requestedResources);
        
        // Create refresh token with orig_resources claim
        Map<String, Object> refreshTokenJWT = new HashMap<>();
        JSONArray origResourcesArray = new JSONArray();
        origResourcesArray.addAll(origResources);
        refreshTokenJWT.put(Claims.ORIG_RESOURCES, origResourcesArray);
        refreshTokenJWT.put("sub", "user-123");
        
        RefreshToken refreshToken = new RefreshToken(refreshTokenValue);
        refreshToken.setAdditionalInformation(refreshTokenJWT);
        
        when(tokenRequest.parameters()).thenReturn(parameters);
        // createOAuth2Request() is not called in the error path, so we don't need to mock it
        when(tokenService.refresh(refreshTokenValue, tokenRequest, client)).thenReturn(Single.just(refreshToken));
        when(resourceConsistencyValidationService.resolveFinalResources(any(OAuth2Request.class), eq(origResources)))
                .thenThrow(new InvalidResourceException("The requested resource is not recognized by this authorization server."));
        // Note: We don't mock tokenService.create() or rulesEngine.fire() since the test should fail before reaching them
        
        // Act
        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        
        // Assert
        testObserver.assertError(InvalidResourceException.class);
    }

    @Test
    public void shouldExtractOrigResourcesFromRefreshTokenJWT_WhenOrigResourcesClaimIsPresent() {
        // Arrange: Test that orig_resources are correctly extracted from refresh token
        String refreshTokenValue = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshTokenValue);
        
        Set<String> origResources = Set.of("https://api.example.com/photos", "https://api.example.com/videos");
        
        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList("refresh_token"));
        
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId(client.getClientId());
        oAuth2Request.setGrantType(GrantType.REFRESH_TOKEN);
        oAuth2Request.setParameters(parameters);
        
        // Create refresh token with orig_resources claim
        Map<String, Object> refreshTokenJWT = new HashMap<>();
        JSONArray origResourcesArray = new JSONArray();
        origResourcesArray.addAll(origResources);
        refreshTokenJWT.put(Claims.ORIG_RESOURCES, origResourcesArray);
        refreshTokenJWT.put("sub", "user-123");
        
        RefreshToken refreshToken = new RefreshToken(refreshTokenValue);
        refreshToken.setAdditionalInformation(refreshTokenJWT);
        
        Token accessToken = new AccessToken("test-token");
        
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> resourcesCaptor = ArgumentCaptor.forClass((Class<Set<String>>) (Class<?>) Set.class);
        
        when(tokenRequest.parameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);
        when(tokenService.refresh(refreshTokenValue, tokenRequest, client)).thenReturn(Single.just(refreshToken));
        when(resourceConsistencyValidationService.resolveFinalResources(any(OAuth2Request.class), resourcesCaptor.capture()))
                .thenReturn(origResources);
        when(tokenService.create(any(), any(), any())).thenReturn(Single.just(accessToken));
        when(rulesEngine.fire(any(), any(), any(), any(), any())).thenReturn(Single.just(executionContext));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));
        
        // Act
        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        
        // Assert
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        // Verify that orig_resources were correctly extracted and passed to validation service
        Set<String> extractedResources = resourcesCaptor.getValue();
        assertNotNull(extractedResources);
        assertEquals(origResources, extractedResources);
    }
}
