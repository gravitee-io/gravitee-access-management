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
package io.gravitee.am.service.validators;

import io.gravitee.am.model.application.TokenExchangeClaimMapping;
import io.gravitee.am.model.application.TokenExchangeClaimSource;
import io.gravitee.am.service.validators.tokenexchange.TokenExchangeClaimsMapperValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenExchangeClaimsMapperValidatorTest {

    private final TokenExchangeClaimsMapperValidator validator = new TokenExchangeClaimsMapperValidator();

    @Test
    void shouldAcceptNullMappings() {
        assertThat(validator.validate(null).isInvalid()).isFalse();
    }

    @Test
    void shouldAcceptEmptyMappings() {
        assertThat(validator.validate(List.of()).isInvalid()).isFalse();
    }

    @Test
    void shouldAcceptOrdinaryClaimNames() {
        var result = validator.validate(List.of(
                mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "claim_id", "claim_id"),
                mapping(TokenExchangeClaimSource.ACTOR_TOKEN, "email", "actor_email")));

        assertThat(result.isInvalid()).isFalse();
        assertThat(result.invalidClaims()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gis", "sub", "iss", "aud", "exp", "iat", "nbf", "jti", "act", "client_id", "scope",
            "auth_time", "nonce", "acr", "amr", "azp", "client_profile", "sub_profile",
            "cnf", "domain", "updated_at", "claims_request_parameter", "ip_address", "user_agent",
            "permissions", "authorization_details"})
    void shouldRejectReservedTargetClaim(String reserved) {
        var result = validator.validate(List.of(mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "claim_id", reserved)));

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.invalidClaims()).containsExactly(reserved);
    }

    @Test
    void shouldRejectReservedTargetClaimWithSurroundingWhitespace() {
        var result = validator.validate(List.of(mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "claim_id", "  sub  ")));

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.invalidClaims()).containsExactly("sub");
    }

    @Test
    void shouldReportOnlyTheInvalidMappings() {
        var result = validator.validate(List.of(
                mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "claim_id", "claim_id"),
                mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "tenant", "iss"),
                mapping(TokenExchangeClaimSource.ACTOR_TOKEN, "email", "actor_email")));

        assertThat(result.invalidClaims()).containsExactly("iss");
    }

    @Test
    void shouldIgnoreMappingWithoutTargetClaim() {
        var result = validator.validate(List.of(mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "claim_id", null)));

        assertThat(result.isInvalid()).isFalse();
    }

    @Test
    void shouldIgnoreMappingWithBlankTargetClaim() {
        var result = validator.validate(List.of(mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "claim_id", "   ")));

        assertThat(result.isInvalid()).isFalse();
        assertThat(result.invalidClaims()).isEmpty();
    }

    @Test
    void shouldRejectTwoMappingsOntoTheSameTargetClaim() {
        var result = validator.validate(List.of(
                mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "claim_id", "business_id"),
                mapping(TokenExchangeClaimSource.ACTOR_TOKEN, "agent_id", "business_id")));

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.duplicateClaims()).containsExactly("business_id");
        assertThat(result.invalidClaims()).isEmpty();
    }

    @Test
    void shouldReportADuplicatedTargetClaimOnce() {
        var result = validator.validate(List.of(
                mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "a", "business_id"),
                mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "b", "business_id"),
                mapping(TokenExchangeClaimSource.ACTOR_TOKEN, "c", "business_id")));

        assertThat(result.duplicateClaims()).containsExactly("business_id");
    }

    @Test
    void shouldTreatTargetClaimsDifferingOnlyByWhitespaceAsDuplicates() {
        var result = validator.validate(List.of(
                mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "a", "business_id"),
                mapping(TokenExchangeClaimSource.ACTOR_TOKEN, "b", "  business_id  ")));

        assertThat(result.duplicateClaims()).containsExactly("business_id");
    }

    @Test
    void shouldDescribeAReservedTargetClaim() {
        var result = validator.validate(List.of(mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "claim_id", "sub")));

        assertThat(result.describe()).isEqualTo("Invalid token exchange claim mappings: [sub]");
    }

    @Test
    void shouldDescribeADuplicateTargetClaim() {
        var result = validator.validate(List.of(
                mapping(TokenExchangeClaimSource.SUBJECT_TOKEN, "a", "business_id"),
                mapping(TokenExchangeClaimSource.ACTOR_TOKEN, "b", "business_id")));

        assertThat(result.describe()).isEqualTo("Duplicate token exchange claim mappings: [business_id]");
    }

    private static TokenExchangeClaimMapping mapping(TokenExchangeClaimSource source, String sourceClaim, String tokenClaim) {
        TokenExchangeClaimMapping m = new TokenExchangeClaimMapping();
        m.setSource(source);
        m.setSourceClaim(sourceClaim);
        m.setTokenClaim(tokenClaim);
        return m;
    }
}
