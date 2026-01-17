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
package io.gravitee.am.extensiongrant.tokenexchange.validation;

import io.gravitee.am.common.oauth2.TokenTypeURN;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SubjectTokenValidatorFactory - RFC 8693 token validation.
 *
 * @author GraviteeSource Team
 */
class SubjectTokenValidatorFactoryTest {

    private SubjectTokenValidatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SubjectTokenValidatorFactory();
    }

    @Test
    void shouldRegisterDefaultValidators() {
        // When
        Set<String> supportedTypes = factory.getSupportedTokenTypes();

        // Then
        assertTrue(supportedTypes.contains(TokenTypeURN.JWT));
        assertTrue(supportedTypes.contains(TokenTypeURN.ID_TOKEN));
        assertTrue(supportedTypes.contains(TokenTypeURN.ACCESS_TOKEN));
        assertTrue(supportedTypes.contains(TokenTypeURN.REFRESH_TOKEN));
        assertTrue(supportedTypes.contains(TokenTypeURN.SAML1));
        assertTrue(supportedTypes.contains(TokenTypeURN.SAML2));
        assertEquals(6, supportedTypes.size());
    }

    @Test
    void shouldReturnJwtValidator() throws InvalidGrantException {
        // When
        SubjectTokenValidator validator = factory.getValidator(TokenTypeURN.JWT);

        // Then
        assertNotNull(validator);
        assertTrue(validator instanceof JwtSubjectTokenValidator);
    }

    @Test
    void shouldReturnIdTokenValidator() throws InvalidGrantException {
        // When
        SubjectTokenValidator validator = factory.getValidator(TokenTypeURN.ID_TOKEN);

        // Then
        assertNotNull(validator);
        assertTrue(validator instanceof JwtSubjectTokenValidator);
    }

    @Test
    void shouldReturnAccessTokenValidator() throws InvalidGrantException {
        // When
        SubjectTokenValidator validator = factory.getValidator(TokenTypeURN.ACCESS_TOKEN);

        // Then
        assertNotNull(validator);
        assertTrue(validator instanceof AccessTokenValidator);
    }

    @Test
    void shouldThrowForUnsupportedTokenType() {
        // When/Then
        InvalidGrantException exception = assertThrows(InvalidGrantException.class, () ->
                factory.getValidator("urn:unsupported:token:type"));

        assertTrue(exception.getMessage().contains("Unsupported token type"));
    }

    @Test
    void shouldCheckValidatorAvailability() {
        // When/Then
        assertTrue(factory.hasValidator(TokenTypeURN.JWT));
        assertTrue(factory.hasValidator(TokenTypeURN.ID_TOKEN));
        assertTrue(factory.hasValidator(TokenTypeURN.ACCESS_TOKEN));
        assertTrue(factory.hasValidator(TokenTypeURN.REFRESH_TOKEN));
        assertTrue(factory.hasValidator(TokenTypeURN.SAML1));
        assertTrue(factory.hasValidator(TokenTypeURN.SAML2));
        assertFalse(factory.hasValidator("urn:unsupported:token:type"));
    }

    @Test
    void shouldRegisterCustomValidator() throws InvalidGrantException {
        // Given
        String customTokenType = "urn:custom:token:type";
        SubjectTokenValidator customValidator = new SubjectTokenValidator() {
            @Override
            public Single<ValidatedToken> validate(
                    String token,
                    TokenExchangeExtensionGrantConfiguration config) {
                return Single.just(ValidatedToken.builder()
                        .subject("custom-subject")
                        .build());
            }

            @Override
            public String getSupportedTokenType() {
                return TokenTypeURN.ACCESS_TOKEN;
            }

            @Override
            public boolean supports(String tokenType) {
                return customTokenType.equals(tokenType);
            }
        };

        // When
        factory.registerValidator(customTokenType, customValidator);

        // Then
        assertTrue(factory.hasValidator(customTokenType));
        SubjectTokenValidator retrieved = factory.getValidator(customTokenType);
        assertNotNull(retrieved);
        assertEquals(customValidator, retrieved);
    }

    @Test
    void shouldFindValidatorBySupportMethod() throws InvalidGrantException {
        // The factory should be able to find validators that support a token type
        // even if they're not registered directly under that key

        // JWT validator supports both JWT and related token types
        SubjectTokenValidator validator = factory.getValidator(TokenTypeURN.JWT);
        assertNotNull(validator);
    }

    @Test
    void shouldReturnSupportedTokenTypes() {
        // When
        Set<String> supportedTypes = factory.getSupportedTokenTypes();

        // Then
        assertNotNull(supportedTypes);
        assertFalse(supportedTypes.isEmpty());
    }

    @Test
    void shouldExposeSamlValidators() {
        assertTrue(factory.hasValidator(TokenTypeURN.SAML2));
        assertTrue(factory.hasValidator(TokenTypeURN.SAML1));
    }

    @Test
    void shouldValidateSaml2Assertion() throws InvalidGrantException {
        String assertion = encode(
                "<saml2:Assertion xmlns:saml2='urn:oasis:names:tc:SAML:2.0:assertion' ID='_id123' IssueInstant='2024-01-01T00:00:00Z'>" +
                "<saml2:Issuer>https://issuer.example.com</saml2:Issuer>" +
                "<saml2:Subject><saml2:NameID>user@example.com</saml2:NameID></saml2:Subject>" +
                "<saml2:Conditions NotBefore='2023-01-01T00:00:00Z' NotOnOrAfter='2999-01-01T00:00:00Z'>" +
                "<saml2:AudienceRestriction><saml2:Audience>https://api.example.com</saml2:Audience></saml2:AudienceRestriction>" +
                "</saml2:Conditions></saml2:Assertion>");
        TokenExchangeExtensionGrantConfiguration configuration = new TokenExchangeExtensionGrantConfiguration();
        configuration.setTrustedIssuers(Set.of("https://issuer.example.com"));

        SubjectTokenValidator validator = factory.getValidator(TokenTypeURN.SAML2);
        ValidatedToken validatedToken = validator.validate(assertion, configuration).blockingGet();

        assertEquals("user@example.com", validatedToken.getSubject());
        assertEquals("https://issuer.example.com", validatedToken.getIssuer());
        assertTrue(validatedToken.getAudience().contains("https://api.example.com"));
    }

    @Test
    void shouldValidateSaml1Assertion() throws InvalidGrantException {
        String assertion = encode(
                "<saml1:Assertion xmlns:saml1='urn:oasis:names:tc:SAML:1.0:assertion' Issuer='https://issuer.example.com'>" +
                "<saml1:Subject><saml1:NameIdentifier>user@example.com</saml1:NameIdentifier></saml1:Subject>" +
                "<saml1:Conditions NotBefore='2023-01-01T00:00:00Z' NotOnOrAfter='2999-01-01T00:00:00Z'>" +
                "<saml1:AudienceRestrictionCondition><saml1:Audience>https://legacy-api.example.com</saml1:Audience></saml1:AudienceRestrictionCondition>" +
                "</saml1:Conditions></saml1:Assertion>");
        TokenExchangeExtensionGrantConfiguration configuration = new TokenExchangeExtensionGrantConfiguration();
        configuration.setTrustedIssuers(Set.of("https://issuer.example.com"));

        SubjectTokenValidator validator = factory.getValidator(TokenTypeURN.SAML1);
        ValidatedToken validatedToken = validator.validate(assertion, configuration).blockingGet();

        assertEquals("user@example.com", validatedToken.getSubject());
        assertEquals("https://issuer.example.com", validatedToken.getIssuer());
        assertTrue(validatedToken.getAudience().contains("https://legacy-api.example.com"));
    }

    private static String encode(String xml) {
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }
}
