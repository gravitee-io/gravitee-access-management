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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.SchemaLoader;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.service.validators.Validator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.gravitee.am.model.oauth2.AuthorizationDetailType.DESCRIPTION_MAX_LENGTH;
import static io.gravitee.am.model.oauth2.AuthorizationDetailType.NAME_MAX_LENGTH;
import static io.gravitee.am.model.oauth2.AuthorizationDetailType.SCHEMA_MAX_LENGTH;
import static io.gravitee.am.model.oauth2.AuthorizationDetailType.TYPE_MAX_LENGTH;

/**
 * @author GraviteeSource Team
 */
@Component
public class AuthorizationDetailTypeValidator implements Validator<AuthorizationDetailType, Optional<String>> {

    public static final String DRAFT_7_SCHEMA = "http://json-schema.org/draft-07/schema#";

    public static final String TYPE_CHARACTERS_MESSAGE = "Type must contain only visible ASCII characters (%x21-7E)";

    private static final Pattern VISIBLE_ASCII = Pattern.compile(AuthorizationDetailType.TYPE_PATTERN);
    private static final int MAX_REPORTED_ERRORS = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    // no IriResourceLoader: only the bundled meta-schemas resolve, nothing is ever fetched
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7,
            builder -> builder
                    .schemaLoader(SchemaLoader.getDefault())
                    .schemaRegistryConfig(SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build()));

    private static final Schema DRAFT_7_META_SCHEMA = SCHEMA_REGISTRY.getSchema(SchemaLocation.of(DRAFT_7_SCHEMA));

    @Override
    public Optional<String> validate(AuthorizationDetailType element) {
        return validateType(element.getType())
                .or(() -> validateName(element.getName()))
                .or(() -> validateDescription(element.getDescription()))
                .or(() -> element.getSchema() == null ? Optional.empty() : validateSchema(element.getType(), element.getSchema()));
    }

    private static Optional<String> validateType(String type) {
        if (type == null || type.isEmpty()) {
            return Optional.of("Type is required");
        }
        if (type.length() > TYPE_MAX_LENGTH) {
            return Optional.of("Type must be at most " + TYPE_MAX_LENGTH + " characters");
        }
        if (!VISIBLE_ASCII.matcher(type).matches()) {
            return Optional.of(TYPE_CHARACTERS_MESSAGE);
        }
        return Optional.empty();
    }

    private static Optional<String> validateName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.of("Name is required");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            return Optional.of("Name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return Optional.empty();
    }

    private static Optional<String> validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            return Optional.of("Description must be at most " + DESCRIPTION_MAX_LENGTH + " characters");
        }
        return Optional.empty();
    }

    private static Optional<String> validateSchema(String type, String schema) {
        if (schema.length() > SCHEMA_MAX_LENGTH) {
            return Optional.of("Schema must be at most " + SCHEMA_MAX_LENGTH + " characters");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(schema);
        } catch (JsonProcessingException e) {
            return Optional.of("Schema is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            return Optional.of("Schema must be a JSON object");
        }
        JsonNode dialect = root.get("$schema");
        if (dialect != null && !DRAFT_7_SCHEMA.equals(dialect.asText(null))) {
            return Optional.of("Schema must use JSON Schema Draft 7 ($schema must be absent or \"" + DRAFT_7_SCHEMA + "\")");
        }
        List<Error> errors = DRAFT_7_META_SCHEMA.validate(root);
        if (!errors.isEmpty()) {
            return Optional.of("Schema is not a valid JSON Schema Draft 7: " + errors.stream()
                    .limit(MAX_REPORTED_ERRORS)
                    .map(Error::toString)
                    .collect(Collectors.joining(", ")));
        }
        Optional<String> references = SchemaReferences.check(root);
        if (references.isPresent()) {
            return references;
        }
        if (!"object".equals(root.path("type").asText(null)) || !root.path("type").isTextual()) {
            return Optional.of("Schema must declare \"type\": \"object\" at its root");
        }
        if (!constrainsType(root, type)) {
            return Optional.of("Schema must constrain \"type\" to \"" + type + "\": its root must declare "
                    + "properties.type.const equal to the type and list \"type\" in required");
        }
        return Optional.empty();
    }

    private static boolean constrainsType(JsonNode root, String type) {
        JsonNode constant = root.path("properties").path("type").path("const");
        boolean pinned = constant.isTextual() && constant.asText().equals(type);
        boolean required = root.path("required").isArray()
                && StreamSupport.stream(root.path("required").spliterator(), false)
                .anyMatch(item -> item.isTextual() && "type".equals(item.asText()));
        return pinned && required;
    }
}
