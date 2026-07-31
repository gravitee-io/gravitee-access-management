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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * @author GraviteeSource Team
 */
final class DataPlaneConfigs {

    private DataPlaneConfigs() {
    }

    static JsonNode requireBlock(JsonNode configuration, String blockName) {
        JsonNode block = configuration == null ? null : configuration.get(blockName);
        if (block == null || !block.isObject()) {
            throw new InvalidParameterException("configuration must contain a '" + blockName + "' object");
        }
        return block;
    }

    static JsonNode block(JsonNode configuration, String blockName) {
        JsonNode block = configuration == null ? null : configuration.get(blockName);
        return block != null && block.isObject() ? block : null;
    }

    static boolean hasText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank();
    }

    static String text(JsonNode node, String field) {
        return hasText(node, field) ? node.get(field).asText() : null;
    }

    /**
     * Re-parses the scheme-specific part so an opaque r2dbc uri becomes hierarchical.
     */
    static Optional<URI> parseUri(String uri) {
        try {
            URI parsed = new URI(uri);
            return parsed.isOpaque() ? Optional.of(new URI(parsed.getSchemeSpecificPart())) : Optional.of(parsed);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * The database segment of a connection uri, i.e. {@code /my-database} becomes {@code my-database}.
     */
    static String databaseFromUri(URI uri) {
        String path = uri.getPath();
        return path == null || path.length() <= 1 ? null : path.substring(1);
    }

    /**
     * getHost() only, never getAuthority(), which would return {@code user:password@host}.
     */
    static String hostFromUri(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        return uri.getPort() > 0 ? host + ":" + uri.getPort() : host;
    }

    static String hostAndPort(JsonNode node) {
        String host = text(node, "host");
        if (host == null) {
            return null;
        }
        JsonNode port = node.get("port");
        return port == null || port.isNull() ? host : host + ":" + port.asText();
    }
}
