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
package io.gravitee.am.model.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Represents a cursor-based pagination request. Encodes/decodes opaque cursors that
 * carry the keyset position (last sort value + last ID) for efficient keyset pagination.
 *
 * <p>Cursor format (internal, Base64-encoded JSON):
 * <pre>{"v":1,"sv":"value","id":"abc123","d":"ASC","f":"name"}</pre>
 * <ul>
 *   <li>{@code v} - version for forward compatibility</li>
 *   <li>{@code sv} - last item's sort field value</li>
 *   <li>{@code id} - last item's ID (tiebreaker for deterministic ordering)</li>
 *   <li>{@code d} - sort direction</li>
 *   <li>{@code f} - sort field name (optional, for multi-field sort support)</li>
 * </ul>
 */
public class CursorRequest {

    private static final int CURRENT_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 50;

    private final String lastSortValue;
    private final String lastId;
    private final SortDirection direction;
    private final String sortField;
    private final int limit;

    private CursorRequest(String lastSortValue, String lastId, SortDirection direction, String sortField, int limit) {
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        this.lastSortValue = lastSortValue;
        this.lastId = lastId;
        this.direction = direction;
        this.sortField = sortField;
        this.limit = Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /**
     * Create a request for the first page (no cursor).
     */
    public static CursorRequest firstPage(SortDirection direction, int limit) {
        return new CursorRequest(null, null, direction, null, limit);
    }

    /**
     * Create a request for the first page with a specific sort field.
     */
    public static CursorRequest firstPage(String sortField, SortDirection direction, int limit) {
        return new CursorRequest(null, null, direction, sortField, limit);
    }

    /**
     * Decode an opaque cursor string, or return a first-page request if the cursor is null/empty.
     */
    public static CursorRequest from(String encodedCursor, SortDirection defaultDirection, int limit) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return firstPage(defaultDirection, limit);
        }
        return decode(encodedCursor, limit);
    }

    /**
     * Decode an opaque cursor string, or return a first-page request with a specific sort field.
     */
    public static CursorRequest from(String encodedCursor, String defaultSortField, SortDirection defaultDirection, int limit) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return firstPage(defaultSortField, defaultDirection, limit);
        }
        return decode(encodedCursor, limit);
    }

    /**
     * Encode a cursor from the last item's sort value, ID, and sort field.
     */
    public static String encode(String sortValue, String id, SortDirection direction, String sortField) {
        try {
            var payload = new CursorPayload(CURRENT_VERSION, sortValue, id, direction.name(), sortField);
            byte[] json = MAPPER.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    /**
     * Encode a cursor without a sort field (backwards compatible).
     */
    public static String encode(String sortValue, String id, SortDirection direction) {
        return encode(sortValue, id, direction, null);
    }

    private static CursorRequest decode(String encodedCursor, int limit) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedCursor.getBytes(StandardCharsets.UTF_8));
            CursorPayload payload = MAPPER.readValue(json, CursorPayload.class);
            if (payload.v() != CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported cursor version: " + payload.v());
            }
            if (payload.sv() == null || payload.id() == null || payload.d() == null) {
                throw new IllegalArgumentException("Invalid cursor: missing required fields");
            }
            SortDirection dir = SortDirection.valueOf(payload.d());
            return new CursorRequest(payload.sv(), payload.id(), dir, payload.f(), limit);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor format", e);
        }
    }

    /**
     * Parse a sort parameter string (e.g., "name", "-updatedAt") into field + direction.
     * Prefix with "-" for descending. Returns a CursorRequest for the first page.
     */
    /**
     * Parse a sort parameter string (e.g., "name", "-updatedAt") into field + direction.
     * Prefix with "-" for descending. If a cursor is provided, its embedded sort field and
     * direction are used (the cursor is authoritative for continuation pages).
     *
     * @throws IllegalArgumentException if the cursor's sort field conflicts with the requested sort
     */
    public static CursorRequest fromSortParam(String sortParam, String defaultSortField, SortDirection defaultDirection, String afterCursor, int limit) {
        String sortField = defaultSortField;
        SortDirection direction = defaultDirection;

        if (sortParam != null && !sortParam.isBlank()) {
            String parsed = sortParam.startsWith("-") ? sortParam.substring(1) : sortParam;
            if (parsed.isBlank()) {
                // sortParam was just "-" with no field name — use defaults
                parsed = defaultSortField;
            }
            sortField = parsed;
            direction = sortParam.startsWith("-") ? SortDirection.DESC : SortDirection.ASC;
        }

        if (afterCursor != null && !afterCursor.isBlank()) {
            CursorRequest decoded = decode(afterCursor, limit);
            String cursorSortField = decoded.sortField != null ? decoded.sortField : sortField;
            // Reject conflicting sort field between cursor and query param
            if (decoded.sortField != null && !decoded.sortField.equals(sortField)) {
                throw new IllegalArgumentException(
                        "Sort field '" + sortField + "' conflicts with cursor sort field '" + decoded.sortField
                                + "'. Start a new pagination (omit the cursor) when changing sort order.");
            }
            return new CursorRequest(decoded.lastSortValue, decoded.lastId, decoded.direction, cursorSortField, limit);
        }

        return firstPage(sortField, direction, limit);
    }

    public boolean isFirstPage() {
        return lastSortValue == null && lastId == null;
    }

    public String getLastSortValue() {
        return lastSortValue;
    }

    public String getLastId() {
        return lastId;
    }

    public SortDirection getDirection() {
        return direction;
    }

    public String getSortField() {
        return sortField;
    }

    public int getLimit() {
        return limit;
    }

    public static int getDefaultLimit() {
        return DEFAULT_LIMIT;
    }

    public static int getMaxLimit() {
        return MAX_LIMIT;
    }

    public enum SortDirection {
        ASC, DESC;

        public boolean isAscending() {
            return this == ASC;
        }
    }

    private record CursorPayload(
            @JsonProperty("v") int v,
            @JsonProperty("sv") String sv,
            @JsonProperty("id") String id,
            @JsonProperty("d") String d,
            @JsonProperty("f") String f
    ) {
        @JsonCreator
        CursorPayload {}
    }
}
