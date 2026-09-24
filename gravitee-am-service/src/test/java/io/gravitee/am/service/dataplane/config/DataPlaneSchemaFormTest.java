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

import io.gravitee.am.plugins.handlers.api.core.StrictJsonSchemaValidator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author GraviteeSource Team
 */
class DataPlaneSchemaFormTest {

    private static final Path PLUGINS = Path.of("..", "gravitee-am-dataplane");

    private final StrictJsonSchemaValidator validator = new StrictJsonSchemaValidator();

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
    void mongoSchemaRejectsAnUnknownKey() {
        // a typo would otherwise be dropped and the plugin would fall back to its default
        rejects(mongo(), "{\"dbname\": \"acme\", \"host\": \"mongo\", \"port\": 27017, \"bogus\": \"x\"}", "bogus");
        rejects(mongo(), "{\"dbName\": \"acme\", \"host\": \"mongo\"}", "dbName");
        rejects(mongo(), "{\"dbname\": \"acme\", \"servers\": [{\"host\": \"m1\", \"Port\": 27017}]}", "Port");
        rejects(mongo(), "{\"dbname\": \"acme\", \"host\": \"mongo\", \"truststore\": {\"path\": \"/etc/ts.jks\", \"pass\": \"x\"}}", "pass");
    }

    @Test
    void mongoSchemaAcceptsANullValueForADeclaredKeyOnly() {
        accepts(mongo(), "{\"dbname\": \"acme\", \"host\": \"mongo\", \"port\": null, \"username\": null, \"truststore\": null}");
        rejects(mongo(), "{\"dbname\": \"acme\", \"host\": \"mongo\", \"bogus\": null}", "bogus");
    }

    @Test
    void mongoSchemaRejectsAPortOutOfRange() {
        rejects(mongo(), "{\"dbname\": \"acme\", \"host\": \"mongo\", \"port\": 99999}", "port");
        rejects(mongo(), "{\"dbname\": \"acme\", \"servers\": [{\"host\": \"m1\", \"port\": 65536}]}", "port");
        accepts(mongo(), "{\"dbname\": \"acme\", \"host\": \"mongo\", \"port\": 65535}");
    }

    @Test
    void mongoSchemaAcceptsTheKeyAndTrustStores() {
        accepts(mongo(), """
                {"dbname": "acme", "host": "mongo", "sslEnabled": true,
                 "keystore": {"path": "/etc/ks.p12", "type": "PKCS12", "password": "secret", "keyPassword": "secret"},
                 "truststore": {"path": "/etc/ts.jks", "type": "JKS", "password": "secret"}}
                """);
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
    void jdbcSchemaRejectsAConnectionThatIsNeitherForm() {
        rejects(jdbc(), "{\"port\": 5432}", "required");
        rejects(jdbc(), "{\"driver\": \"postgresql\", \"host\": \"pg\"}", "required");
        rejects(jdbc(), "{\"driver\": \"postgresql\", \"database\": \"acme\"}", "required");
    }

    @Test
    void jdbcSchemaRejectsABlankKeyStandingInForAMissingOne() {
        // 'required' only asks for the key, so the connection would still land on the driver defaults
        rejects(jdbc(), "{\"uri\": \"\"}", "uri");
        rejects(jdbc(), "{\"driver\": \"postgresql\", \"host\": \"\", \"database\": \"acme\"}", "host");
        rejects(jdbc(), "{\"driver\": \"postgresql\", \"host\": \"pg\", \"database\": \"\"}", "database");
    }

    @Test
    void jdbcSchemaRejectsMistypedSettings() {
        rejects(jdbc(), "{\"driver\": \"mysql\", \"host\": \"db\", \"port\": \"3306\", \"database\": \"acme\"}", "port");
    }

    @Test
    void jdbcSchemaRejectsAnUnknownKey() {
        // a typo would otherwise be dropped and the plugin would fall back to its default
        rejects(jdbc(), "{\"driver\": \"mysql\", \"host\": \"db\", \"database\": \"acme\", \"bogus\": \"x\"}", "bogus");
        rejects(jdbc(), "{\"driver\": \"mysql\", \"host\": \"db\", \"database\": \"acme\", \"userName\": \"am\"}", "userName");
        rejects(jdbc(), "{\"driver\": \"mysql\", \"host\": \"db\", \"database\": \"acme\", \"trustStore\": {\"path\": \"/etc/ts.jks\", \"pass\": \"x\"}}", "pass");
    }

    @Test
    void jdbcSchemaAcceptsANullValueForADeclaredKeyOnly() {
        accepts(jdbc(), "{\"driver\": \"mysql\", \"host\": \"db\", \"port\": null, \"database\": \"acme\", \"schema\": null}");
        rejects(jdbc(), "{\"driver\": \"mysql\", \"host\": \"db\", \"database\": \"acme\", \"userName\": null}", "userName");
    }

    @Test
    void jdbcSchemaRejectsAPortOutOfRange() {
        rejects(jdbc(), "{\"driver\": \"postgresql\", \"host\": \"pg\", \"port\": 99999, \"database\": \"acme\"}", "port");
        accepts(jdbc(), "{\"driver\": \"postgresql\", \"host\": \"pg\", \"port\": 65535, \"database\": \"acme\"}");
    }

    @Test
    void jdbcSchemaAcceptsTheSettingsReadByTheConnectionFactory() {
        accepts(jdbc(), """
                {"driver": "postgresql", "host": "pg", "database": "acme",
                 "validationQuery": "SELECT 1", "maxValidationTime": 2000, "tcpKeepAlive": false, "preferCursoredExecution": true,
                 "sslEnabled": true, "sslMode": "verify-full", "sslServerCert": "/etc/pg.crt",
                 "trustStore": {"path": "/etc/ts.jks", "password": "secret"}}
                """);
    }
}
