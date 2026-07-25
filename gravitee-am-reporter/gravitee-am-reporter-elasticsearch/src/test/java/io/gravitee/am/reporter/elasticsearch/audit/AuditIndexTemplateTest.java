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
package io.gravitee.am.reporter.elasticsearch.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class AuditIndexTemplateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void coversTheDailyIndices() throws Exception {
        JsonNode template = MAPPER.readTree(AuditIndexTemplate.body("gravitee-audit"));

        assertThat(template.get("index_patterns").get(0).asText()).isEqualTo("gravitee-audit-*");
    }

    @Test
    void aMoreSpecificIndexNameOutranksAShorterOneItOverlaps() {
        // patterns can only overlap when one index name is a prefix of the other, so ordering by
        // name length is enough to make overlapping reporters resolve deterministically
        assertThat(AuditIndexTemplate.priority("gravitee-audit-tenant-a"))
                .isGreaterThan(AuditIndexTemplate.priority("gravitee-audit"));
    }

    @Test
    void sitsAboveTheDefaultPriorityACustomerTemplateWouldHave() {
        assertThat(AuditIndexTemplate.priority("gravitee-audit")).isPositive();
    }

    @Test
    void storesFreeFormAttributesWithoutIndexingThem() throws Exception {
        JsonNode properties = MAPPER.readTree(AuditIndexTemplate.body("gravitee-audit"))
                .get("template").get("mappings").get("properties");

        for (String owner : new String[]{"actor", "target"}) {
            JsonNode attributes = properties.get(owner).get("properties").get("attributes");
            assertThat(attributes.get("type").asText()).isEqualTo("object");
            assertThat(attributes.get("enabled").asBoolean())
                    .describedAs("%s.attributes must not be indexed, or a value type change rejects the document", owner)
                    .isFalse();
        }
    }

    @Test
    void namesTheTemplateAfterTheIndex() {
        assertThat(AuditIndexTemplate.name("gravitee-audit")).isEqualTo("gravitee-audit-template");
    }
}
