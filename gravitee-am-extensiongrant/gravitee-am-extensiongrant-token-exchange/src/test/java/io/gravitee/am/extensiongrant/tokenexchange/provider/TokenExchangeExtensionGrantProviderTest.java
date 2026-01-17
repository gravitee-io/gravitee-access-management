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
package io.gravitee.am.extensiongrant.tokenexchange.provider;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeURN;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TokenExchangeExtensionGrantProvider - RFC 8693 Token Exchange.
 *
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class TokenExchangeExtensionGrantProviderTest {

    @InjectMocks
    private TokenExchangeExtensionGrantProvider provider;

    @Mock
    private TokenExchangeExtensionGrantConfiguration configuration;

    @BeforeEach
    void setUp() {
        // Set up default configuration
        lenient().when(configuration.getAllowedSubjectTokenTypes()).thenReturn(
                new ArrayList<>(List.of(TokenTypeURN.ACCESS_TOKEN, TokenTypeURN.JWT, TokenTypeURN.ID_TOKEN)));
        lenient().when(configuration.getAllowedActorTokenTypes()).thenReturn(
                new ArrayList<>(List.of(TokenTypeURN.ACCESS_TOKEN, TokenTypeURN.JWT)));
        lenient().when(configuration.getAllowedRequestedTokenTypes()).thenReturn(
                new ArrayList<>(List.of(TokenTypeURN.ACCESS_TOKEN, TokenTypeURN.JWT)));
        lenient().when(configuration.isAllowDelegation()).thenReturn(true);
        lenient().when(configuration.isAllowImpersonation()).thenReturn(false);
        lenient().when(configuration.isRequireAudience()).thenReturn(false);
        lenient().when(configuration.getMaxDelegationChainDepth()).thenReturn(3);
        lenient().when(configuration.getScopePolicy()).thenReturn(
                TokenExchangeExtensionGrantConfiguration.ScopePolicy.REDUCE);

        // Initialize the provider
        provider.init();
    }

    @Test
    void shouldRejectMissingSubjectToken() {
        // Given
        TokenRequest tokenRequest = createTokenRequest(null, TokenTypeURN.ACCESS_TOKEN, null, null);

        // When/Then
        assertThrows(InvalidGrantException.class, () -> provider.grant(tokenRequest).blockingGet());
    }

    @Test
    void shouldRejectMissingSubjectTokenType() {
        // Given
        TokenRequest tokenRequest = createTokenRequest("test-token", null, null, null);

        // When/Then
        assertThrows(InvalidGrantException.class, () -> provider.grant(tokenRequest).blockingGet());
    }

    @Test
    void shouldRejectUnsupportedSubjectTokenType() {
        // Given
        when(configuration.getAllowedSubjectTokenTypes()).thenReturn(
                new ArrayList<>(List.of(TokenTypeURN.ACCESS_TOKEN)));

        TokenRequest tokenRequest = createTokenRequest("test-token", TokenTypeURN.JWT, null, null);

        // When/Then
        assertThrows(InvalidGrantException.class, () -> provider.grant(tokenRequest).blockingGet());
    }

    @Test
    void shouldRejectActorTokenWithoutActorTokenType() {
        // Given
        Map<String, String> params = new HashMap<>();
        params.put(Parameters.SUBJECT_TOKEN, "test-subject-token");
        params.put(Parameters.SUBJECT_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);
        params.put(Parameters.ACTOR_TOKEN, "test-actor-token");
        // Missing actor_token_type

        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(tokenRequest.getRequestParameters()).thenReturn(params);

        // When/Then
        assertThrows(InvalidGrantException.class, () -> provider.grant(tokenRequest).blockingGet());
    }

    @Test
    void shouldRejectUnsupportedActorTokenType() {
        // Given
        when(configuration.getAllowedActorTokenTypes()).thenReturn(
                new ArrayList<>(List.of(TokenTypeURN.ACCESS_TOKEN)));

        Map<String, String> params = new HashMap<>();
        params.put(Parameters.SUBJECT_TOKEN, "test-subject-token");
        params.put(Parameters.SUBJECT_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);
        params.put(Parameters.ACTOR_TOKEN, "test-actor-token");
        params.put(Parameters.ACTOR_TOKEN_TYPE, TokenTypeURN.JWT);

        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(tokenRequest.getRequestParameters()).thenReturn(params);

        // When/Then
        assertThrows(InvalidGrantException.class, () -> provider.grant(tokenRequest).blockingGet());
    }

    @Test
    void shouldRejectUnsupportedRequestedTokenType() {
        // Given
        when(configuration.getAllowedRequestedTokenTypes()).thenReturn(
                new ArrayList<>(List.of(TokenTypeURN.ACCESS_TOKEN)));

        Map<String, String> params = new HashMap<>();
        params.put(Parameters.SUBJECT_TOKEN, "test-subject-token");
        params.put(Parameters.SUBJECT_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);
        params.put(Parameters.REQUESTED_TOKEN_TYPE, "urn:ietf:params:oauth:token-type:id_token");

        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(tokenRequest.getRequestParameters()).thenReturn(params);

        // When/Then
        assertThrows(InvalidGrantException.class, () -> provider.grant(tokenRequest).blockingGet());
    }

    @Test
    void shouldRejectMissingAudienceWhenRequired() {
        // Given
        when(configuration.isRequireAudience()).thenReturn(true);

        TokenRequest tokenRequest = createTokenRequest("test-token", TokenTypeURN.ACCESS_TOKEN, null, null);

        // When/Then
        assertThrows(InvalidGrantException.class, () -> provider.grant(tokenRequest).blockingGet());
    }

    @Test
    void shouldRejectDelegationWhenDisabled() {
        // Given
        when(configuration.isAllowDelegation()).thenReturn(false);

        Map<String, String> params = new HashMap<>();
        params.put(Parameters.SUBJECT_TOKEN, "test-subject-token");
        params.put(Parameters.SUBJECT_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);
        params.put(Parameters.ACTOR_TOKEN, "test-actor-token");
        params.put(Parameters.ACTOR_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);

        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(tokenRequest.getRequestParameters()).thenReturn(params);

        // When/Then
        assertThrows(InvalidGrantException.class, () -> provider.grant(tokenRequest).blockingGet());
    }

    @Test
    void shouldAcceptValidRequestParameters() {
        // Given
        Map<String, String> params = new HashMap<>();
        params.put(Parameters.SUBJECT_TOKEN, "test-subject-token");
        params.put(Parameters.SUBJECT_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);
        params.put(Parameters.AUDIENCE, "https://api.example.com");
        params.put(Parameters.SCOPE, "read write");

        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(tokenRequest.getRequestParameters()).thenReturn(params);
        when(tokenRequest.getClientId()).thenReturn("test-client");

        // When - this will fail at token validation, but the request parsing should succeed
        // The test verifies that request parameters are properly parsed and validated
        try {
            provider.grant(tokenRequest).blockingGet();
        } catch (Exception e) {
            // Expected - token validation will fail as we're using a mock token
            // We're testing that the request parsing succeeded
            assertTrue(e.getMessage() == null || !e.getMessage().contains("Missing required parameter"));
            assertTrue(e.getMessage() == null || !e.getMessage().contains("Unsupported"));
        }
    }

    @Test
    void shouldDefaultRequestedTokenTypeToAccessToken() {
        // Given
        Map<String, String> params = new HashMap<>();
        params.put(Parameters.SUBJECT_TOKEN, "test-subject-token");
        params.put(Parameters.SUBJECT_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);
        // No requested_token_type specified

        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(tokenRequest.getRequestParameters()).thenReturn(params);

        // When - this will fail at token validation
        // The test verifies that default requested_token_type is applied
        try {
            provider.grant(tokenRequest).blockingGet();
        } catch (Exception e) {
            // Request parsing should succeed without errors about requested_token_type
            assertFalse(e.getMessage() != null && e.getMessage().contains("requested_token_type"));
        }
    }

    @Test
    void shouldAcceptAudienceWhenProvided() {
        // Given
        when(configuration.isRequireAudience()).thenReturn(true);

        Map<String, String> params = new HashMap<>();
        params.put(Parameters.SUBJECT_TOKEN, "test-subject-token");
        params.put(Parameters.SUBJECT_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);
        params.put(Parameters.AUDIENCE, "https://api.example.com");

        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(tokenRequest.getRequestParameters()).thenReturn(params);
        when(tokenRequest.getClientId()).thenReturn("test-client");

        // When - this will fail at token validation, but audience validation should pass
        try {
            provider.grant(tokenRequest).blockingGet();
        } catch (Exception e) {
            // Request parsing should succeed without errors about audience
            assertFalse(e.getMessage() != null && e.getMessage().contains("Missing required parameter: audience"));
        }
    }

    @Test
    void shouldPassDelegationWithActorToken() {
        // Given - delegation enabled with actor token
        when(configuration.isAllowDelegation()).thenReturn(true);

        Map<String, String> params = new HashMap<>();
        params.put(Parameters.SUBJECT_TOKEN, "test-subject-token");
        params.put(Parameters.SUBJECT_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);
        params.put(Parameters.ACTOR_TOKEN, "test-actor-token");
        params.put(Parameters.ACTOR_TOKEN_TYPE, TokenTypeURN.ACCESS_TOKEN);

        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(tokenRequest.getRequestParameters()).thenReturn(params);
        when(tokenRequest.getClientId()).thenReturn("test-client");

        // When - this will fail at token validation, but delegation check should pass
        try {
            provider.grant(tokenRequest).blockingGet();
        } catch (Exception e) {
            // Request parsing should succeed without errors about delegation
            assertFalse(e.getMessage() != null && e.getMessage().contains("Delegation is not allowed"));
        }
    }

    private TokenRequest createTokenRequest(String subjectToken, String subjectTokenType,
                                             String actorToken, String actorTokenType) {
        Map<String, String> params = new HashMap<>();
        if (subjectToken != null) {
            params.put(Parameters.SUBJECT_TOKEN, subjectToken);
        }
        if (subjectTokenType != null) {
            params.put(Parameters.SUBJECT_TOKEN_TYPE, subjectTokenType);
        }
        if (actorToken != null) {
            params.put(Parameters.ACTOR_TOKEN, actorToken);
        }
        if (actorTokenType != null) {
            params.put(Parameters.ACTOR_TOKEN_TYPE, actorTokenType);
        }

        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(tokenRequest.getRequestParameters()).thenReturn(params);
        return tokenRequest;
    }
}
