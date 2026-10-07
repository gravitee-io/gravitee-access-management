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
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GraviteeApiDefinitionTest {

    @Test
    void shouldNotExposeTagsDifferingOnlyByCase() {
        OpenAPI openAPI = new Reader(new OpenAPI()).read(Set.of(
                OrganizationsResource.class,
                PlatformResource.class,
                CurrentUserResource.class,
                GraviteeApiDefinition.class));

        List<String> tags = openAPI.getTags().stream().map(Tag::getName).map(String::toLowerCase).toList();

        assertThat(tags).isNotEmpty().doesNotHaveDuplicates();
    }
}
