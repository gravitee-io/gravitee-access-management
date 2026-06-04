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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class GraviteeApiDefinitionTest {

    private static Schema<?> dateTime() {
        return new Schema<>().type("string").format("date-time");
    }

    private OpenAPI afterScanOn(Schema<?> rootSchema) {
        OpenAPI openAPI = new OpenAPI()
                .paths(new Paths())
                .components(new Components().addSchemas("Root", rootSchema));
        new GraviteeApiDefinition().afterScan(null, openAPI);
        return openAPI;
    }

    private Schema<?> rootAfterScan(Schema<?> rootSchema) {
        return afterScanOn(rootSchema).getComponents().getSchemas().get("Root");
    }

    @Test
    void should_rewrite_date_time_property_to_epoch_millis_int64() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("createdAt", dateTime());
        Schema<?> result = rootAfterScan(new Schema<>().type("object").properties(props));

        Schema<?> createdAt = (Schema<?>) result.getProperties().get("createdAt");
        assertThat(createdAt.getType()).isEqualTo("integer");
        assertThat(createdAt.getFormat()).isEqualTo("int64");
        assertThat(createdAt.getDescription()).isEqualTo("Epoch timestamp in milliseconds.");
    }

    @Test
    void should_not_overwrite_an_existing_description() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("createdAt", dateTime().description("Domain creation date"));
        Schema<?> result = rootAfterScan(new Schema<>().type("object").properties(props));

        Schema<?> createdAt = (Schema<?>) result.getProperties().get("createdAt");
        assertThat(createdAt.getType()).isEqualTo("integer");
        assertThat(createdAt.getFormat()).isEqualTo("int64");
        assertThat(createdAt.getDescription()).isEqualTo("Domain creation date");
    }

    @Test
    void should_leave_non_timestamp_strings_untouched() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("name", new Schema<>().type("string"));
        Schema<?> result = rootAfterScan(new Schema<>().type("object").properties(props));

        Schema<?> name = (Schema<?>) result.getProperties().get("name");
        assertThat(name.getType()).isEqualTo("string");
        assertThat(name.getFormat()).isNull();
    }

    @Test
    void should_recurse_into_array_items_and_map_values() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("history", new Schema<>().type("array").items(dateTime()));
        props.put("byKey", new Schema<>().type("object").additionalProperties(dateTime()));
        Schema<?> result = rootAfterScan(new Schema<>().type("object").properties(props));

        Schema<?> items = ((Schema<?>) result.getProperties().get("history")).getItems();
        assertThat(items.getType()).isEqualTo("integer");
        assertThat(items.getFormat()).isEqualTo("int64");

        Object additional = ((Schema<?>) result.getProperties().get("byKey")).getAdditionalProperties();
        assertThat(additional).isInstanceOf(Schema.class);
        assertThat(((Schema<?>) additional).getType()).isEqualTo("integer");
        assertThat(((Schema<?>) additional).getFormat()).isEqualTo("int64");
    }
}
