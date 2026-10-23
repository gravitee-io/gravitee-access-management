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
package io.gravitee.am.service.validators.authorizationdetailtype;

import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationDetailTypeValidatorTest {

    private static final String TYPE = "payment_initiation";

    private final AuthorizationDetailTypeValidator validator = new AuthorizationDetailTypeValidator();

    @ParameterizedTest
    @ValueSource(strings = {"payment_initiation", "https://scheme.example.org/files", "!~", "a"})
    void shouldAcceptType(String type) {
        assertThat(validator.validate(build(type, null))).isEmpty();
    }

    @Test
    void shouldAcceptTypeOfMaxLength() {
        assertThat(validator.validate(build("a".repeat(AuthorizationDetailType.TYPE_MAX_LENGTH), null))).isEmpty();
    }

    @Test
    void shouldRejectMissingOrEmptyType() {
        assertThat(validator.validate(build(null, null))).contains("Type is required");
        assertThat(validator.validate(build("", null))).contains("Type is required");
    }

    @Test
    void shouldRejectTooLongType() {
        assertThat(validator.validate(build("a".repeat(AuthorizationDetailType.TYPE_MAX_LENGTH + 1), null)))
                .contains("Type must be at most 512 characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment initiation", " payment", "payment ", "pay\tment", "pay\u0000ment", "pay\u007Fment", "paiement_initié", " "})
    void shouldRejectTypeOutsideVisibleAscii(String type) {
        assertThat(validator.validate(build(type, null))).contains("Type must contain only visible ASCII characters (%x21-7E)");
    }

    @Test
    void shouldRejectMissingOrBlankName() {
        AuthorizationDetailType missing = build(TYPE, null);
        missing.setName(null);
        AuthorizationDetailType blank = build(TYPE, null);
        blank.setName("  ");

        assertThat(validator.validate(missing)).contains("Name is required");
        assertThat(validator.validate(blank)).contains("Name is required");
    }

    @Test
    void shouldRejectTooLongName() {
        AuthorizationDetailType type = build(TYPE, null);
        type.setName("n".repeat(AuthorizationDetailType.NAME_MAX_LENGTH + 1));

        assertThat(validator.validate(type)).contains("Name must be at most 255 characters");
    }

    @Test
    void shouldRejectTooLongDescription() {
        AuthorizationDetailType type = build(TYPE, null);
        type.setDescription("d".repeat(AuthorizationDetailType.DESCRIPTION_MAX_LENGTH + 1));

        assertThat(validator.validate(type)).contains("Description must be at most 1024 characters");
    }

    @Test
    void shouldAcceptDescriptionOfMaxLength() {
        AuthorizationDetailType type = build(TYPE, null);
        type.setDescription("d".repeat(AuthorizationDetailType.DESCRIPTION_MAX_LENGTH));

        assertThat(validator.validate(type)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void shouldRejectExpiresInBelowOne(int expiresIn) {
        AuthorizationDetailType type = build(TYPE, null);
        type.setExpiresIn(expiresIn);

        assertThat(validator.validate(type)).contains("Expires in must be greater than 0");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3600, Integer.MAX_VALUE})
    void shouldAcceptPositiveExpiresIn(int expiresIn) {
        AuthorizationDetailType type = build(TYPE, null);
        type.setExpiresIn(expiresIn);

        assertThat(validator.validate(type)).isEmpty();
    }

    @Test
    void shouldAcceptMissingExpiresIn() {
        assertThat(validator.validate(build(TYPE, null))).isEmpty();
    }

    @Test
    void shouldAcceptValidSchema() {
        assertThat(validator.validate(build(TYPE, validSchema("")))).isEmpty();
    }

    @Test
    void shouldRejectInvalidSchema() {
        assertThat(validator.validate(build(TYPE, "not json"))).contains("Schema is not valid JSON");
    }

    @Test
    void shouldCheckFieldsBeforeTheSchema() {
        assertThat(validator.validate(build("", "not json"))).contains("Type is required");
    }

    private static String validSchema(String extraMembers) {
        return "{" + extraMembers + " \"type\": \"object\", \"properties\": {\"type\": {\"const\": \"payment_initiation\"}, "
                + "\"amount\": {\"type\": \"string\"}}, \"required\": [\"type\"]}";
    }

    private static AuthorizationDetailType build(String type, String schema) {
        return AuthorizationDetailType.builder()
                .domain("domain-1")
                .type(type)
                .name("Payment initiation")
                .schema(schema)
                .build();
    }
}
