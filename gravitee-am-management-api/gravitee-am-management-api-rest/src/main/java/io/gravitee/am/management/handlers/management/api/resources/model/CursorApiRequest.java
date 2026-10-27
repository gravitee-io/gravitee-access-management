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
package io.gravitee.am.management.handlers.management.api.resources.model;

import io.gravitee.am.model.cursor.CursorRequest;
import io.gravitee.am.model.cursor.CursorRequest.SortDirection;

import java.util.Base64;

public record CursorApiRequest(String id, String sortField, SortDirection sortDirection, String lastSortValue) {
    private final static String SEPARATOR = "##";

    public static CursorApiRequest of(CursorRequest next) {
        return new CursorApiRequest(next.getLastId(), next.getSortField(), next.getSortDirection(), next.getLastSortValue());
    }

    public String encode(){
        Base64.Encoder encoder = Base64.getEncoder();
        String value = String.join(SEPARATOR, id, sortField, sortDirection.name(), lastSortValue);
        return encoder.encodeToString(value.getBytes());
    }

    /**
     * Fails when the cursor was issued for another sort field or direction than the request's.
     */
    public static CursorApiRequest decode(String value, String sortField, String direction){
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'cursor' is required");
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Query parameter 'cursor' is not valid", e);
        }
        // the sort value comes last and may itself contain the separator
        String[] split = decoded.split(SEPARATOR, 4);
        if (split.length != 4) {
            throw new IllegalArgumentException("Query parameter 'cursor' is not valid");
        }
        if (!split[1].equals(sortField) || !split[2].equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("Query parameter 'cursor' does not match the sort and dir query parameters");
        }
        return new CursorApiRequest(split[0], split[1], SortDirection.valueOf(split[2]), split[3]);
    }
}
