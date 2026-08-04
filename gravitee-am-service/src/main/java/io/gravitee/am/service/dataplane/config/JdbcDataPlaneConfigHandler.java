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

import com.fasterxml.jackson.databind.JsonNode;
import io.gravitee.am.service.exception.InvalidParameterException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * The schema requires either a uri or all of driver, host and database. What it cannot say is that a
 * uri has to name a host and a database, without which the connection still builds and lands on
 * whatever the driver defaults to.
 *
 * @author GraviteeSource Team
 */
@Component
public class JdbcDataPlaneConfigHandler implements DataPlaneConfigHandler {

    private static final String BLOCK = "jdbc";

    @Override
    public String blockName() {
        return BLOCK;
    }

    @Override
    public boolean supports(String type) {
        return BLOCK.equals(type);
    }

    @Override
    public void validate(JsonNode configuration) {
        JsonNode block = DataPlaneConfigs.requireBlock(configuration, BLOCK);

        if (DataPlaneConfigs.hasText(block, "uri")) {
            requireHostAndDatabaseInUri(block.get("uri").asText());
        }
    }

    private void requireHostAndDatabaseInUri(String uri) {
        URI parsed = DataPlaneConfigs.parseUri(uri)
                .orElseThrow(() -> new InvalidParameterException("configuration.jdbc.uri is not a valid URI"));
        if (!DataPlaneConfigs.namesAHost(parsed)) {
            throw new InvalidParameterException("configuration.jdbc.uri must name a host, e.g. r2dbc:postgresql://host:5432/my-database");
        }
        if (DataPlaneConfigs.databaseFromUri(parsed) == null) {
            throw new InvalidParameterException("configuration.jdbc.uri must name a database, e.g. r2dbc:postgresql://host:5432/my-database");
        }
    }

    @Override
    public DataPlaneConnectionSummary summarize(JsonNode configuration) {
        JsonNode block = DataPlaneConfigs.block(configuration, BLOCK);
        if (block == null) {
            return DataPlaneConnectionSummary.UNKNOWN;
        }

        if (DataPlaneConfigs.hasText(block, "uri")) {
            Optional<URI> uri = DataPlaneConfigs.parseUri(block.get("uri").asText());
            return uri
                    .map(parsed -> new DataPlaneConnectionSummary(
                            DataPlaneConfigs.databaseFromUri(parsed),
                            hostsOf(DataPlaneConfigs.hostFromUri(parsed))))
                    .orElse(DataPlaneConnectionSummary.UNKNOWN);
        }

        return new DataPlaneConnectionSummary(
                DataPlaneConfigs.text(block, "database"),
                hostsOf(DataPlaneConfigs.hostAndPort(block)));
    }

    private List<String> hostsOf(String host) {
        return host == null ? List.of() : List.of(host);
    }
}
