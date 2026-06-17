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
package io.gravitee.am.management.handlers.automation.swagger;

import io.gravitee.common.util.Version;
import io.swagger.v3.jaxrs2.ReaderListener;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.integration.api.OpenApiReader;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * OpenAPI definition for the Automation API.
 * Filters the auto-generated spec to only include automation-specific paths and schemas.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@OpenAPIDefinition
public class AutomationApiDefinition implements ReaderListener {

    public static final String BEARER_AUTH_SCHEME = "BearerAuth";

    /**
     * Default servlet path the Automation API is mounted at, shared with the runtime server
     * configuration (see {@code http.api.automation.entrypoint}). The generated OAS is static, so the
     * declared server url always reflects this default even when the runtime path is overridden.
     */
    public static final String DEFAULT_AUTOMATION_ENTRYPOINT = "/automation";

    private static final Set<String> AUTOMATION_TAGS = Set.of("Domains", "Identity Providers", "Certificates", "Reporters");

    private static final String ERROR_SCHEMA = "Error";

    /**
     * Human-readable descriptions for each tag, keyed by tag name.
     */
    private static final Map<String, String> TAG_DESCRIPTIONS = Map.of(
            "Domains", "Security domains: the top-level container for an authentication and authorization " +
                    "configuration. Create, read, update, and delete domains, and reach their sub-resources.",
            "Identity Providers", "Identity providers configured under a domain to authenticate users.",
            "Certificates", "Certificates configured under a domain to sign and verify tokens.",
            "Reporters", "Reporters configured under a domain to persist audit events to a backend.");

    /**
     * Descriptions and examples for the shared path parameters, keyed by parameter name. Injected onto every
     * operation so the repetitive parameters are documented once and consistently.
     */
    private static final Map<String, String[]> PATH_PARAM_DOCS = Map.of(
            "orgId", new String[]{"Identifier of the organization that owns the environment.", "DEFAULT"},
            "envId", new String[]{"Identifier of the environment the domain belongs to.", "DEFAULT"},
            "domainKey", new String[]{"Key of the domain: its stable, immutable Automation identifier within the " +
                    "environment.", "example-domain"},
            "certKey", new String[]{"Key of the certificate within the domain.", "signing-cert"},
            "identityKey", new String[]{"Key of the identity within the domain.", "corporate-ldap"},
            "reporterKey", new String[]{"Key of the reporter within the domain.", "audit-kafka"});

    @Override
    public void beforeScan(OpenApiReader openApiReader, OpenAPI openAPI) {
    }

    @Override
    public void afterScan(OpenApiReader openApiReader, OpenAPI openAPI) {

        // Filter paths to only automation endpoints (those using {orgId} not {organizationId})
        Paths filteredPaths = new Paths();
        openAPI.getPaths().forEach((path, pathItem) -> {
            if (path.startsWith("/organizations/{orgId}")) {
                filteredPaths.put(path, pathItem);
            }
        });
        filteredPaths.putAll(new TreeMap<>(filteredPaths));
        openAPI.setPaths(filteredPaths);

        // Document a generic failure case on every operation. OAS linters
        // (e.g. Speakeasy's generator-missing-error-response) require a default
        // or 4XX/5XX response so client error handling is predictable.
        filteredPaths.values().stream()
                .map(PathItem::readOperations)
                .flatMap(Collection::stream)
                .forEach(AutomationApiDefinition::addDefaultErrorResponse);

        // Every Automation API operation is permission-gated, so document a shared 403 on each one.
        // This lets clients distinguish an authorization failure from the generic error above.
        filteredPaths.values().stream()
                .map(PathItem::readOperations)
                .flatMap(Collection::stream)
                .forEach(AutomationApiDefinition::addForbiddenResponse);

        // Document the repetitive path parameters once, consistently, on every operation.
        filteredPaths.values().stream()
                .map(PathItem::readOperations)
                .flatMap(Collection::stream)
                .forEach(AutomationApiDefinition::documentPathParameters);

        // Attach the shared Error body to every error response that lacks one (default, 4XX, 5XX), so error
        // payloads are documented and consistent across operations.
        filteredPaths.values().stream()
                .map(PathItem::readOperations)
                .flatMap(Collection::stream)
                .forEach(AutomationApiDefinition::attachErrorBodies);

        // Mirror the request-body examples (authored once as @ExampleObject on each PUT) onto the matching
        // success responses, so SDK and Terraform generators get response examples without hand-maintaining a
        // second copy. A response that returns the same entity reuses the example verbatim; a list response
        // wraps the example values in an array.
        Map<String, Map<String, Example>> entityExamples = collectRequestBodyExamples(filteredPaths);
        filteredPaths.values().stream()
                .map(PathItem::readOperations)
                .flatMap(Collection::stream)
                .forEach(op -> propagateExamplesToResponses(op, entityExamples));

        // Collect schema names referenced by the filtered paths
        Set<String> referencedSchemas = new HashSet<>();
        filteredPaths.values().stream()
                .map(PathItem::readOperations)
                .flatMap(Collection::stream)
                .forEach(op -> collectSchemaRefs(op, referencedSchemas));

        // Filter schemas to only referenced ones, then resolve transitive refs
        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            Map<String, Schema> allSchemas = openAPI.getComponents().getSchemas();
            Set<String> resolved = new HashSet<>();
            Set<String> toResolve = new HashSet<>(referencedSchemas);
            while (!toResolve.isEmpty()) {
                Set<String> next = new HashSet<>();
                for (String name : toResolve) {
                    if (resolved.add(name) && allSchemas.containsKey(name)) {
                        collectSchemaRefsFromSchema(allSchemas.get(name), next);
                    }
                }
                toResolve = next;
            }
            Map<String, Schema> filteredSchemas = new TreeMap<>();
            for (String name : resolved) {
                if (allSchemas.containsKey(name)) {
                    filteredSchemas.put(name, allSchemas.get(name));
                }
            }
            fixWebAuthnCertificatesValueType(filteredSchemas);
            filteredSchemas.values().forEach(AutomationApiDefinition::lowercaseEnumsInSchema);
            // sort properties within each schema for deterministic output
            filteredSchemas.values().forEach(AutomationApiDefinition::sortSchemaProperties);
            openAPI.getComponents().setSchemas(filteredSchemas);
        }

        // Filter tags to only automation ones, each with a description.
        openAPI.tags(AUTOMATION_TAGS.stream()
                .sorted()
                .map(name -> new Tag().name(name).description(TAG_DESCRIPTIONS.get(name)))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

        // The Automation API is mounted at the configurable http.api.automation.entrypoint
        // (defaults to /automation), mirroring how the management API declares its
        // /management server in GraviteeApiDefinition. The spec is static, so it always reflects the default.
        openAPI.servers(List.of(new Server().url("http://localhost:8093" + DEFAULT_AUTOMATION_ENTRYPOINT)));
        openAPI.info(new Info()
                .version(Version.RUNTIME_VERSION.toString())
                .title("Gravitee.io AM - Automation API")
                .description(API_DESCRIPTION)
                .contact(new Contact()
                        .name("DevX team")
                        .url("https://www.gravitee.io/")
                        .email("team-gko@graviteesource.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.html"))
        );

        Components components = openAPI.getComponents() != null ? openAPI.getComponents() : new Components();
        // Register the shared Error schema referenced by the error responses.
        if (components.getSchemas() == null) {
            components.setSchemas(new TreeMap<>());
        }
        components.getSchemas().put(ERROR_SCHEMA, errorSchema());
        // Replace all security schemes — remove inherited ones (e.g. gravitee-auth from management API).
        // Use an ordered map so the generated spec is deterministic.
        Map<String, SecurityScheme> securitySchemes = new LinkedHashMap<>();
        securitySchemes.put(BEARER_AUTH_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .description("Authentication uses a bearer token: a JWT, or an opaque user service-account " +
                        "access token. Every operation is additionally permission-gated against the target " +
                        "organization, environment, and resource; a caller lacking the required permission " +
                        "receives a 403 response."));
        components.setSecuritySchemes(securitySchemes);
        openAPI.components(components);
        openAPI.security(List.of(new SecurityRequirement().addList(BEARER_AUTH_SCHEME)));
    }

    private static final String API_DESCRIPTION =
            "Declarative, idempotent management of Gravitee Access Management resources.\n\n" +
            "Each resource is identified by a stable, immutable `key` that you choose. Applying a resource with " +
            "`PUT` creates it on first use and updates it on subsequent applies, so the same request can be " +
            "replayed safely to converge on a desired state.\n\n" +
            "The API only sees and manages resources it owns (those marked as managed by the Automation API). " +
            "Resources created through the Management API or console are invisible to these endpoints and cannot " +
            "be read, updated, or deleted here; conversely, resources created here are fully owned by the " +
            "Automation API.\n\n" +
            "Domains are the top-level resource; certificates, identity providers, and reporters are managed as " +
            "sub-resources of a domain. Deleting a domain cascades to its Automation-managed sub-resources. Some " +
            "sub-resources support a `system` flag identifying the domain's built-in default; the flag is " +
            "immutable once set.\n\n" +
            "This API complements the Management API, which remains the interactive, full-featured management " +
            "surface.";

    private static void addDefaultErrorResponse(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        boolean hasErrorResponse = responses.keySet().stream()
                .anyMatch(code -> "default".equals(code) || code.startsWith("4") || code.startsWith("5"));
        if (!hasErrorResponse) {
            responses.addApiResponse("default", new ApiResponse().description("Unexpected error"));
        }
    }

    private static void addForbiddenResponse(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        responses.computeIfAbsent("403", code -> new ApiResponse().description("Permission denied"));
        // Re-insert in sorted key order so the generated spec is deterministic (ApiResponses is a LinkedHashMap).
        ApiResponses sorted = new ApiResponses();
        new TreeMap<>(responses).forEach(sorted::addApiResponse);
        operation.setResponses(sorted);
    }

    /**
     * Sets a description and example on each shared path parameter, using {@link #PATH_PARAM_DOCS}, so the
     * repetitive parameters are documented once and consistently across every operation.
     */
    private static void documentPathParameters(Operation operation) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            String[] docs = PATH_PARAM_DOCS.get(parameter.getName());
            if (docs != null) {
                if (parameter.getDescription() == null) {
                    parameter.setDescription(docs[0]);
                }
                if (parameter.getExample() == null) {
                    parameter.setExample(docs[1]);
                }
            }
        }
    }

    /**
     * Attaches the shared {@code Error} body to every error response (default, 4XX, 5XX) that has no content, so
     * error payloads are documented and consistent. The example's {@code http_status} matches the response code
     * where it is numeric.
     */
    @SuppressWarnings("rawtypes")
    private static void attachErrorBodies(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return;
        }
        responses.forEach((code, response) -> {
            boolean isError = "default".equals(code) || code.startsWith("4") || code.startsWith("5");
            if (isError && response.getContent() == null) {
                Schema ref = new Schema().$ref("#/components/schemas/" + ERROR_SCHEMA);
                // Use an ordered map: Map.of has a randomized, per-JVM iteration order, which would make the
                // serialized example fields reorder between regenerations and flake the staleness check.
                Map<String, Object> exampleValue = new LinkedHashMap<>();
                exampleValue.put("message", "A human-readable description of the error");
                exampleValue.put("http_status", errorStatusForExample(code));
                Example example = new Example().value(exampleValue);
                response.setContent(new Content().addMediaType("application/json",
                        new MediaType().schema(ref).addExamples("error", example)));
            }
        });
    }

    private static int errorStatusForExample(String code) {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return 500;
        }
    }

    /**
     * Builds a map of entity schema name to the examples authored on the request body that targets it. The
     * Automation API authors comprehensive {@code @ExampleObject}s once on each {@code PUT}; this lets those
     * examples be reused on the corresponding responses.
     */
    @SuppressWarnings("rawtypes")
    private static Map<String, Map<String, Example>> collectRequestBodyExamples(Paths paths) {
        Map<String, Map<String, Example>> byEntity = new HashMap<>();
        paths.values().stream()
                .map(PathItem::readOperations)
                .flatMap(Collection::stream)
                .forEach(op -> {
                    if (op.getRequestBody() == null || op.getRequestBody().getContent() == null) {
                        return;
                    }
                    op.getRequestBody().getContent().values().forEach(media -> {
                        String entity = entityNameOf(media.getSchema());
                        if (entity != null && media.getExamples() != null && !media.getExamples().isEmpty()) {
                            byEntity.put(entity, media.getExamples());
                        }
                    });
                });
        return byEntity;
    }

    /**
     * Copies the request-body examples onto each success ({@code 2XX}) response that returns the same entity (or
     * an array of it) and has no examples of its own. Direct-entity responses reuse the examples verbatim; list
     * responses wrap the example values in a single array example. Existing response examples are never replaced.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void propagateExamplesToResponses(Operation operation, Map<String, Map<String, Example>> entityExamples) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return;
        }
        responses.forEach((code, response) -> {
            if (!code.startsWith("2") || response.getContent() == null) {
                return;
            }
            response.getContent().values().forEach(media -> {
                if (media.getSchema() == null || (media.getExamples() != null && !media.getExamples().isEmpty())) {
                    return;
                }
                Schema schema = media.getSchema();
                String direct = entityNameOf(schema);
                if (direct != null && entityExamples.containsKey(direct)) {
                    entityExamples.get(direct).forEach((name, ex) -> media.addExamples(name, copyExample(ex)));
                    return;
                }
                if ("array".equals(schema.getType()) && schema.getItems() != null) {
                    String itemEntity = entityNameOf(schema.getItems());
                    if (itemEntity != null && entityExamples.containsKey(itemEntity)) {
                        List<Object> values = new ArrayList<>();
                        entityExamples.get(itemEntity).values().forEach(ex -> values.add(ex.getValue()));
                        media.addExamples(itemEntity + "List", new Example().value(values));
                    }
                }
            });
        });
    }

    @SuppressWarnings("rawtypes")
    private static String entityNameOf(Schema schema) {
        if (schema == null || schema.get$ref() == null) {
            return null;
        }
        return schema.get$ref().replace("#/components/schemas/", "");
    }

    /**
     * Creates a fresh {@link Example} carrying the same content, so request and response specs do not share a
     * single instance (which a YAML serializer could otherwise emit as an anchor/alias).
     */
    private static Example copyExample(Example src) {
        return new Example()
                .summary(src.getSummary())
                .description(src.getDescription())
                .value(src.getValue());
    }

    /**
     * The shared error body returned by the management exception mappers ({@code ErrorEntity}): a human-readable
     * message and the HTTP status code.
     */
    @SuppressWarnings("rawtypes")
    private static Schema errorSchema() {
        Schema schema = new Schema()
                .type("object")
                .description("Error response body returned for failed requests.");
        schema.addProperty("message", new StringSchema()
                .description("Human-readable description of the error."));
        schema.addProperty("http_status", new IntegerSchema()
                .description("HTTP status code of the error response.")
                .example(400));
        return schema;
    }

    @SuppressWarnings("rawtypes")
    private static void sortSchemaProperties(Schema schema) {
        if (schema.getProperties() != null) {
            schema.setProperties(new LinkedHashMap<>(new TreeMap<>(schema.getProperties())));
        }
    }

    /**
     * Retypes {@code WebAuthnSettings.certificates} map values to {@code string} as a spec-only correction.
     */
    @SuppressWarnings("rawtypes")
    private static void fixWebAuthnCertificatesValueType(Map<String, Schema> schemas) {
        Schema webAuthn = schemas.get("WebAuthnSettings");
        if (webAuthn == null || webAuthn.getProperties() == null) {
            return;
        }
        Object certificates = webAuthn.getProperties().get("certificates");
        if (certificates instanceof Schema certificatesSchema
                && certificatesSchema.getAdditionalProperties() instanceof Schema valueSchema) {
            valueSchema.setType("string");
        }
    }

    /**
     * Lowercases the {@code enum} values (and an enum-typed {@code default}) of a schema and, recursively, of
     * its properties, array items and map values to match the runtime wire format.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void lowercaseEnumsInSchema(Schema schema) {
        if (schema == null) {
            return;
        }
        List<Object> values = schema.getEnum();
        if (values != null && !values.isEmpty()) {
            values.replaceAll(value -> value instanceof String s ? s.toLowerCase(Locale.ROOT) : value);
            if (schema.getDefault() instanceof String defaultValue) {
                schema.setDefault(defaultValue.toLowerCase(Locale.ROOT));
            }
        }
        if (schema.getProperties() != null) {
            ((Map<String, Schema>) schema.getProperties()).values()
                    .forEach(AutomationApiDefinition::lowercaseEnumsInSchema);
        }
        if (schema.getItems() != null) {
            lowercaseEnumsInSchema(schema.getItems());
        }
        if (schema.getAdditionalProperties() instanceof Schema additionalProperties) {
            lowercaseEnumsInSchema(additionalProperties);
        }
    }

    @SuppressWarnings("rawtypes")
    private void collectSchemaRefs(Operation operation, Set<String> refs) {
        if (operation.getResponses() != null) {
            operation.getResponses().values().forEach(resp -> {
                if (resp.getContent() != null) {
                    resp.getContent().values().forEach(media -> {
                        if (media.getSchema() != null) {
                            addSchemaRef(media.getSchema(), refs);
                        }
                        if (media.getSchema() != null && media.getSchema().getItems() != null) {
                            addSchemaRef(media.getSchema().getItems(), refs);
                        }
                    });
                }
            });
        }
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            operation.getRequestBody().getContent().values().forEach(media -> {
                if (media.getSchema() != null) {
                    addSchemaRef(media.getSchema(), refs);
                }
            });
        }
    }

    @SuppressWarnings("rawtypes")
    private void addSchemaRef(Schema schema, Set<String> refs) {
        if (schema.get$ref() != null) {
            refs.add(schema.get$ref().replace("#/components/schemas/", ""));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void collectSchemaRefsFromSchema(Schema schema, Set<String> refs) {
        if (schema.get$ref() != null) {
            refs.add(schema.get$ref().replace("#/components/schemas/", ""));
        }
        if (schema.getProperties() != null) {
            ((Map<String, Schema>) schema.getProperties()).values().forEach(prop -> {
                if (prop.get$ref() != null) {
                    refs.add(prop.get$ref().replace("#/components/schemas/", ""));
                }
                if (prop.getItems() != null && prop.getItems().get$ref() != null) {
                    refs.add(prop.getItems().get$ref().replace("#/components/schemas/", ""));
                }
            });
        }
        if (schema.getItems() != null && schema.getItems().get$ref() != null) {
            refs.add(schema.getItems().get$ref().replace("#/components/schemas/", ""));
        }
    }
}
