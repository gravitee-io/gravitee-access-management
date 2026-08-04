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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Requires host and database explicitly: {@code MongoFactoryImpl} silently defaults them to
 * localhost/gravitee-am, which would put a data plane on the shared store.
 *
 * @author GraviteeSource Team
 */
@Component
public class MongoDataPlaneConfigHandler implements DataPlaneConfigHandler {

    private static final String BLOCK = "mongodb";

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

        // an empty uri is ignored by MongoFactoryImpl, so it falls through to the host/dbname form
        if (DataPlaneConfigs.hasText(block, "uri")) {
            requireHostAndDatabaseInUri(block.get("uri").asText());
            return;
        }

        if (!DataPlaneConfigs.hasText(block, "dbname")) {
            throw new InvalidParameterException("configuration.mongodb requires either 'uri' or 'dbname', otherwise the data plane would silently use the default 'gravitee-am' database");
        }
        if (!DataPlaneConfigs.hasText(block, "host") && !hasFirstServerHost(block)) {
            throw new InvalidParameterException("configuration.mongodb requires 'host' or 'servers[0].host', otherwise the data plane would silently use 'localhost'");
        }
    }

    @Override
    public DataPlaneConnectionSummary summarise(JsonNode configuration) {
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

        return new DataPlaneConnectionSummary(DataPlaneConfigs.text(block, "dbname"), declaredHosts(block));
    }

    private List<String> declaredHosts(JsonNode block) {
        JsonNode servers = block.get("servers");
        if (servers != null && servers.isArray()) {
            List<String> hosts = new ArrayList<>();
            servers.forEach(server -> {
                String host = DataPlaneConfigs.hostAndPort(server);
                if (host != null) {
                    hosts.add(host);
                }
            });
            return hosts;
        }
        return hostsOf(DataPlaneConfigs.hostAndPort(block));
    }

    private List<String> hostsOf(String host) {
        return host == null ? List.of() : List.of(host);
    }

    private void requireHostAndDatabaseInUri(String uri) {
        URI parsed = DataPlaneConfigs.parseUri(uri)
                .orElseThrow(() -> new InvalidParameterException("configuration.mongodb.uri is not a valid URI"));
        if (!DataPlaneConfigs.namesAHost(parsed)) {
            throw new InvalidParameterException("configuration.mongodb.uri must name a host, e.g. mongodb://host:27017/my-database, otherwise the data plane would silently use 'localhost'");
        }
        if (DataPlaneConfigs.databaseFromUri(parsed) == null) {
            throw new InvalidParameterException("configuration.mongodb.uri must name a database, e.g. mongodb://host:27017/my-database");
        }
    }

    private boolean hasFirstServerHost(JsonNode block) {
        JsonNode servers = block.get("servers");
        return servers != null && servers.isArray() && !servers.isEmpty()
                && DataPlaneConfigs.hasText(servers.get(0), "host");
    }
}
