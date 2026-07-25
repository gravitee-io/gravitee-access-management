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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The index name rule exists twice: in {@code schema-form.json}, where it stops an illegal name being
 * accepted by the form and the management API, and in {@link AuditIndexNames}, the backstop for a
 * configuration written straight to the database. Two copies of a rule drift, so they are checked
 * against each other here rather than only against themselves.
 *
 * @author GraviteeSource Team
 */
class AuditIndexNameSchemaTest {

    /**
     * The management API validates plugin configuration with everit, which tests {@code pattern} with
     * {@link java.util.regex.Matcher#find()} rather than {@code matches()}. An unanchored pattern would
     * therefore accept any name merely containing a legal run, so the schema is exercised the same way.
     */
    private static final Pattern SCHEMA_PATTERN = Pattern.compile(schemaIndexPattern());

    @ParameterizedTest
    @ValueSource(strings = {
            "gravitee-audit", "gravitee-audit_2.x+eu", "a", "0", "9audit", "audit.log-2026",
            "Gravitee-Audit", "QA-Audit-UPPER", "gravitee Audit", "-gravitee", "_gravitee", ".gravitee",
            "+gravitee", "gravitee/audit", "gravitee*audit", "gravitee:audit", "gravitee,audit",
            "gravitee\\audit", "gravitee#audit", "gravitee?audit", "gravitee\"audit", "gravitee<audit",
            "gravitée-audit", "", " ", "audit ", " audit"
    })
    void theSchemaAndTheRuntimeCheckAgreeOnEveryName(String name) {
        assertThat(SCHEMA_PATTERN.matcher(name).find())
                .describedAs("schema and AuditIndexNames.validate disagree on '%s'", name)
                .isEqualTo(runtimeAccepts(name));
    }

    @Test
    void theSchemaConstrainsTheIndexName() {
        assertThat(schemaIndexPattern()).isNotBlank();
    }

    private static boolean runtimeAccepts(String name) {
        try {
            AuditIndexNames.validate(name);
            return true;
        } catch (IllegalStateException rejected) {
            return false;
        }
    }

    private static String schemaIndexPattern() {
        try (InputStream schema = AuditIndexNameSchemaTest.class.getResourceAsStream("/schemas/schema-form.json")) {
            JsonNode index = new ObjectMapper().readTree(schema).at("/properties/index/pattern");
            return index.asText();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the Elasticsearch reporter schema", e);
        }
    }
}
