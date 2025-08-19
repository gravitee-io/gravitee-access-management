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
package io.gravitee.am.extensiongrant.jwtbearer.provider;

import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.MalformedJWTException;
import io.gravitee.am.common.exception.jwt.PrematureJWTException;
import io.gravitee.am.common.exception.jwt.SignatureException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.jwtbearer.OpenIDJWTBearerExtensionGrantConfiguration;
import io.gravitee.am.extensiongrant.jwtbearer.parser.JWKSJwtParser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.jwt.Claims.GIO_INTERNAL_SUB;
import static io.gravitee.am.common.jwt.Claims.SUB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenIDJWTBearerExtensionGrantProviderTest {

    @Mock
    private JWKSJwtParser jwtParser;

    private OpenIDJWTBearerExtensionGrantProvider provider;
    private OpenIDJWTBearerExtensionGrantConfiguration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new OpenIDJWTBearerExtensionGrantConfiguration();
        configuration.setJwksUri("https://localhost/jwks.json");
        configuration.setClaimsMapper(List.of());
        provider = new OpenIDJWTBearerExtensionGrantProvider(configuration);

        // Injection du mock jwtParser via reflection
        Field jwtParserField = OpenIDJWTBearerExtensionGrantProvider.class.getDeclaredField("jwtParser");
        jwtParserField.setAccessible(true);
        jwtParserField.set(provider, jwtParser);
    }

    @Test
    void should_throw_invalid_grant_when_assertion_is_missing() {
        // Given
        TokenRequest tokenRequest = createTokenRequest(null);

        // When & Then
        InvalidGrantException exception = assertThrows(InvalidGrantException.class,
                () -> provider.grant(tokenRequest));
        assertEquals("Assertion value is missing", exception.getMessage());
    }

    @Test
    void should_grant_successfully_with_valid_jwt() {
        // Given
        String assertion = "valid.jwt.token";
        TokenRequest tokenRequest = createTokenRequest(assertion);
        JWT jwt = createJWT("user123", "john_doe", null);

        when(jwtParser.parse(assertion)).thenReturn(jwt);

        // When
        TestObserver<User> testObserver = provider.grant(tokenRequest).test();

        // Then
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);

        User user = testObserver.values().get(0);
        assertEquals("john_doe", user.getUsername());
        assertEquals("user123", user.getId());
        assertEquals("user123", user.getAdditionalInformation().get(SUB));
    }

    @Test
    void should_use_sub_as_username_when_preferred_username_missing() {
        // Given
        String assertion = "valid.jwt.token";
        TokenRequest tokenRequest = createTokenRequest(assertion);
        JWT jwt = createJWT("user123", null, null);

        when(jwtParser.parse(assertion)).thenReturn(jwt);

        // When
        TestObserver<User> testObserver = provider.grant(tokenRequest).test();

        // Then
        testObserver.assertComplete();
        User user = testObserver.values().get(0);
        assertEquals("user123", user.getUsername()); // sub utilisé comme username
    }

    @Test
    void should_include_internal_sub_when_present() {
        // Given
        String assertion = "valid.jwt.token";
        TokenRequest tokenRequest = createTokenRequest(assertion);
        JWT jwt = createJWT("user123", "john_doe", "internal_123");

        when(jwtParser.parse(assertion)).thenReturn(jwt);

        // When
        TestObserver<User> testObserver = provider.grant(tokenRequest).test();

        // Then
        testObserver.assertComplete();
        User user = testObserver.values().get(0);
        assertEquals("internal_123", user.getAdditionalInformation().get(GIO_INTERNAL_SUB));
    }

    @Test
    void should_map_claims_according_to_configuration() {
        // Given
        String assertion = "valid.jwt.token";
        TokenRequest tokenRequest = createTokenRequest(assertion);

        // Configuration avec mapping de claims
        List<Map<String, String>> claimsMapper = List.of(
                Map.of("assertion_claim", "email", "token_claim", "user_email"),
                Map.of("assertion_claim", "role", "token_claim", "user_role")
        );
        configuration.setClaimsMapper(claimsMapper);

        JWT jwt = createJWTWithClaims("user123", "john_doe", null,
                Map.of("email", "john@example.com", "role", "admin"));

        when(jwtParser.parse(assertion)).thenReturn(jwt);

        // When
        TestObserver<User> testObserver = provider.grant(tokenRequest).test();

        // Then
        testObserver.assertComplete();
        User user = testObserver.values().get(0);
        assertEquals("john@example.com", user.getAdditionalInformation().get("user_email"));
        assertEquals("admin", user.getAdditionalInformation().get("user_role"));
    }

    @Test
    void should_handle_malformed_jwt_exception() {
        // Given
        String assertion = "invalid.jwt.token";
        TokenRequest tokenRequest = createTokenRequest(assertion);

        when(jwtParser.parse(assertion)).thenThrow(new MalformedJWTException("Invalid JWT format"));

        // When
        TestObserver<User> testObserver = provider.grant(tokenRequest).test();

        // Then
        testObserver.assertError(InvalidGrantException.class);
    }

    @Test
    void should_handle_expired_jwt_exception() {
        // Given
        String assertion = "expired.jwt.token";
        TokenRequest tokenRequest = createTokenRequest(assertion);

        when(jwtParser.parse(assertion)).thenThrow(new ExpiredJWTException("JWT has expired"));

        // When
        TestObserver<User> testObserver = provider.grant(tokenRequest).test();

        // Then
        testObserver.assertError(InvalidGrantException.class);
    }

    @Test
    void should_handle_premature_jwt_exception() {
        // Given
        String assertion = "premature.jwt.token";
        TokenRequest tokenRequest = createTokenRequest(assertion);

        when(jwtParser.parse(assertion)).thenThrow(new PrematureJWTException("JWT not yet valid"));

        // When
        TestObserver<User> testObserver = provider.grant(tokenRequest).test();

        // Then
        testObserver.assertError(InvalidGrantException.class);
    }

    @Test
    void should_handle_signature_exception() {
        // Given
        String assertion = "invalid.signature.token";
        TokenRequest tokenRequest = createTokenRequest(assertion);

        when(jwtParser.parse(assertion)).thenThrow(new SignatureException("Invalid signature"));

        // When
        TestObserver<User> testObserver = provider.grant(tokenRequest).test();

        // Then
        testObserver.assertError(InvalidGrantException.class);
    }

    @Test
    void should_handle_generic_exception() {
        // Given
        String assertion = "problematic.jwt.token";
        TokenRequest tokenRequest = createTokenRequest(assertion);

        when(jwtParser.parse(assertion)).thenThrow(new RuntimeException("Unexpected error"));

        // When
        TestObserver<User> testObserver = provider.grant(tokenRequest).test();

        // Then
        testObserver.assertError(InvalidGrantException.class);
    }

    private TokenRequest createTokenRequest(String assertion) {
        TokenRequest tokenRequest = new TokenRequest();
        Map<String, String> parameters = new HashMap<>();
        if (assertion != null) {
            parameters.put("assertion", assertion);
        }
        tokenRequest.setRequestParameters(parameters);
        return tokenRequest;
    }

    private JWT createJWT(String sub, String preferredUsername, String internalSub) {
        return createJWTWithClaims(sub, preferredUsername, internalSub, Map.of());
    }

    private JWT createJWTWithClaims(String sub, String preferredUsername, String internalSub, Map<String, Object> additionalClaims) {
        JWT jwt = new JWT();
        jwt.setSub(sub);
        jwt.setInternalSub(internalSub);

        if (preferredUsername != null) {
            jwt.put(StandardClaims.PREFERRED_USERNAME, preferredUsername);
        }

        jwt.putAll(additionalClaims);

        return jwt;
    }
}