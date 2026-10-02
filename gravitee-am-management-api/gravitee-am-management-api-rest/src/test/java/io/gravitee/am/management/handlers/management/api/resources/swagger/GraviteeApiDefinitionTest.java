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

import io.gravitee.am.management.handlers.management.api.resources.organizations.CurrentUserResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.OrganizationsResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.PlatformResource;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GraviteeApiDefinitionTest {

    private static final String DOMAIN_DEVICE_IDENTIFIERS_PATH = "/domains/{domain}/device-identifiers";
    private static final String PLUGIN_DEVICE_IDENTIFIERS_PATH = "/platform/plugins/device-identifiers";
    private static final String PLUGIN_AND_DOMAIN_TAG = "device-identifier";
    private static final String LEGACY_DOMAIN_TAG = "device-identifiers";

    private static OpenAPI openAPI;

    @BeforeAll
    static void generateOpenApi() {
        openAPI = new Reader(new OpenAPI()).read(Set.of(
                OrganizationsResource.class,
                PlatformResource.class,
                CurrentUserResource.class,
                GraviteeApiDefinition.class));
    }

    @Test
    void shouldExposeOnlyLowercaseHyphenatedTags() {
        List<String> tags = openAPI.getTags().stream().map(Tag::getName).toList();

        assertThat(tags)
                .isNotEmpty()
                .allSatisfy(tag -> assertThat(tag).matches("[a-z]+(-[a-z]+)*"));
    }

    @Test
    void shouldGroupPluginAndDomainDeviceIdentifierOperationsUnderSameTag() {
        assertThat(operationTagsUnder(PLUGIN_DEVICE_IDENTIFIERS_PATH))
                .hasSize(3)
                .allSatisfy(tags -> assertThat(tags).contains(PLUGIN_AND_DOMAIN_TAG));
        assertThat(operationTagsUnder(DOMAIN_DEVICE_IDENTIFIERS_PATH))
                .hasSize(5)
                .allSatisfy(tags -> assertThat(tags).contains(PLUGIN_AND_DOMAIN_TAG));
    }

    @Test
    void shouldKeepLegacyDeviceIdentifiersTagOnDomainOperationsForBackwardCompatibility() {
        assertThat(operationTagsUnder(DOMAIN_DEVICE_IDENTIFIERS_PATH))
                .hasSize(5)
                .allSatisfy(tags -> assertThat(tags).contains(LEGACY_DOMAIN_TAG));
        assertThat(operationTagsUnder(PLUGIN_DEVICE_IDENTIFIERS_PATH))
                .hasSize(3)
                .allSatisfy(tags -> assertThat(tags).doesNotContain(LEGACY_DOMAIN_TAG));
    }

    @Test
    void shouldDescribeLegacyDeviceIdentifiersTagAsDeprecated() {
        assertThat(openAPI.getTags())
                .filteredOn(tag -> LEGACY_DOMAIN_TAG.equals(tag.getName()))
                .singleElement()
                .extracting(Tag::getDescription)
                .isEqualTo("Deprecated, use '" + PLUGIN_AND_DOMAIN_TAG + "'");
    }

    private static List<List<String>> operationTagsUnder(String pathFragment) {
        return openAPI.getPaths().entrySet().stream()
                .filter(path -> path.getKey().contains(pathFragment))
                .flatMap(path -> path.getValue().readOperations().stream())
                .map(Operation::getTags)
                .toList();
    }
}
