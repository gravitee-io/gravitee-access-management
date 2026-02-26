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
package io.gravitee.am.management.handlers.management.api.resources.swagger;

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
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@OpenAPIDefinition
public class GraviteeApiDefinition implements ReaderListener {

    public static final String TOKEN_AUTH_SCHEME = "gravitee-auth";

    @Override
    public void beforeScan(OpenApiReader openApiReader, OpenAPI openAPI) {
        // This method is currently not implemented and does not perform any actions.
    }

    @SuppressWarnings("rawtypes")
    private static void sortSchemaProperties(Schema schema) {
        if (schema.getProperties() != null) {
            schema.setProperties(new LinkedHashMap<>(new TreeMap<>(schema.getProperties())));
        }
    }

    @Override
    public void afterScan(OpenApiReader openApiReader, OpenAPI openAPI){

        Map<String, Tag> tags = new TreeMap<>();
        if (openAPI.getTags() != null) {
            openAPI.getTags().forEach(tag -> tags.put(tag.getName(), tag));
        }
        openAPI
                .getPaths()
                .values()
                .stream()
                .map(PathItem::readOperations)
                .flatMap(Collection::stream)
                .map(Operation::getTags)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .forEach(tag -> tags.computeIfAbsent(tag, s -> new Tag().name(s)));

        openAPI.servers(List.of(new Server().url("/management")));
        openAPI.info(new Info()
                .version(Version.RUNTIME_VERSION.MAJOR_VERSION)
                .title("Gravitee.io - Access Management API")
        );

        // sort tags for better comparisons
        openAPI.tags(new ArrayList<>(tags.values()));
        // sort paths for better comparisons
        Paths paths = new Paths();
        paths.putAll(new TreeMap<>(openAPI.getPaths()));
        openAPI.setPaths(paths);
        // sort definitions for better comparisons
        Map<String, Schema> sortedSchemas = new TreeMap<>(openAPI.getComponents().getSchemas());
        // sort properties within each schema for deterministic SDK generation
        sortedSchemas.values().forEach(GraviteeApiDefinition::sortSchemaProperties);
        Components components = new Components();
        components.schemas(sortedSchemas);
        components.addSecuritySchemes(TOKEN_AUTH_SCHEME, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("Bearer"));
        openAPI.components(components);
        openAPI.addSecurityItem(new SecurityRequirement().addList(TOKEN_AUTH_SCHEME));
    }
}
