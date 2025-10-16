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
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Strings;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.quote;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractSensitiveProxy {

    protected static final String DEFAULT_SCHEMA_CONFIG = "{}";

    private static final String PROPERTIES_SCHEMA_KEY = "properties";
    private static final String SENSITIVE_SCHEMA_KEY = "sensitive";
    private static final String SENSITIVE_URI_SCHEMA_KEY = "sensitive-uri";

    protected static final String SENSITIVE_VALUE = "********";
    protected static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile("^(\\*+)$");

    private static final String USERINFO_PATTERN_EXTRACTOR = "^(?:[^/]+://)?(?<userInfo>[^/@]+)@.*";
    private static final Pattern USER_INFO_PATTERN = Pattern.compile(USERINFO_PATTERN_EXTRACTOR);
    protected void filterSensitiveData(
            JsonNode schemaNode,
            JsonNode configurationNode,
            Consumer<String> configurationSetter
    ) {
        if (schemaNode.has(PROPERTIES_SCHEMA_KEY) && configurationNode.isObject()) {
            var properties = schemaNode.get(PROPERTIES_SCHEMA_KEY).fields();
            properties.forEachRemaining(entry -> {
                if (isSensitive(entry)) {
                    ((ObjectNode) configurationNode).put(entry.getKey(), SENSITIVE_VALUE);
                }
                if (isSensitiveUri(entry) && configurationNode.get(entry.getKey()) instanceof TextNode) {
                    final String uri = configurationNode.get(entry.getKey()).asText();
                    extractPasswordFromUriUserInfo(uri).ifPresent(passwordToHide ->
                        ((ObjectNode) configurationNode).put(entry.getKey(), uri.replaceFirst(quote(passwordToHide+"@"), SENSITIVE_VALUE+"@"))
                    );
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
            if (isSensitive(entry) && !valueIsUpdatable(updatedConfigurationNode, entry) && updatedConfigurationNode.isObject()) {
                ((ObjectNode) updatedConfigurationNode).set(entry.getKey(), oldConfigurationNode.get(entry.getKey()));
            }
            if (isSensitiveUri(entry) && updatedConfigurationNode.isObject()) {
                final JsonNode newUri = updatedConfigurationNode.get(entry.getKey());
                if (newUri != null && !Strings.isNullOrEmpty(newUri.asText())) {
                    final JsonNode oldUriNode = oldConfigurationNode.get(entry.getKey());
                    if (oldUriNode != null && !Strings.isNullOrEmpty(oldUriNode.asText())) {
                        extractPasswordFromUriUserInfo(newUri.asText()).ifPresent(newPassword -> {
                            if (SENSITIVE_VALUE_PATTERN.matcher(newPassword).matches()) {
                                extractPasswordFromUriUserInfo(oldUriNode.asText()).or(() -> Optional.of(""))
                                        .ifPresent(oldPwd -> ((ObjectNode) updatedConfigurationNode).put(entry.getKey(), newUri.asText().replaceFirst(quote(newPassword+"@"), oldPwd+"@")));
                            }
                        });
                    }
                }
            }
        };
    }

    protected final Optional<String> extractPasswordFromUriUserInfo(String uri) {
        Optional<String> result = Optional.empty();
        Matcher matcher = USER_INFO_PATTERN.matcher(uri);
        if (matcher.find()) {
            final String userInfo = matcher.group("userInfo");
            final int index = userInfo.indexOf(":");
            if (index != -1) {
                final String pwd = userInfo.substring(index+1);
                result = Optional.of(pwd.trim());
            }
        }
        return result;
    }

    protected boolean isSensitive(Entry<String, JsonNode> entry) {
        return entry.getValue().has(SENSITIVE_SCHEMA_KEY) && entry.getValue().get(SENSITIVE_SCHEMA_KEY).asBoolean();
    }

    protected boolean isSensitiveUri(Entry<String, JsonNode> entry) {
        return entry.getValue().has(SENSITIVE_URI_SCHEMA_KEY) && entry.getValue().get(SENSITIVE_URI_SCHEMA_KEY).asBoolean();
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
