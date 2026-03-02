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
package io.gravitee.am.authorizationengine.api.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.authorizationengine.api.audit.AuthorizationAuditEvent;

/**
 * Encodes and decodes {@link WsMessage} instances to/from JSON strings.
 * <p>
 * Wire format uses a {@code "type"} discriminator field:
 * <ul>
 *   <li>{@code bundle_check} → {@link WsMessage.BundleCheck}</li>
 *   <li>{@code bundle_current} → {@link WsMessage.BundleCurrent}</li>
 *   <li>{@code bundle_update} → {@link WsMessage.BundleUpdate}</li>
 *   <li>{@code audit_event} → {@link WsMessage.AuditEvent}</li>
 *   <li>{@code error} → {@link WsMessage.Error}</li>
 * </ul>
 *
 * @author GraviteeSource Team
 */
public final class WsMessageCodec {

    static final String TYPE_BUNDLE_CHECK = "bundle_check";
    static final String TYPE_BUNDLE_CURRENT = "bundle_current";
    static final String TYPE_BUNDLE_UPDATE = "bundle_update";
    static final String TYPE_AUDIT_EVENT = "audit_event";
    static final String TYPE_ERROR = "error";

    private final ObjectMapper objectMapper;

    public WsMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WsMessageCodec() {
        this(new ObjectMapper());
    }

    /**
     * Encode a {@link WsMessage} to a JSON string.
     */
    public String encode(WsMessage msg) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            switch (msg) {
                case WsMessage.BundleCheck bc -> {
                    node.put("type", TYPE_BUNDLE_CHECK);
                    node.put("version", bc.version());
                }
                case WsMessage.BundleCurrent ignored -> {
                    node.put("type", TYPE_BUNDLE_CURRENT);
                }
                case WsMessage.BundleUpdate bu -> {
                    node.put("type", TYPE_BUNDLE_UPDATE);
                    node.put("version", bu.version());
                    node.put("policy", bu.policy());
                    node.put("data", bu.data());
                    node.put("schema", bu.schema());
                }
                case WsMessage.AuditEvent ae -> {
                    node.put("type", TYPE_AUDIT_EVENT);
                    node.set("event", objectMapper.valueToTree(ae.event()));
                }
                case WsMessage.Error err -> {
                    node.put("type", TYPE_ERROR);
                    node.put("code", err.code());
                    node.put("message", err.message());
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode WsMessage", e);
        }
    }

    /**
     * Decode a JSON string to a {@link WsMessage}.
     *
     * @throws IllegalArgumentException if the JSON is malformed or has an unknown type
     */
    public WsMessage decode(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = requiredText(root, "type");
            return switch (type) {
                case TYPE_BUNDLE_CHECK -> new WsMessage.BundleCheck(root.path("version").asInt(0));
                case TYPE_BUNDLE_CURRENT -> new WsMessage.BundleCurrent();
                case TYPE_BUNDLE_UPDATE -> new WsMessage.BundleUpdate(
                        root.path("version").asInt(0),
                        textOrNull(root, "policy"),
                        textOrNull(root, "data"),
                        textOrNull(root, "schema")
                );
                case TYPE_AUDIT_EVENT -> {
                    JsonNode eventNode = root.path("event");
                    if (eventNode.isMissingNode() || eventNode.isNull()) {
                        throw new IllegalArgumentException("Missing 'event' field in audit_event message");
                    }
                    AuthorizationAuditEvent event = objectMapper.treeToValue(eventNode, AuthorizationAuditEvent.class);
                    yield new WsMessage.AuditEvent(event);
                }
                case TYPE_ERROR -> new WsMessage.Error(
                        textOrNull(root, "code"),
                        textOrNull(root, "message")
                );
                default -> throw new IllegalArgumentException("Unknown WsMessage type: " + type);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode WsMessage: " + e.getMessage(), e);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return child.asText();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return (child.isMissingNode() || child.isNull()) ? null : child.asText();
    }
}
