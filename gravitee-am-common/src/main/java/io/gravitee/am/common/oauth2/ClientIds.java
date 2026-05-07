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
package io.gravitee.am.common.oauth2;

import io.gravitee.am.common.web.UriBuilder;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utilities for client_id values that can be URLs (e.g. OAuth Client ID Metadata Document).
 */
public final class ClientIds {

    static final Pattern URL_SHAPED = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    private ClientIds() {}

    public static boolean isUrlShaped(String clientId) {
        return clientId != null && URL_SHAPED.matcher(clientId).find();
    }

    /**
     * Returns the canonical form of a URL-shaped client_id: scheme and host are lowercased; port,
     * path, and query are preserved. Non-URL values are returned unchanged.
     */
    public static String canonicalize(String clientId) {
        if (!isUrlShaped(clientId)) {
            return clientId;
        }
        try {
            URI uri = UriBuilder.fromHttpUrl(clientId).build();
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String path = uri.getRawPath() != null ? uri.getRawPath() : "";
            String query = uri.getRawQuery();
            return scheme + "://" + host
                    + (port >= 0 ? ":" + port : "")
                    + path
                    + (query != null ? "?" + query : "");
        } catch (Exception ex) {
            return clientId;
        }
    }

    /**
     * Returns true if two client_id values identify the same client for gateway lookup.
     * Non-URL-shaped values use {@link String#equals(Object)}. If either value is URL-shaped,
     * comparison uses {@link #canonicalize(String)} on both sides.
     */
    public static boolean sameForLookup(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (isUrlShaped(a) || isUrlShaped(b)) {
            return canonicalize(a).equals(canonicalize(b));
        }
        return a.equals(b);
    }
}
