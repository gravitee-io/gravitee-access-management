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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Strings;
import io.gravitee.am.model.PluginConfigurableEntity;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.InvalidPluginConfigurationException;
import io.gravitee.am.service.model.PluginConfigurableUpdate;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.quote;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractSensitiveProxy {

    protected PluginService pluginService;
    protected AuditService auditService;
    protected ObjectMapper objectMapper;

    protected static final String DEFAULT_SCHEMA_CONFIG = "{}";

    private static final String PROPERTIES_SCHEMA_KEY = "properties";
    private static final String SENSITIVE_SCHEMA_KEY = "sensitive";
    private static final String SENSITIVE_URI_SCHEMA_KEY = "sensitive-uri";

    protected static final String SENSITIVE_VALUE = "********";
    protected static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile("^(\\*+)$");

    private static final String USERINFO_PATTERN_EXTRACTOR = "^(?:[^/]+://)?(?<userInfo>[^/@]+)@.*";
    private static final Pattern USER_INFO_PATTERN = Pattern.compile(USERINFO_PATTERN_EXTRACTOR);

    private static final ObjectMapper MASKED_VALUE_MAPPER = new ObjectMapper();

    private MaskingMode maskingMode = MaskingMode.ALWAYS;

    /**
     * How this proxy masks the entities it returns; audit events are always masked {@link MaskingMode#ALWAYS}.
     */
    public MaskingMode getMaskingMode() {
        return maskingMode;
    }

    public void setMaskingMode(MaskingMode maskingMode) {
        this.maskingMode = maskingMode;
    }

    /**
     * Parse a user-supplied plugin configuration, surfacing a missing or malformed configuration as a
     * {@link InvalidPluginConfigurationException}.
     */
    protected static JsonNode parseConfiguration(ObjectMapper objectMapper, String configuration) {
        if (configuration == null || configuration.isBlank()) {
            throw InvalidPluginConfigurationException.fromValidationError("configuration is required");
        }
        try {
            return objectMapper.readTree(configuration);
        } catch (JsonProcessingException e) {
            throw InvalidPluginConfigurationException.fromValidationError("configuration is not valid JSON");
        }
    }

    protected void filterSensitiveData(
            JsonNode schemaNode,
            JsonNode configurationNode,
            Consumer<String> configurationSetter
    ) {
        filterSensitiveData(schemaNode, configurationNode, configurationSetter, MaskingMode.ALWAYS);
    }

    protected void filterSensitiveData(
            JsonNode schemaNode,
            JsonNode configurationNode,
            Consumer<String> configurationSetter,
            MaskingMode mode
    ) {
        if (schemaNode.has(PROPERTIES_SCHEMA_KEY) && configurationNode.isObject()) {
            var properties = schemaNode.get(PROPERTIES_SCHEMA_KEY).fields();
            properties.forEachRemaining(entry -> {
                final JsonNode value = configurationNode.get(entry.getKey());
                if (mode == MaskingMode.PRESENT_ONLY && (isSensitive(entry) || isSensitiveUri(entry))
                        && (value == null || value.isNull())) {
                    ((ObjectNode) configurationNode).remove(entry.getKey());
                    return;
                }
                if (isSensitive(entry) && (mode == MaskingMode.ALWAYS || !isEmptyText(value))) {
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
        filterNestedSensitiveData(schemaNode, configurationNode, nestedSchemaPath, nestedConfigPath, MaskingMode.ALWAYS);
    }

    protected void filterNestedSensitiveData(
            JsonNode schemaNode,
            JsonNode configurationNode,
            String nestedSchemaPath,
            String nestedConfigPath,
            MaskingMode mode
    ) {
        var nestedSchemaNode = schemaNode.at(nestedSchemaPath);
        var nestedConfigNode = configurationNode.at(nestedConfigPath);
        // We use an empty Consumer because we only update the nested object
        // The update will be made at top level config
        this.filterSensitiveData(nestedSchemaNode, nestedConfigNode, str -> {
        }, mode);
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

    /**
     * Errors with {@link InvalidParameterException} naming the first sensitive field that holds the mask, bare or
     * as a URI password. {@code mask} returns a configuration as this proxy masks it.
     */
    protected Completable rejectMaskedSensitiveValues(String configuration, Function<String, Single<String>> mask) {
        return Completable.defer(() -> {
            if (Strings.isNullOrEmpty(configuration)) {
                return Completable.complete();
            }
            final JsonNode submitted;
            try {
                submitted = MASKED_VALUE_MAPPER.readTree(configuration);
            } catch (JsonProcessingException e) {
                // Plugin validation rejects malformed JSON.
                return Completable.complete();
            }
            final String placeholder = UUID.randomUUID().toString();
            final JsonNode probe = submitted.deepCopy();
            if (!replaceMaskedValues(probe, placeholder)) {
                return Completable.complete();
            }
            return mask.apply(probe.toString())
                    .flatMapCompletable(masked -> findMaskedSensitiveValue(probe, MASKED_VALUE_MAPPER.readTree(masked), placeholder, "")
                            .map(path -> Completable.error(maskedValueError(path, submitted.at(path))))
                            .orElseGet(Completable::complete));
        });
    }

    /**
     * Replaces, in place, each value or URI password that is the mask with the placeholder.
     *
     * @return whether anything was replaced
     */
    private boolean replaceMaskedValues(JsonNode node, String placeholder) {
        boolean replaced = false;
        if (node instanceof ObjectNode object) {
            Iterator<Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isTextual()) {
                    Optional<String> probed = probedValue(field.getValue().asText(), placeholder);
                    if (probed.isPresent()) {
                        field.setValue(TextNode.valueOf(probed.get()));
                        replaced = true;
                    }
                } else {
                    replaced |= replaceMaskedValues(field.getValue(), placeholder);
                }
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode item = node.get(i);
                if (item.isTextual()) {
                    Optional<String> probed = probedValue(item.asText(), placeholder);
                    if (probed.isPresent()) {
                        ((ArrayNode) node).set(i, TextNode.valueOf(probed.get()));
                        replaced = true;
                    }
                } else {
                    replaced |= replaceMaskedValues(item, placeholder);
                }
            }
        }
        return replaced;
    }

    private Optional<String> probedValue(String value, String placeholder) {
        if (SENSITIVE_VALUE_PATTERN.matcher(value.trim()).matches()) {
            return Optional.of(placeholder);
        }
        return extractPasswordFromUriUserInfo(value)
                .filter(password -> SENSITIVE_VALUE_PATTERN.matcher(password).matches())
                .map(password -> value.replaceFirst(quote(password + "@"), placeholder + "@"));
    }

    /**
     * @return the JSON pointer of the first probed value the masking replaced, if any
     */
    private static Optional<String> findMaskedSensitiveValue(JsonNode probe, JsonNode masked, String placeholder, String path) {
        if (probe == null || masked == null) {
            return Optional.empty();
        }
        if (probe.isTextual()) {
            return probe.asText().contains(placeholder) && masked.isTextual() && !masked.asText().contains(placeholder)
                    ? Optional.of(path) : Optional.empty();
        }
        if (probe.isArray()) {
            for (int i = 0; i < probe.size(); i++) {
                Optional<String> found = findMaskedSensitiveValue(probe.get(i), masked.get(i), placeholder, path + "/" + i);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (probe.isObject()) {
            Iterator<Entry<String, JsonNode>> fields = probe.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> field = fields.next();
                Optional<String> found = findMaskedSensitiveValue(field.getValue(), masked.get(field.getKey()), placeholder,
                        path + "/" + field.getKey().replace("~", "~0").replace("/", "~1"));
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static InvalidParameterException maskedValueError(String path, JsonNode submitted) {
        String problem = SENSITIVE_VALUE_PATTERN.matcher(submitted.asText().trim()).matches()
                ? "holds the masked value '" + SENSITIVE_VALUE + "'"
                : "holds a masked password";
        return new InvalidParameterException("Field 'configuration" + path + "' " + problem
                + "; supply the secret itself. A masked value only keeps an existing secret when updating");
    }

    private static boolean isEmptyText(JsonNode value) {
        return value.isTextual() && value.asText().isEmpty();
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

    /**
     * Filter sensitive data from a plugin-configurable entity.
     *
     * @param entity The entity to filter (must implement PluginConfigurableEntity)
     * @param <T> Entity type that extends PluginConfigurableEntity&lt;T&gt;
     * @return Single containing filtered entity
     */
    protected <T extends PluginConfigurableEntity<T>> Single<T> filterSensitiveData(
            T entity) {
        String type = entity.getType();
        return pluginService.getSchema(type)
                .map(Optional::ofNullable)
                .switchIfEmpty(Maybe.just(Optional.empty()))
                .toSingle()
                .map(schema -> {
                    // Duplicate the object to avoid side effect
                    var filteredEntity = entity.copy();
                    if (schema.isPresent()) {
                        var schemaNode = objectMapper.readTree(schema.get());
                        var configurationNode = objectMapper.readTree(filteredEntity.getConfiguration());

                        // TODO template method to filter additional sensitive data for read operations

                        filterSensitiveData(schemaNode, configurationNode, filteredEntity::setConfiguration);
                    } else {
                        // no schema: remove all the configuration to avoid sensitive data leak
                        // this case may happen when the plugin zip file has been removed from the plugins directory
                        // (set empty object to avoid NullPointer on the UI)
                        filteredEntity.setConfiguration(DEFAULT_SCHEMA_CONFIG);
                    }
                    return filteredEntity;
                });
    }

    /**
     * Filter sensitive data for create operations.
     *
     * @param entity The entity to filter (must implement PluginConfigurableEntity)
     * @param <T> Entity type that extends PluginConfigurableEntity&lt;T&gt;
     * @return Single containing filtered entity
     */
    protected <T extends PluginConfigurableEntity<T>> Single<T> filterSensitiveDataForCreate(T entity) {
        String type = entity.getType();
        return pluginService.getSchema(type)
                .switchIfEmpty(Single.error(() -> getSchemaNotFoundException(type)))
                .map(schema -> {
                    var filteredEntity = entity.copy();
                    var schemaNode = objectMapper.readTree(schema);
                    var configurationNode = objectMapper.readTree(filteredEntity.getConfiguration());

                    // TODO template method to filter additional sensitive data for create operations
                    
                    filterSensitiveData(schemaNode, configurationNode, filteredEntity::setConfiguration);
                    return filteredEntity;
                });
    }

    /**
     * Update sensitive data in an update object.
     *
     * @param updateEntity The update entity containing new configuration (must implement PluginConfigurableUpdate)
     * @param oldEntity The old entity containing existing configuration (must implement PluginConfigurableEntity)
     * @param <T> Update entity type that extends PluginConfigurableUpdate
     * @param <U> Old entity type that extends PluginConfigurableEntity&lt;U&gt;
     * @return Single containing update entity with sensitive data updated
     */
    protected <T extends PluginConfigurableUpdate, U extends PluginConfigurableEntity<U>> Single<T> updateSensitiveData(
            T updateEntity,
            U oldEntity) {
        String type = oldEntity.getType();
        return pluginService.getSchema(type)
                .switchIfEmpty(Single.error(() -> getSchemaNotFoundException(type)))
                .map(schema -> {
                    var updateConfig = objectMapper.readTree(updateEntity.getConfiguration());
                    var oldConfig = objectMapper.readTree(oldEntity.getConfiguration());
                    var schemaConfig = objectMapper.readTree(schema);

                    // TODO template method to filter additional sensitive data for update operations
                    
                    updateSensitiveData(updateConfig, oldConfig, schemaConfig, updateEntity::setConfiguration);
                    return updateEntity;
                });
    }

    protected RuntimeException getSchemaNotFoundException(String type) {
        return new RuntimeException("Schema not found for type: " + type);
    }
}
