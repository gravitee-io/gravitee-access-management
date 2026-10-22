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

import com.sun.net.httpserver.HttpServer;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationDetailTypeValidatorTest {

    private static final String TYPE = "payment_initiation";

    private final AuthorizationDetailTypeValidator validator = new AuthorizationDetailTypeValidator();

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"type\":\"object\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

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

    @Test
    void shouldAcceptValidSchema() {
        assertThat(validator.validate(build(TYPE, validSchema("")))).isEmpty();
    }

    @Test
    void shouldAcceptExplicitDraft7() {
        assertThat(validator.validate(build(TYPE, validSchema("\"$schema\": \"http://json-schema.org/draft-07/schema#\","))))
                .isEmpty();
    }

    @Test
    void shouldAcceptKeywordsDraft7DoesNotDefine() {
        assertThat(validator.validate(build(TYPE, validSchema("\"x-masked\": [\"iban\"], \"unevaluatedProperties\": false,"))))
                .isEmpty();
    }

    @Test
    void shouldAcceptLocalReferences() {
        String schema = """
                {
                  "type": "object",
                  "definitions": { "amount": { "type": "string" } },
                  "properties": {
                    "type": { "const": "payment_initiation" },
                    "amount": { "$ref": "#/definitions/amount" },
                    "self": { "$ref": "#" }
                  },
                  "required": ["type"]
                }""";

        assertThat(validator.validate(build(TYPE, schema))).isEmpty();
    }

    @Test
    void shouldRejectTooLongSchema() {
        String padding = " ".repeat(AuthorizationDetailType.SCHEMA_MAX_LENGTH);

        assertThat(validator.validate(build(TYPE, validSchema("") + padding)))
                .contains("Schema must be at most 65536 characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "not json", "{\"type\": \"object\"} trailing", "{\"type\": \"object\", \"type\": \"string\"}"})
    void shouldRejectInvalidJson(String schema) {
        assertThat(validator.validate(build(TYPE, schema))).contains("Schema is not valid JSON");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"object\"", "true", "42", "null", ""})
    void shouldRejectNonObjectJson(String schema) {
        assertThat(validator.validate(build(TYPE, schema))).contains("Schema must be a JSON object");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://json-schema.org/draft/2020-12/schema",
            "http://json-schema.org/draft-04/schema#",
            "http://json-schema.org/draft-07/schema",
            "https://json-schema.org/draft-07/schema#"})
    void shouldRejectOtherDialects(String dialect) {
        assertThat(validator.validate(build(TYPE, validSchema("\"$schema\": \"" + dialect + "\","))))
                .contains("Schema must use JSON Schema Draft 7 ($schema must be absent or \"http://json-schema.org/draft-07/schema#\")");
    }

    @Test
    void shouldRejectDialectThatIsNotAString() {
        assertThat(validator.validate(build(TYPE, validSchema("\"$schema\": 7,"))))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema must use JSON Schema Draft 7"));
    }

    @Test
    void shouldRejectSchemaFailingTheMetaSchema() {
        assertThat(validator.validate(build(TYPE, validSchema("\"minProperties\": -1,"))))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema is not a valid JSON Schema Draft 7: "));
        assertThat(validator.validate(build(TYPE, validSchema("\"additionalProperties\": \"no\","))))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema is not a valid JSON Schema Draft 7: "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"properties\": {\"type\": {\"const\": \"payment_initiation\"}}, \"required\": [\"type\"]}",
            "{\"type\": \"string\"}",
            "{\"type\": [\"object\"], \"properties\": {\"type\": {\"const\": \"payment_initiation\"}}, \"required\": [\"type\"]}"})
    void shouldRejectRootThatIsNotAnObject(String schema) {
        assertThat(validator.validate(build(TYPE, schema))).contains("Schema must declare \"type\": \"object\" at its root");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\": \"object\"}",
            "{\"type\": \"object\", \"properties\": {\"type\": {\"const\": \"payment_initiation\"}}}",
            "{\"type\": \"object\", \"properties\": {\"type\": {\"type\": \"string\"}}, \"required\": [\"type\"]}",
            "{\"type\": \"object\", \"properties\": {\"type\": {\"const\": \"Payment_Initiation\"}}, \"required\": [\"type\"]}",
            "{\"type\": \"object\", \"properties\": {\"type\": {\"const\": \"other\"}}, \"required\": [\"type\"]}",
            "{\"type\": \"object\", \"properties\": {\"type\": {\"const\": \"payment_initiation\"}}, \"required\": [\"amount\"]}"})
    void shouldRejectSchemaNotConstrainingType(String schema) {
        assertThat(validator.validate(build(TYPE, schema))).contains("Schema must constrain \"type\" to \"payment_initiation\": "
                + "its root must declare properties.type.const equal to the type and list \"type\" in required");
    }

    @Test
    void shouldRejectRemoteReferenceWithoutFetchingIt() {
        String schema = validSchema("\"definitions\": {\"nested\": {\"items\": [{\"$ref\": \"" + serverUrl() + "/remote.json\"}]}},");

        assertThat(validator.validate(build(TYPE, schema)))
                .contains("Schema must only contain local references (starting with \"#\"), found \"" + serverUrl() + "/remote.json\"");
        assertThat(requests).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"other.json", "other.json#/definitions/a", "file:///etc/passwd", "classpath:draft-07/schema", "urn:example:a"})
    void shouldRejectNonLocalReference(String ref) {
        assertThat(validator.validate(build(TYPE, validSchema("\"definitions\": {\"a\": {\"$ref\": \"" + ref + "\"}},"))))
                .contains("Schema must only contain local references (starting with \"#\"), found \"" + ref + "\"");
    }

    @Test
    void shouldNotFetchRemoteDialect() {
        assertThat(validator.validate(build(TYPE, validSchema("\"$schema\": \"" + serverUrl() + "/schema#\","))))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema must use JSON Schema Draft 7"));
        assertThat(requests).hasValue(0);
    }

    @Test
    void shouldNotFetchRemoteIdentifier() {
        assertThat(validator.validate(build(TYPE, validSchema("\"$id\": \"" + serverUrl() + "/base.json\","))))
                .isEmpty();
        assertThat(requests).hasValue(0);
    }

    @Test
    void shouldRejectRootReferenceBesideThePinnedType() {
        String schema = "{\"type\":\"object\",\"properties\":{\"type\":{\"const\":\"payment_initiation\"}},\"required\":[\"type\"],"
                + "\"$ref\":\"#/definitions/loose\",\"definitions\":{\"loose\":{}}}";

        assertThat(validator.validate(build(TYPE, schema)))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema must not combine \"$ref\" with other keywords")
                        .endsWith(" at #"));
    }

    @Test
    void shouldRejectReferenceBesideTheTypeConstant() {
        String schema = "{\"type\":\"object\",\"properties\":{\"type\":{\"const\":\"payment_initiation\",\"$ref\":\"#/definitions/loose\"}},"
                + "\"required\":[\"type\"],\"definitions\":{\"loose\":{}}}";

        assertThat(validator.validate(build(TYPE, schema)))
                .hasValueSatisfying(error -> assertThat(error).endsWith(" at #/properties/type"));
    }

    @Test
    void shouldAcceptReferenceWithComment() {
        assertThat(validator.validate(build(TYPE, validSchema(
                "\"definitions\": {\"amount\": {\"type\": \"string\"}, \"alias\": {\"$ref\": \"#/definitions/amount\", \"$comment\": \"same\"}},"))))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"definitions\": {\"a\": {\"$ref\": \"#/definitions/b\"}, \"b\": {\"$ref\": \"#/definitions/a\"}},",
            "\"definitions\": {\"a\": {\"$ref\": \"#/definitions/a\"}},",
            "\"allOf\": [{\"$ref\": \"#\"}],",
            "\"definitions\": {\"a\": {\"not\": {\"$ref\": \"#/definitions/a\"}}},",
            "\"definitions\": {\"a\": {\"if\": {\"anyOf\": [{\"$ref\": \"#/definitions/a\"}]}}},",
            "\"dependencies\": {\"amount\": {\"oneOf\": [{\"$ref\": \"#\"}]}},"})
    void shouldRejectSchemaApplyingItselfWithoutEnd(String extraMembers) {
        assertThat(validator.validate(build(TYPE, validSchema(extraMembers))))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema must not apply itself to the same value without end, through #"));
    }

    @Test
    void shouldAcceptRecursionIntoTheValue() {
        String schema = "{\"type\":\"object\",\"properties\":{\"type\":{\"const\":\"payment_initiation\"},"
                + "\"children\":{\"type\":\"array\",\"items\":{\"$ref\":\"#\"}}},\"required\":[\"type\"]}";

        assertThat(validator.validate(build(TYPE, schema))).isEmpty();
        assertThat(SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schema)
                .validate("{\"type\":\"payment_initiation\",\"children\":[{\"type\":\"payment_initiation\"}]}", InputFormat.JSON))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#/definitions/missing", "#/properties/type/const", "#anchor", "#/definitions/amount/type"})
    void shouldRejectReferenceThatIsNotASubschema(String ref) {
        assertThat(validator.validate(build(TYPE, validSchema(
                "\"definitions\": {\"amount\": {\"type\": \"string\"}, \"a\": {\"$ref\": \"" + ref + "\"}},"))))
                .contains("Schema reference \"" + ref + "\" does not resolve to a subschema");
    }

    @Test
    void shouldResolveEscapedPointers() {
        assertThat(validator.validate(build(TYPE, validSchema("\"definitions\": {\"a/b\": {\"type\": \"string\"}, \"c~d\": {\"type\": \"string\"}, "
                + "\"e f\": {\"type\": \"string\"}, \"x\": {\"allOf\": [{\"$ref\": \"#/definitions/a~1b\"}, {\"$ref\": \"#/definitions/c~0d\"}, "
                + "{\"$ref\": \"#/definitions/e%20f\"}]}},"))))
                .isEmpty();
    }

    @Test
    void shouldRejectIdentifierBelowTheRoot() {
        assertThat(validator.validate(build(TYPE, validSchema("\"definitions\": {\"a\": {\"$id\": \"#a\", \"type\": \"string\"}},"))))
                .contains("Schema must not declare \"$id\" below its root, found at #/definitions/a");
    }

    @Test
    void shouldTreatConstantsAsData() {
        assertThat(validator.validate(build(TYPE, validSchema(
                "\"definitions\": {\"a\": {\"const\": {\"$ref\": \"https://example.org/remote\", \"$id\": \"x\"}}},"))))
                .isEmpty();
    }

    private String serverUrl() {
        return "http://localhost:" + server.getAddress().getPort();
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
