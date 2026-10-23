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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationDetailTypeSchemaValidatorTest {

    private static final String TYPE = "payment_initiation";

    private final AuthorizationDetailTypeSchemaValidator validator = new AuthorizationDetailTypeSchemaValidator();

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

    @Test
    void shouldAcceptValidSchema() {
        assertThat(validator.validate(TYPE, validSchema(""))).isEmpty();
    }

    @Test
    void shouldAcceptExplicitDialect() {
        assertThat(validator.validate(TYPE, validSchema("\"$schema\": \"https://json-schema.org/draft/2020-12/schema\",")))
                .isEmpty();
    }

    @Test
    void shouldAcceptKeywords2020_12DoesNotDefine() {
        assertThat(validator.validate(TYPE, validSchema("\"x-masked\": [\"iban\"], \"$defs\": {\"a\": {\"x-label\": \"A\"}},")))
                .isEmpty();
    }

    @Test
    void shouldAccept2020_12Keywords() {
        assertThat(validator.validate(TYPE, validSchema("\"unevaluatedProperties\": false, \"dependentRequired\": {\"amount\": [\"currency\"]}, "
                + "\"dependentSchemas\": {\"amount\": {\"required\": [\"currency\"]}}, "
                + "\"$defs\": {\"pair\": {\"type\": \"array\", \"prefixItems\": [{\"type\": \"string\"}], \"unevaluatedItems\": false}},")))
                .isEmpty();
    }

    @Test
    void shouldAcceptLocalReferences() {
        String schema = """
                {
                  "type": "object",
                  "$defs": { "amount": { "type": "string" } },
                  "properties": {
                    "type": { "const": "payment_initiation" },
                    "amount": { "$ref": "#/$defs/amount" },
                    "self": { "$ref": "#" }
                  },
                  "required": ["type"]
                }""";

        assertThat(validator.validate(TYPE, schema)).isEmpty();
    }

    @Test
    void shouldRejectTooLongSchema() {
        String padding = " ".repeat(AuthorizationDetailType.SCHEMA_MAX_LENGTH);

        assertThat(validator.validate(TYPE, validSchema("") + padding))
                .contains("Schema must be at most 65536 characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "not json", "{\"type\": \"object\"} trailing", "{\"type\": \"object\", \"type\": \"string\"}"})
    void shouldRejectInvalidJson(String schema) {
        assertThat(validator.validate(TYPE, schema)).contains("Schema is not valid JSON");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"object\"", "true", "42", "null", ""})
    void shouldRejectNonObjectJson(String schema) {
        assertThat(validator.validate(TYPE, schema)).contains("Schema must be a JSON object");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://json-schema.org/draft/2020-12/schema#",
            "https://json-schema.org/draft/2019-09/schema",
            "http://json-schema.org/draft-07/schema#",
            "http://json-schema.org/draft-04/schema#"})
    void shouldRejectOtherDialects(String dialect) {
        assertThat(validator.validate(TYPE, validSchema("\"$schema\": \"" + dialect + "\",")))
                .contains("Schema must use JSON Schema 2020-12 ($schema must be absent or \"https://json-schema.org/draft/2020-12/schema\"), found at #/$schema");
    }

    @Test
    void shouldRejectDialectThatIsNotAString() {
        assertThat(validator.validate(TYPE, validSchema("\"$schema\": 7,")))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema is not a valid JSON Schema 2020-12: /$schema"));
    }

    @Test
    void shouldRejectOtherDialectBelowTheRoot() {
        assertThat(validator.validate(TYPE, validSchema("\"$defs\": {\"a\": {\"$schema\": \"http://json-schema.org/draft-07/schema#\"}},")))
                .contains("Schema must use JSON Schema 2020-12 ($schema must be absent or \"https://json-schema.org/draft/2020-12/schema\"), "
                        + "found at #/$defs/a/$schema");
    }

    @Test
    void shouldRejectSchemaFailingTheMetaSchema() {
        assertThat(validator.validate(TYPE, validSchema("\"minProperties\": -1,")))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema is not a valid JSON Schema 2020-12: "));
        assertThat(validator.validate(TYPE, validSchema("\"additionalProperties\": \"no\",")))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema is not a valid JSON Schema 2020-12: "));
        assertThat(validator.validate(TYPE, validSchema("\"$defs\": {\"pair\": {\"items\": [{\"type\": \"string\"}]}},")))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema is not a valid JSON Schema 2020-12: "));
    }

    @Test
    void shouldReportDialectFailuresBeforeRuleFailures() {
        assertThat(validator.validate(TYPE, validSchema("\"$defs\": {\"a\": {\"$id\": \"https://example.org/a\", \"minLength\": -1}},")))
                .contains("Schema is not a valid JSON Schema 2020-12: /$defs/a/minLength: must have a minimum value of 0");
    }

    @Test
    void shouldReportEachDialectFailureOnce() {
        assertThat(validator.validate(TYPE, validSchema("\"$defs\": {\"pair\": {\"items\": [{\"type\": \"string\"}]}},")))
                .contains("Schema is not a valid JSON Schema 2020-12: /$defs/pair/items: array found, [object, boolean] expected");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"properties\": {\"type\": {\"const\": \"payment_initiation\"}}, \"required\": [\"type\"]} | #",
            "{\"type\": \"string\"} | #/type",
            "{\"type\": [\"object\"], \"properties\": {\"type\": {\"const\": \"payment_initiation\"}}, \"required\": [\"type\"]} | #/type"})
    void shouldRejectRootThatIsNotAnObject(String schema, String location) {
        assertThat(validator.validate(TYPE, schema)).contains("Schema must declare \"type\": \"object\" at its root, found at " + location);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"type\": \"object\"} | its root must declare properties.type.const and list \"type\" in required, found at #",
            "{\"type\": \"object\", \"properties\": {\"type\": {\"const\": \"payment_initiation\"}}} | its root must declare properties.type.const and list \"type\" in required, found at #",
            "{\"type\": \"object\", \"properties\": {}, \"required\": [\"type\"]} | its root must declare properties.type.const, found at #/properties",
            "{\"type\": \"object\", \"properties\": {\"type\": {\"type\": \"string\"}}, \"required\": [\"type\"]} | its root must declare properties.type.const, found at #/properties/type",
            "{\"type\": \"object\", \"properties\": {\"type\": {\"const\": 1}}, \"required\": [\"type\"]} | properties.type.const must be a string, found at #/properties/type/const",
            "{\"type\": \"object\", \"properties\": {\"type\": {\"const\": \"payment_initiation\"}}, \"required\": [\"amount\"]} | its root must list \"type\" in required, found at #/required"})
    void shouldRejectSchemaNotConstrainingType(String schema, String problem) {
        assertThat(validator.validate(TYPE, schema)).contains("Schema must constrain \"type\": " + problem);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Payment_Initiation", "other"})
    void shouldRejectSchemaConstrainingTypeToAnotherValue(String constant) {
        String schema = "{\"type\": \"object\", \"properties\": {\"type\": {\"const\": \"" + constant + "\"}}, \"required\": [\"type\"]}";

        assertThat(validator.validate(TYPE, schema))
                .contains("Schema must constrain \"type\" to \"payment_initiation\": properties.type.const must equal the type");
    }

    @Test
    void shouldRejectRemoteReferenceWithoutFetchingIt() {
        String schema = validSchema("\"$defs\": {\"nested\": {\"prefixItems\": [{\"$ref\": \"" + serverUrl() + "/remote.json\"}]}},");

        assertThat(validator.validate(TYPE, schema))
                .contains("Schema must only contain local references (starting with \"#\"), found at #/$defs/nested/prefixItems/0/$ref");
        assertThat(requests).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"other.json", "other.json#/$defs/a", "file:///etc/passwd", "classpath:draft-07/schema", "urn:example:a"})
    void shouldRejectNonLocalReference(String ref) {
        assertThat(validator.validate(TYPE, validSchema("\"$defs\": {\"a\": {\"$ref\": \"" + ref + "\"}},")))
                .contains("Schema must only contain local references (starting with \"#\"), found at #/$defs/a/$ref");
    }

    @Test
    void shouldNotFetchRemoteDialect() {
        assertThat(validator.validate(TYPE, validSchema("\"$schema\": \"" + serverUrl() + "/schema#\",")))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema must use JSON Schema 2020-12"));
        assertThat(requests).hasValue(0);
    }

    @Test
    void shouldRejectIdentifierAtTheRootWithoutFetchingIt() {
        assertThat(validator.validate(TYPE, validSchema("\"$id\": \"" + serverUrl() + "/base.json\",")))
                .contains("Schema must not declare \"$id\", found at #/$id");
        assertThat(requests).hasValue(0);
    }

    @ParameterizedTest
    @CsvSource({"$id, https://example.org/a", "$anchor, a", "$dynamicAnchor, a", "$dynamicRef, #a", "$recursiveAnchor, a", "$recursiveRef, #"})
    void shouldRejectIdentifiersAndAnchorsAnywhere(String keyword, String value) {
        assertThat(validator.validate(TYPE, validSchema("\"$defs\": {\"a\": {\"items\": {\"" + keyword + "\": \"" + value + "\"}}},")))
                .contains("Schema must not declare \"" + keyword + "\", found at #/$defs/a/items/" + keyword);
    }

    @Test
    void shouldRejectVocabulary() {
        assertThat(validator.validate(TYPE, validSchema("\"$vocabulary\": {\"https://json-schema.org/draft/2020-12/vocab/core\": true},")))
                .contains("Schema must not declare \"$vocabulary\", found at #/$vocabulary");
    }

    @Test
    void shouldRejectDefinitions() {
        assertThat(validator.validate(TYPE, validSchema("\"$defs\": {\"a\": {\"definitions\": {}}},")))
                .contains("Schema must declare reusable subschemas in \"$defs\", not \"definitions\", found at #/$defs/a/definitions");
    }

    @Test
    void shouldRejectDependencies() {
        assertThat(validator.validate(TYPE, validSchema("\"dependencies\": {\"amount\": [\"currency\"]},")))
                .contains("Schema must use \"dependentSchemas\" or \"dependentRequired\", not \"dependencies\", found at #/dependencies");
    }

    @Test
    void shouldApplyKeywordsBesideAReference() {
        String schema = "{\"type\":\"object\",\"properties\":{\"type\":{\"const\":\"payment_initiation\",\"$ref\":\"#/$defs/loose\"}},"
                + "\"required\":[\"type\"],\"$ref\":\"#/$defs/loose\",\"$defs\":{\"loose\":{}}}";

        assertThat(validator.validate(TYPE, schema)).isEmpty();
        assertThat(evaluate(schema, "{\"type\":\"other\"}")).isNotEmpty();
        assertThat(evaluate(schema, "{\"type\":\"payment_initiation\"}")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"$defs\": {\"a\": {\"$ref\": \"#/$defs/b\"}, \"b\": {\"$ref\": \"#/$defs/a\"}},",
            "\"$defs\": {\"a\": {\"$ref\": \"#/$defs/a\"}},",
            "\"allOf\": [{\"$ref\": \"#\"}],",
            "\"$defs\": {\"a\": {\"not\": {\"$ref\": \"#/$defs/a\"}}},",
            "\"$defs\": {\"a\": {\"if\": {\"anyOf\": [{\"$ref\": \"#/$defs/a\"}]}}},",
            "\"dependentSchemas\": {\"amount\": {\"oneOf\": [{\"$ref\": \"#\"}]}},",
            "\"$defs\": {\"a\": {\"type\": \"string\", \"$ref\": \"#/$defs/a\"}},"})
    void shouldRejectSchemaApplyingItselfWithoutEnd(String extraMembers) {
        assertThat(validator.validate(TYPE, validSchema(extraMembers)))
                .hasValueSatisfying(error -> assertThat(error).startsWith("Schema must not apply itself to the same value without end, through #"));
    }

    @Test
    void shouldAcceptRecursionIntoTheValue() {
        String schema = "{\"type\":\"object\",\"properties\":{\"type\":{\"const\":\"payment_initiation\"},"
                + "\"children\":{\"type\":\"array\",\"items\":{\"$ref\":\"#\"}}},\"required\":[\"type\"]}";

        assertThat(validator.validate(TYPE, schema)).isEmpty();
        assertThat(evaluate(schema, "{\"type\":\"payment_initiation\",\"children\":[{\"type\":\"payment_initiation\"}]}")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#/$defs/missing", "#/properties/type/const", "#anchor", "#/$defs/amount/type", "#/x-hidden"})
    void shouldRejectReferenceThatIsNotASubschema(String ref) {
        assertThat(validator.validate(TYPE, validSchema(
                "\"x-hidden\": {\"type\": \"string\"}, \"$defs\": {\"amount\": {\"type\": \"string\"}, \"a\": {\"$ref\": \"" + ref + "\"}},")))
                .contains("Schema reference \"" + ref + "\" does not resolve to a subschema");
    }

    @Test
    void shouldResolveEscapedPointers() {
        assertThat(validator.validate(TYPE, validSchema("\"$defs\": {\"a/b\": {\"type\": \"string\"}, \"c~d\": {\"type\": \"string\"}, "
                + "\"e f\": {\"type\": \"string\"}, \"x\": {\"allOf\": [{\"$ref\": \"#/$defs/a~1b\"}, {\"$ref\": \"#/$defs/c~0d\"}, "
                + "{\"$ref\": \"#/$defs/e%20f\"}]}},")))
                .isEmpty();
    }

    @Test
    void shouldTreatConstantsAsData() {
        assertThat(validator.validate(TYPE, validSchema(
                "\"$defs\": {\"a\": {\"const\": {\"$ref\": \"https://example.org/remote\", \"$id\": \"x\", \"definitions\": {}}}},")))
                .isEmpty();
    }

    private String serverUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static String validSchema(String extraMembers) {
        return "{" + extraMembers + " \"type\": \"object\", \"properties\": {\"type\": {\"const\": \"payment_initiation\"}, "
                + "\"amount\": {\"type\": \"string\"}}, \"required\": [\"type\"]}";
    }

    private static List<com.networknt.schema.Error> evaluate(String schema, String instance) {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(schema).validate(instance, InputFormat.JSON);
    }
}
