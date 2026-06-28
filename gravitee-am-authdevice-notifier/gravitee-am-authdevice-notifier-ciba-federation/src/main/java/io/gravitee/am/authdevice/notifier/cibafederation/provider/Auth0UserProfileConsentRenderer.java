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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import java.util.*;

/**
 * Renders an arbitrary RFC 9396 {@code authorization_details} array into an Auth0 user-profile URN so a
 * downstream Auth0 tenant can display the consent on its Guardian push. It is intentionally
 * schema-agnostic: it assumes no particular consent vocabulary (data clusters, durations, …). It surfaces
 * each entry's {@code type} plus a flattened list of scalar leaf values, so whatever the client requested
 * is shown to the user; richer, vocabulary-aware rendering is the client/IdP's concern.
 */
public class Auth0UserProfileConsentRenderer {

    private static final String USER_PROFILE = "urn:auth0:schemas:authorization-details:v1:user-profile";

    public List<Map<String, Object>> render(List<Map<String, Object>> authorizationDetails, String recipient) {
        String safeRecipient = recipient == null ? "" : recipient;
        List<String> types = new ArrayList<>();
        List<String> leaves = new ArrayList<>();
        if (authorizationDetails != null) {
            for (Map<String, Object> entry : authorizationDetails) {
                if (entry == null) continue;
                Object type = entry.get("type");
                if (type != null) types.add(String.valueOf(type));
                // Collect scalar leaves (excluding the type, shown separately) so the requested details
                // surface regardless of the consent vocabulary.
                collectLeaves(entry, leaves, true);
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("shared_with", prop("Shared with", 1, trunc(safeRecipient)));
        properties.put("requested", prop("Requested access", 2,
                trunc(types.isEmpty() ? "account data" : String.join(", ", types))));
        if (!leaves.isEmpty()) {
            properties.put("details", prop("Details", 3, trunc(String.join(", ", leaves))));
        }

        Map<String, Object> urn = new LinkedHashMap<>();
        urn.put("type", USER_PROFILE);
        urn.put("instruction", trunc(safeRecipient + " requests access to your account data."));
        urn.put("properties", properties);
        return List.of(urn);
    }

    /** Recursively collect scalar (String/Number/Boolean) leaf values. Skips top-level "type" keys. */
    @SuppressWarnings("unchecked")
    private void collectLeaves(Object value, List<String> out, boolean skipType) {
        if (value instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                if (skipType && "type".equals(e.getKey())) continue;
                collectLeaves(e.getValue(), out, false);
            }
        } else if (value instanceof Iterable) {
            for (Object o : (Iterable<Object>) value) collectLeaves(o, out, false);
        } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            out.add(String.valueOf(value));
        }
    }

    private Map<String, Object> prop(String name, int order, String value) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("display", true); p.put("name", name); p.put("display_order", order); p.put("value", value);
        return p;
    }

    private String trunc(String s) { return s == null ? "" : (s.length() > 255 ? s.substring(0, 255) : s); }
}
