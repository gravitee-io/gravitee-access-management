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
package io.gravitee.am.extensiongrant.tokenexchange.delegation;

import io.gravitee.am.extensiongrant.tokenexchange.validation.ValidatedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MayActValidator - RFC 8693 Section 4.4.
 *
 * @author GraviteeSource Team
 */
class MayActValidatorTest {

    private MayActValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MayActValidator();
    }

    @Test
    void shouldAllowDelegationWhenNoMayActClaim() {
        // Given - subject token without may_act claim
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(null)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .clientId("client-123")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(subjectToken, actorToken, null);

        // Then
        assertTrue(result.isValid());
        assertNull(result.getReason());
    }

    @Test
    void shouldAllowDelegationWhenActorMatchesMayActSubject() {
        // Given - may_act allows specific actor
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("sub", "admin@example.com");

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(subjectToken, actorToken, null);

        // Then
        assertTrue(result.isValid());
    }

    @Test
    void shouldDenyDelegationWhenActorDoesNotMatchMayActSubject() {
        // Given - may_act allows different actor
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("sub", "authorized-admin@example.com");

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("unauthorized-admin@example.com")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(subjectToken, actorToken, null);

        // Then
        assertFalse(result.isValid());
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains("not authorized"));
    }

    @Test
    void shouldAllowDelegationWhenActorInMayActSubjectList() {
        // Given - may_act allows list of actors
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("sub", List.of("admin1@example.com", "admin2@example.com", "admin3@example.com"));

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin2@example.com")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(subjectToken, actorToken, null);

        // Then
        assertTrue(result.isValid());
    }

    @Test
    void shouldDenyDelegationWhenActorNotInMayActSubjectList() {
        // Given - may_act allows list of actors, but actor not in list
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("sub", List.of("admin1@example.com", "admin2@example.com"));

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("unauthorized@example.com")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(subjectToken, actorToken, null);

        // Then
        assertFalse(result.isValid());
    }

    @Test
    void shouldAllowDelegationWhenAudienceMatchesMayAct() {
        // Given - may_act specifies audience
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("aud", "https://api.example.com");

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(
                subjectToken, actorToken, "https://api.example.com");

        // Then
        assertTrue(result.isValid());
    }

    @Test
    void shouldDenyDelegationWhenAudienceDoesNotMatchMayAct() {
        // Given - may_act specifies different audience
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("aud", "https://api.example.com");

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(
                subjectToken, actorToken, "https://other-api.example.com");

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("audience"));
    }

    @Test
    void shouldAllowDelegationWhenAudienceInMayActList() {
        // Given - may_act specifies list of audiences
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("aud", List.of("https://api1.example.com", "https://api2.example.com"));

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(
                subjectToken, actorToken, "https://api2.example.com");

        // Then
        assertTrue(result.isValid());
    }

    @Test
    void shouldAllowDelegationWhenClientIdMatchesMayAct() {
        // Given - may_act specifies client_id
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("client_id", "authorized-client");

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .clientId("authorized-client")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(subjectToken, actorToken, null);

        // Then
        assertTrue(result.isValid());
    }

    @Test
    void shouldDenyDelegationWhenClientIdDoesNotMatchMayAct() {
        // Given - may_act specifies different client_id
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("client_id", "authorized-client");

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .clientId("unauthorized-client")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(subjectToken, actorToken, null);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("client"));
    }

    @Test
    void shouldRejectInvalidMayActFormat() {
        // Given - may_act is not a Map
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim("invalid-format")
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When
        MayActValidator.ValidationResult result = validator.validateMayAct(subjectToken, actorToken, null);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Invalid may_act"));
    }

    @Test
    void shouldValidateMultipleConstraints() {
        // Given - may_act with multiple constraints
        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("sub", "admin@example.com");
        mayAct.put("aud", "https://api.example.com");
        mayAct.put("client_id", "admin-client");

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .clientId("admin-client")
                .build();

        // When - all constraints match
        MayActValidator.ValidationResult result = validator.validateMayAct(
                subjectToken, actorToken, "https://api.example.com");

        // Then
        assertTrue(result.isValid());
    }

    @Test
    void shouldDetectMayActRestrictions() {
        // Given - may_act with restrictions
        Map<String, Object> mayActWithRestrictions = new HashMap<>();
        mayActWithRestrictions.put("sub", "admin@example.com");

        Map<String, Object> mayActWithoutRestrictions = new HashMap<>();

        // When/Then
        assertTrue(validator.hasMayActRestrictions(mayActWithRestrictions));
        assertFalse(validator.hasMayActRestrictions(mayActWithoutRestrictions));
        assertFalse(validator.hasMayActRestrictions(null));
        assertFalse(validator.hasMayActRestrictions("not-a-map"));
    }
}
