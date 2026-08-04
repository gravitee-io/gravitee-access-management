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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.service.exception.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author GraviteeSource Team
 */
class DataPlaneConfigHandlerTest {

    /**
     * The shipped Management API configuration. Its {@code dataPlanes[0]} entry is the reference for
     * "the same information as the ones provided in the gravitee.yaml": whatever is accepted there
     * must be accepted here.
     */
    private static final Path MAPI_GRAVITEE_YML = Path.of("..",
            "gravitee-am-management-api",
            "gravitee-am-management-api-standalone",
            "gravitee-am-management-api-standalone-distribution",
            "src", "main", "resources", "config", "gravitee.yml");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MongoDataPlaneConfigHandler mongoHandler = new MongoDataPlaneConfigHandler();
    private final JdbcDataPlaneConfigHandler jdbcHandler = new JdbcDataPlaneConfigHandler();

    // --- gravitee.yml parity ---------------------------------------------------------------

    @Test
    void shouldAcceptTheDataPlaneShippedInTheManagementApiGraviteeYml() {
        ObjectNode configuration = shippedDataPlaneConfiguration();

        assertThat(configuration.get("mongodb")).isNotNull();
        assertThatCode(() -> mongoHandler.validate(configuration)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTheShippedDataPlaneWithoutDbname() {
        ObjectNode configuration = shippedDataPlaneConfiguration();
        ((ObjectNode) configuration.get("mongodb")).remove("dbname");

        assertThatThrownBy(() -> mongoHandler.validate(configuration))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("dbname");
    }

    @Test
    void shouldRejectTheShippedDataPlaneWithoutHost() {
        ObjectNode configuration = shippedDataPlaneConfiguration();
        ((ObjectNode) configuration.get("mongodb")).remove("host");

        assertThatThrownBy(() -> mongoHandler.validate(configuration))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("host");
    }

    @Test
    void shouldRejectAJdbcBlockUnderTheMongodbType() {
        ObjectNode configuration = objectMapper.createObjectNode();
        configuration.putObject("jdbc")
                .put("driver", "postgresql")
                .put("host", "postgres")
                .put("database", "gravitee-am");

        assertThatThrownBy(() -> mongoHandler.validate(configuration))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("mongodb");
    }

    @Test
    void shouldRejectAnEmptyConfiguration() {
        ObjectNode configuration = objectMapper.createObjectNode();

        assertThatThrownBy(() -> mongoHandler.validate(configuration))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("mongodb");
        assertThatThrownBy(() -> jdbcHandler.validate(configuration))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("jdbc");
    }

    // --- mongodb validation ------------------------------------------------------------------

    @Test
    void shouldAcceptAMongoUriNamingADatabase() {
        assertThatCode(() -> mongoHandler.validate(mongo("{\"uri\": \"mongodb://host:27017/gravitee-am-acme\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAMongoUriWithoutDatabase() {
        assertThatThrownBy(() -> mongoHandler.validate(mongo("{\"uri\": \"mongodb://host:27017\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("must name a database");
    }

    @Test
    void shouldRejectAMongoUriWithATrailingSlashOnly() {
        assertThatThrownBy(() -> mongoHandler.validate(mongo("{\"uri\": \"mongodb://host:27017/\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("must name a database");
    }

    @Test
    void shouldRejectAMongoUriWithoutAHost() {
        assertThatThrownBy(() -> mongoHandler.validate(mongo("{\"uri\": \"mongodb:///gravitee-am-acme\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("must name a host");
    }

    /**
     * A replica set uri parses to a null host while still naming its servers, so the host check has
     * to read the authority rather than the host.
     */
    @Test
    void shouldAcceptAMultiHostMongoUri() {
        assertThatCode(() -> mongoHandler.validate(
                mongo("{\"uri\": \"mongodb://am-user:sup3r-s3cret@mongo-1:27017,mongo-2:27017/gravitee-am-acme\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotLeakTheUriCredentialsIntoTheHostRejection() {
        assertThatThrownBy(() -> mongoHandler.validate(mongo("{\"uri\": \"mongodb://am-user:sup3r-s3cret@/gravitee-am-acme\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageNotContaining("sup3r-s3cret")
                .hasMessageNotContaining("am-user");
    }

    @Test
    void shouldAcceptAMongoReplicaSetDeclaredWithServers() {
        assertThatCode(() -> mongoHandler.validate(mongo(
                "{\"dbname\": \"gravitee-am-acme\", \"servers\": [{\"host\": \"mongo-1\", \"port\": 27017}]}")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAMongoConfigurationWithHostButNoDbname() {
        assertThatThrownBy(() -> mongoHandler.validate(mongo("{\"host\": \"mongo\", \"port\": 27017}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("dbname");
    }

    @Test
    void shouldRejectAMongoConfigurationWithDbnameButNoHost() {
        assertThatThrownBy(() -> mongoHandler.validate(mongo("{\"dbname\": \"gravitee-am-acme\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("host");
    }

    @Test
    void shouldRejectAMongoConfigurationWithAnEmptyServersArray() {
        assertThatThrownBy(() -> mongoHandler.validate(mongo("{\"dbname\": \"gravitee-am-acme\", \"servers\": []}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("host");
    }

    @Test
    void shouldRejectAMongoBlockThatIsNotAnObject() {
        assertThatThrownBy(() -> mongoHandler.validate(readTree("{\"mongodb\": \"mongodb://host/db\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("mongodb");
    }

    // --- jdbc validation ---------------------------------------------------------------------

    @Test
    void shouldAcceptAJdbcUri() {
        assertThatCode(() -> jdbcHandler.validate(jdbc("{\"uri\": \"r2dbc:postgresql://postgres:5432/gravitee-am-acme\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAJdbcUriWithoutDatabase() {
        assertThatThrownBy(() -> jdbcHandler.validate(jdbc("{\"uri\": \"r2dbc:postgresql://postgres:5432\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("must name a database");
    }

    @Test
    void shouldRejectAJdbcUriWithoutAHost() {
        assertThatThrownBy(() -> jdbcHandler.validate(jdbc("{\"uri\": \"r2dbc:postgresql:///gravitee-am-acme\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("must name a host");
    }

    @Test
    void shouldRejectAJdbcUriWithATrailingSlashOnly() {
        assertThatThrownBy(() -> jdbcHandler.validate(jdbc("{\"uri\": \"r2dbc:postgresql://postgres:5432/\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("must name a database");
    }

    @Test
    void shouldAcceptAJdbcConfigurationWithDriverHostAndDatabase() {
        assertThatCode(() -> jdbcHandler.validate(jdbc(
                "{\"driver\": \"postgresql\", \"host\": \"postgres\", \"port\": 5432, \"database\": \"gravitee-am-acme\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAJdbcConfigurationWithoutDatabase() {
        assertThatThrownBy(() -> jdbcHandler.validate(jdbc("{\"driver\": \"postgresql\", \"host\": \"postgres\"}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("database");
    }

    @Test
    void shouldListEveryMissingJdbcKey() {
        assertThatThrownBy(() -> jdbcHandler.validate(jdbc("{\"port\": 5432}")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("driver")
                .hasMessageContaining("host")
                .hasMessageContaining("database");
    }

    @Test
    void shouldExposeTheBlockNameAndSupportedType() {
        assertThat(mongoHandler.blockName()).isEqualTo("mongodb");
        assertThat(mongoHandler.supports("mongodb")).isTrue();
        assertThat(mongoHandler.supports("jdbc")).isFalse();

        assertThat(jdbcHandler.blockName()).isEqualTo("jdbc");
        assertThat(jdbcHandler.supports("jdbc")).isTrue();
        assertThat(jdbcHandler.supports("mongodb")).isFalse();
    }

    // --- summaries: what a read is allowed to expose -----------------------------------------

    @Test
    void shouldSummarizeAMongoConfigurationWithoutItsCredentials() {
        DataPlaneConnectionSummary summary = mongoHandler.summarize(mongo("""
                {
                  "dbname": "gravitee-am-acme",
                  "host": "mongo",
                  "port": 27017,
                  "username": "am-user",
                  "password": "sup3r-s3cret",
                  "authSource": "admin",
                  "keystorePassword": "keystore-s3cret",
                  "keyPassword": "key-s3cret",
                  "keystore": { "path": "/etc/ks.p12", "password": "nested-s3cret" },
                  "truststore": { "path": "/etc/ts.p12", "password": "trust-s3cret" }
                }
                """));

        assertThat(summary.database()).isEqualTo("gravitee-am-acme");
        assertThat(summary.hosts()).containsExactly("mongo:27017");
        assertThat(summary.toString())
                .doesNotContain("sup3r-s3cret", "keystore-s3cret", "key-s3cret", "nested-s3cret", "trust-s3cret", "am-user");
    }

    @Test
    void shouldSummarizeAMongoUriWithoutItsUserinfo() {
        DataPlaneConnectionSummary summary = mongoHandler.summarize(
                mongo("{\"uri\": \"mongodb://am-user:sup3r-s3cret@mongo:27017/gravitee-am-acme?authSource=admin\"}"));

        assertThat(summary.database()).isEqualTo("gravitee-am-acme");
        assertThat(summary.hosts()).containsExactly("mongo:27017");
        assertThat(summary.toString()).doesNotContain("sup3r-s3cret").doesNotContain("am-user");
    }

    @Test
    void shouldSummarizeAMongoSrvUri() {
        DataPlaneConnectionSummary summary = mongoHandler.summarize(
                mongo("{\"uri\": \"mongodb+srv://am-user:sup3r-s3cret@cluster0.abc.mongodb.net/gravitee-am-acme\"}"));

        assertThat(summary.database()).isEqualTo("gravitee-am-acme");
        assertThat(summary.hosts()).containsExactly("cluster0.abc.mongodb.net");
        assertThat(summary.toString()).doesNotContain("sup3r-s3cret");
    }

    @Test
    void shouldDropTheHostsOfAMultiHostUriRatherThanRiskTheCredentials() {
        // java.net cannot give us a host for a comma-separated authority, and we will not pick the
        // authority apart around the userinfo, so the hosts are simply not reported
        ObjectNode configuration = mongo("{\"uri\": \"mongodb://am-user:sup3r-s3cret@mongo-1:27017,mongo-2:27017/gravitee-am-acme\"}");

        // still a valid definition, it names a database
        assertThatCode(() -> mongoHandler.validate(configuration)).doesNotThrowAnyException();

        DataPlaneConnectionSummary summary = mongoHandler.summarize(configuration);
        assertThat(summary.database()).isEqualTo("gravitee-am-acme");
        assertThat(summary.hosts()).isEmpty();
        assertThat(summary.toString()).doesNotContain("sup3r-s3cret").doesNotContain("am-user");
    }

    @Test
    void shouldSummarizeAMongoReplicaSetDeclaredWithServers() {
        DataPlaneConnectionSummary summary = mongoHandler.summarize(mongo("""
                {
                  "dbname": "gravitee-am-acme",
                  "password": "sup3r-s3cret",
                  "servers": [ { "host": "mongo-1", "port": 27017 }, { "host": "mongo-2", "port": 27018 } ]
                }
                """));

        assertThat(summary.database()).isEqualTo("gravitee-am-acme");
        assertThat(summary.hosts()).containsExactly("mongo-1:27017", "mongo-2:27018");
        assertThat(summary.toString()).doesNotContain("sup3r-s3cret");
    }

    @Test
    void shouldSummarizeAJdbcConfigurationWithoutItsCredentials() {
        DataPlaneConnectionSummary summary = jdbcHandler.summarize(jdbc("""
                {
                  "driver": "postgresql",
                  "host": "postgres",
                  "port": 5432,
                  "database": "gravitee-am-acme",
                  "username": "am-user",
                  "password": "sup3r-s3cret"
                }
                """));

        assertThat(summary.database()).isEqualTo("gravitee-am-acme");
        assertThat(summary.hosts()).containsExactly("postgres:5432");
        assertThat(summary.toString()).doesNotContain("sup3r-s3cret").doesNotContain("am-user");
    }

    @Test
    void shouldSummarizeAnR2dbcUriWithoutItsUserinfo() {
        DataPlaneConnectionSummary summary = jdbcHandler.summarize(
                jdbc("{\"uri\": \"r2dbc:postgresql://am-user:sup3r-s3cret@postgres:5432/gravitee-am-acme\"}"));

        assertThat(summary.database()).isEqualTo("gravitee-am-acme");
        assertThat(summary.hosts()).containsExactly("postgres:5432");
        assertThat(summary.toString()).doesNotContain("sup3r-s3cret").doesNotContain("am-user");
    }

    @Test
    void shouldReturnAnUnknownSummaryWhenTheBlockIsAbsent() {
        assertThat(mongoHandler.summarize(objectMapper.createObjectNode())).isEqualTo(DataPlaneConnectionSummary.UNKNOWN);
        assertThat(jdbcHandler.summarize(objectMapper.createObjectNode())).isEqualTo(DataPlaneConnectionSummary.UNKNOWN);
    }

    // --- helpers ---------------------------------------------------------------------------

    /**
     * Reads {@code dataPlanes[0]} from the shipped gravitee.yml and turns it into the payload the
     * endpoint expects: everything but the definition's own metadata (id, name, type, gateway).
     */
    private ObjectNode shippedDataPlaneConfiguration() {
        assertThat(MAPI_GRAVITEE_YML)
                .describedAs("the shipped Management API gravitee.yml, this test pins the payload contract to it")
                .exists();

        Map<String, Object> gravitee;
        try (InputStream in = Files.newInputStream(MAPI_GRAVITEE_YML)) {
            gravitee = new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read " + MAPI_GRAVITEE_YML, e);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataPlanes = (List<Map<String, Object>>) gravitee.get("dataPlanes");
        assertThat(dataPlanes).describedAs("dataPlanes entries in the shipped gravitee.yml").isNotEmpty();

        Map<String, Object> entry = new LinkedHashMap<>(dataPlanes.get(0));
        entry.keySet().removeAll(List.of("id", "name", "type", "gateway"));

        return objectMapper.valueToTree(entry);
    }

    private ObjectNode mongo(String block) {
        return readTree("{\"mongodb\": " + block + "}");
    }

    private ObjectNode jdbc(String block) {
        return readTree("{\"jdbc\": " + block + "}");
    }

    private ObjectNode readTree(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
