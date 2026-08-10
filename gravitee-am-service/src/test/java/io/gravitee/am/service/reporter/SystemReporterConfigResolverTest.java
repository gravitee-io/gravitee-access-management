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
package io.gravitee.am.service.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author GraviteeSource Team
 */
class SystemReporterConfigResolverTest {

    private final MockEnvironment environment = new MockEnvironment();
    private final SystemReporterConfigResolver resolver = new SystemReporterConfigResolver(new RepositoriesEnvironment(environment));

    @Test
    @SuppressWarnings("unchecked")
    void shouldHaveNullPortWhenMongoServersAreDefined() throws Exception {
        environment.setProperty("repositories.management.mongodb.servers[0].host", "localhost");
        environment.setProperty("repositories.management.mongodb.servers[0].port", 27017);
        environment.setProperty("repositories.management.mongodb.port", 99999); // this value should be ignored

        String reporterConfig = resolver.createReporterConfig(Reference.domain("test"));
        Map<String, Object> config = new ObjectMapper().readValue(reporterConfig, Map.class);

        assertNull(config.get("port"));
        assertEquals("mongodb://localhost:27017/gravitee-am?connectTimeoutMS=5000&socketTimeoutMS=5000", config.get("uri"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPairHostAndPortOfEveryMongoServer() throws Exception {
        environment.setProperty("repositories.management.mongodb.servers[0].host", "mongo-1");
        environment.setProperty("repositories.management.mongodb.servers[0].port", 27017);
        environment.setProperty("repositories.management.mongodb.servers[1].host", "mongo-2");
        environment.setProperty("repositories.management.mongodb.servers[1].port", 27018);
        environment.setProperty("repositories.management.mongodb.servers[2].host", "mongo-3");

        String reporterConfig = resolver.createReporterConfig(Reference.domain("test"));
        Map<String, Object> config = new ObjectMapper().readValue(reporterConfig, Map.class);

        assertNull(config.get("port"));
        assertEquals("mongodb://mongo-1:27017,mongo-2:27018,mongo-3:27017/gravitee-am?connectTimeoutMS=5000&socketTimeoutMS=5000", config.get("uri"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseHostAndPortWhenMongoServersAreNotDefined() throws Exception {
        environment.setProperty("repositories.management.mongodb.host", "localhost");
        environment.setProperty("repositories.management.mongodb.port", 27017);

        String reporterConfig = resolver.createReporterConfig(Reference.domain("test"));
        Map<String, Object> config = new ObjectMapper().readValue(reporterConfig, Map.class);

        assertEquals("localhost", config.get("host"));
        assertEquals(27017, config.get("port"));
        assertEquals("mongodb://localhost:27017/gravitee-am?connectTimeoutMS=5000&socketTimeoutMS=5000", config.get("uri"));
    }

    @Test
    void shouldSuffixCollectionWithDomainId() throws Exception {
        assertEquals("reporter_audits_domain-1", reportableCollection(Reference.domain("domain-1")));
    }

    @Test
    void shouldNotSuffixCollectionForDefaultOrganization() throws Exception {
        assertEquals("reporter_audits", reportableCollection(Reference.organization(Organization.DEFAULT)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveConfigurationOfSystemReporterFromEnvironment() throws Exception {
        environment.setProperty("repositories.management.mongodb.host", "mongo-host");
        environment.setProperty("repositories.management.mongodb.port", 27017);

        Reporter resolved = resolver.resolve(systemReporter("domain-1", "{\"uri\":\"mongodb://stale:27017/old\"}"));

        Map<String, Object> config = new ObjectMapper().readValue(resolved.getConfiguration(), Map.class);
        assertEquals("mongo-host", config.get("host"));
        assertEquals("reporter_audits_domain-1", config.get("reportableCollection"));
    }

    @Test
    void shouldNotChangeReporterWhileResolving() {
        Reporter reporter = systemReporter("domain-1", "{\"uri\":\"mongodb://stale:27017/old\"}");

        resolver.resolve(reporter);

        assertEquals("{\"uri\":\"mongodb://stale:27017/old\"}", reporter.getConfiguration());
    }

    @Test
    void shouldKeepConfigurationOfNonSystemReporter() {
        Reporter reporter = systemReporter("domain-1", "{\"filename\":\"audit\"}");
        reporter.setSystem(false);

        assertSame(reporter, resolver.resolve(reporter));
    }

    @Test
    void shouldKeepConfigurationWhenBackendIsNotSupported() {
        environment.setProperty("repositories.management.type", "redis");
        Reporter reporter = systemReporter("domain-1", "{\"uri\":\"mongodb://stale:27017/old\"}");

        assertSame(reporter, resolver.resolve(reporter));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveJdbcConfigurationFromEnvironment() throws Exception {
        useJdbcBackend();
        environment.setProperty("repositories.management.jdbc.username", "am-user");
        environment.setProperty("repositories.management.jdbc.password", "am-secret");
        environment.setProperty("repositories.management.jdbc.schema", "am-schema");

        Map<String, Object> config = new ObjectMapper().readValue(resolver.createReporterConfig(Reference.domain("domain-1")), Map.class);

        assertEquals("postgres-host", config.get("host"));
        assertEquals(5432, config.get("port"));
        assertEquals("gravitee-am", config.get("database"));
        assertEquals("postgresql", config.get("driver"));
        assertEquals("am-user", config.get("username"));
        assertEquals("am-secret", config.get("password"));
        assertEquals("domain_1", config.get("tableSuffix"));
        assertEquals(List.of(Map.of("option", "currentSchema", "value", "am-schema")), config.get("options"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveJdbcConfigurationWithoutPasswordAndSchema() throws Exception {
        useJdbcBackend();
        environment.setProperty("repositories.management.jdbc.username", "am-user");

        Map<String, Object> config = new ObjectMapper().readValue(resolver.createReporterConfig(Reference.domain("domain-1")), Map.class);

        assertNull(config.get("password"));
        assertEquals(List.of(), config.get("options"));
    }

    @Test
    void shouldNotSuffixJdbcTableForDefaultOrganization() throws Exception {
        useJdbcBackend();

        assertEquals("", tableSuffix(Reference.organization(Organization.DEFAULT)));
    }

    @Test
    void shouldHashJdbcTableSuffixWhenDomainIdIsTooLong() throws Exception {
        useJdbcBackend();

        assertEquals("8bcd5a9ce5cecaa75ee6eaca547d9b", tableSuffix(Reference.domain("a-very-long-domain-identifier-exceeding-limit")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveConfigurationOfSystemReporterFromJdbcEnvironment() throws Exception {
        useJdbcBackend();
        Reporter resolved = resolver.resolve(systemReporter("domain-1", "{\"host\":\"stale\"}"));

        Map<String, Object> config = new ObjectMapper().readValue(resolved.getConfiguration(), Map.class);
        assertEquals("postgres-host", config.get("host"));
        assertEquals("domain_1", config.get("tableSuffix"));
    }

    private void useJdbcBackend() {
        environment.setProperty("repositories.management.type", "jdbc");
        environment.setProperty("repositories.management.jdbc.host", "postgres-host");
        environment.setProperty("repositories.management.jdbc.port", 5432);
        environment.setProperty("repositories.management.jdbc.database", "gravitee-am");
        environment.setProperty("repositories.management.jdbc.driver", "postgresql");
    }

    @SuppressWarnings("unchecked")
    private String tableSuffix(Reference reference) throws Exception {
        Map<String, Object> config = new ObjectMapper().readValue(resolver.createReporterConfig(reference), Map.class);
        return (String) config.get("tableSuffix");
    }

    @SuppressWarnings("unchecked")
    private String reportableCollection(Reference reference) throws Exception {
        Map<String, Object> config = new ObjectMapper().readValue(resolver.createReporterConfig(reference), Map.class);
        return (String) config.get("reportableCollection");
    }

    private Reporter systemReporter(String domainId, String configuration) {
        Reporter reporter = new Reporter();
        reporter.setId("reporter-id");
        reporter.setName("MongoDB Reporter");
        reporter.setType("mongodb");
        reporter.setSystem(true);
        reporter.setReference(Reference.domain(domainId));
        reporter.setConfiguration(configuration);
        return reporter;
    }
}
