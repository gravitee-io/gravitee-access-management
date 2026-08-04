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
package io.gravitee.am.service.dataplane.config;

import io.gravitee.json.validation.JsonSchemaValidatorImpl;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the {@code schema-form.json} each data plane plugin ships, which is the first pass a
 * provisioned definition is checked against.
 *
 * The schemas are read from the plugin modules rather than the classpath because those modules do not
 * depend on the validator, and their own test suites are skipped by default ({@code skip-dataplane-tests},
 * {@code skip-repositories-tests}), so a copy of this living there would never run. A broken schema
 * would otherwise fail silently: the plugin handler logs and carries on, leaving the type-specific
 * handler as the only check.
 *
 * @author GraviteeSource Team
 */
class DataPlaneSchemaFormTest {

    private static final Path PLUGINS = Path.of("..", "gravitee-am-dataplane");

    private final JsonSchemaValidatorImpl validator = new JsonSchemaValidatorImpl();

    private static String schemaOf(String plugin) {
        Path path = PLUGINS.resolve(plugin).resolve("src/main/resources/schemas/schema-form.json");
        assertThat(path).as("schema shipped by %s", plugin).exists();
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }

    private static String mongo() {
        return schemaOf("gravitee-am-dataplane-mongodb");
    }

    private static String jdbc() {
        return schemaOf("gravitee-am-dataplane-jdbc");
    }

    private void accepts(String schema, String configuration) {
        assertThatCode(() -> validator.validate(schema, configuration)).doesNotThrowAnyException();
    }

    private void rejects(String schema, String configuration, String messageFragment) {
        assertThatThrownBy(() -> validator.validate(schema, configuration))
                .hasMessageContaining(messageFragment);
    }

    @Test
    void mongoSchemaAcceptsBothConnectionForms() {
        accepts(mongo(), "{\"uri\": \"mongodb://mongo:27017/gravitee-am-acme\"}");
        accepts(mongo(), "{\"dbname\": \"gravitee-am-acme\", \"host\": \"mongo\", \"port\": 27017}");
    }

    @Test
    void mongoSchemaAcceptsAReplicaSet() {
        accepts(mongo(), "{\"dbname\": \"acme\", \"servers\": [{\"host\": \"m1\", \"port\": 27017}, {\"host\": \"m2\"}]}");
    }

    @Test
    void mongoSchemaRejectsMistypedSettings() {
        rejects(mongo(), "{\"dbname\": \"acme\", \"host\": \"mongo\", \"port\": \"27017\"}", "port");
        rejects(mongo(), "{\"dbname\": \"acme\", \"host\": \"mongo\", \"sslEnabled\": \"yes\"}", "sslEnabled");
    }

    @Test
    void mongoSchemaRejectsAReplicaSetMemberWithNoHost() {
        rejects(mongo(), "{\"dbname\": \"acme\", \"servers\": [{\"port\": 27017}]}", "host");
    }

    @Test
    void jdbcSchemaAcceptsBothConnectionForms() {
        accepts(jdbc(), "{\"uri\": \"r2dbc:postgresql://pg:5432/gravitee-am-acme\"}");
        accepts(jdbc(), "{\"driver\": \"postgresql\", \"host\": \"pg\", \"port\": 5432, \"database\": \"acme\"}");
    }

    @Test
    void jdbcSchemaRejectsADriverWithNoDataPlaneSupport() {
        rejects(jdbc(), "{\"driver\": \"oracle\", \"host\": \"db\", \"database\": \"acme\"}", "driver");
    }

    @Test
    void jdbcSchemaRejectsMistypedSettings() {
        rejects(jdbc(), "{\"driver\": \"mysql\", \"host\": \"db\", \"port\": \"3306\", \"database\": \"acme\"}", "port");
    }

    /**
     * Neither schema closes {@code additionalProperties}: the providers read more keys than are worth
     * describing, some of them deprecated, and rejecting an undescribed key would break a definition
     * that the provider itself would have honoured.
     */
    @Test
    void schemasToleratePropertiesTheyDoNotDescribe() {
        accepts(mongo(), "{\"dbname\": \"acme\", \"host\": \"mongo\", \"keystore\": \"/etc/keystore.jks\"}");
        accepts(jdbc(), "{\"driver\": \"mysql\", \"host\": \"db\", \"database\": \"acme\", \"preferCursoredExecution\": true}");
    }
}
