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
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
    public static final String BASIC_AUTH_SCHEME = "BasicAuth";

    private static final Set<String> AUTOMATION_TAGS = Set.of("Environments", "Domains", "Identity Providers");

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
            openAPI.getComponents().setSchemas(filteredSchemas);
        }

        // Filter tags to only automation ones
        openAPI.tags(AUTOMATION_TAGS.stream()
                .sorted()
                .map(name -> new Tag().name(name))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

        openAPI.info(new Info()
                .version(Version.RUNTIME_VERSION.MAJOR_VERSION)
                .title("Gravitee.io AM - Automation API")
                .description("Declarative, GitOps-style management of Access Management resources.")
        );

        Components components = openAPI.getComponents() != null ? openAPI.getComponents() : new Components();
        // Replace all security schemes — remove inherited ones (e.g. gravitee-auth from management API)
        components.setSecuritySchemes(Map.of(
                BEARER_AUTH_SCHEME, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
                BASIC_AUTH_SCHEME, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
        ));
        openAPI.components(components);
        openAPI.security(List.of(
                new SecurityRequirement().addList(BEARER_AUTH_SCHEME),
                new SecurityRequirement().addList(BASIC_AUTH_SCHEME)
        ));
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
