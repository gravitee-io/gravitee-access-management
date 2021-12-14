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

package io.gravitee.am.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractSensitiveProxy {

    private static final String PROPERTIES_SCHEMA_KEY = "properties";
    private static final String SENSITIVE_SCHEMA_KEY = "sensitive";

    protected static final String SENSITIVE_VALUE = "********";
    protected static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile("^(\\*+)$");

    protected void filterSensitiveData(
            JsonNode schemaNode,
            JsonNode configurationNode,
            Consumer<String> configurationSetter
    ) {
        if (schemaNode.has(PROPERTIES_SCHEMA_KEY)) {
            var properties = schemaNode.get(PROPERTIES_SCHEMA_KEY).fields();
            properties.forEachRemaining(entry -> {
                if (isSensitive(entry)) {
                    ((ObjectNode) configurationNode).put(entry.getKey(), SENSITIVE_VALUE);
                }
            });
            configurationSetter.accept(configurationNode.toString());
        }
    }

    protected void filterNestedSensitiveData(
            JsonNode schemaNode,
            JsonNode configurationNode,
            String nestedSchemaPath,
            String nestedConfigPath
    ) {
        var nestedSchemaNode = schemaNode.at(nestedSchemaPath);
        var nestedConfigNode = configurationNode.at(nestedConfigPath);
        // We use an empty Consumer because we only update the nested object
        // The update will be made at top level config
        this.filterSensitiveData(nestedSchemaNode, nestedConfigNode, str -> {
        });
    }

    protected void updateSensitiveData(
            JsonNode updatedConfigurationNode,
            JsonNode oldConfigurationNode,
            JsonNode schemaNode,
            Consumer<String> configurationUpdater
    ) {
        if (schemaNode.has(PROPERTIES_SCHEMA_KEY)) {
            var properties = schemaNode.get(PROPERTIES_SCHEMA_KEY).fields();
            properties.forEachRemaining(setOldConfigurationIfNecessary(updatedConfigurationNode, oldConfigurationNode));
            configurationUpdater.accept(updatedConfigurationNode.toString());
        }
    }

    protected void updateNestedSensitiveData(
            JsonNode updatedConfigurationNode,
            JsonNode oldConfigurationNode,
            JsonNode schemaNode,
            String nestedSchemaPath,
            String nestedConfigPath
    ) {
        var nestedUpdatedConfigNode = updatedConfigurationNode.at(nestedConfigPath);
        var nestedOldConfigNode = oldConfigurationNode.at(nestedConfigPath);
        var nestedSchemaNode = schemaNode.at(nestedSchemaPath);
        // We use an empty Consumer because we only update the nested object
        // The update will be made at top level config
        this.updateSensitiveData(nestedUpdatedConfigNode, nestedOldConfigNode, nestedSchemaNode, str -> {
        });
    }

    protected Consumer<Entry<String, JsonNode>> setOldConfigurationIfNecessary(JsonNode updatedConfigurationNode, JsonNode oldConfigurationNode) {
        return entry -> {
            if (isSensitive(entry) && !valueIsUpdatable(updatedConfigurationNode, entry)) {
                ((ObjectNode) updatedConfigurationNode).set(entry.getKey(), oldConfigurationNode.get(entry.getKey()));
            }
        };
    }

    protected boolean isSensitive(Entry<String, JsonNode> entry) {
        return entry.getValue().has(SENSITIVE_SCHEMA_KEY) && entry.getValue().get(SENSITIVE_SCHEMA_KEY).asBoolean();
    }

    protected boolean valueIsUpdatable(JsonNode configNode, Entry<String, JsonNode> entry) {
        if (configNode == null) {
            return true;
        }
        final JsonNode valueNode = configNode.get(entry.getKey());
        var value = valueNode == null ? null : valueNode.asText();
        var safeValue = value == null ? "" : value.trim();
        return !SENSITIVE_VALUE_PATTERN.matcher(safeValue).matches();
    }
}
