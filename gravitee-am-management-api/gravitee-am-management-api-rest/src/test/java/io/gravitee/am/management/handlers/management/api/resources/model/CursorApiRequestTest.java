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

import io.gravitee.am.model.cursor.CursorRequest.SortDirection;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorApiRequestTest {

    @Test
    void shouldRoundTripAnEncodedCursor() {
        CursorApiRequest decoded = CursorApiRequest.decode(cursor("a-sort-value").encode(), "name", "DESC");

        assertEquals("an-id", decoded.id());
        assertEquals("name", decoded.sortField());
        assertEquals(SortDirection.DESC, decoded.sortDirection());
        assertEquals("a-sort-value", decoded.lastSortValue());
    }

    @Test
    void shouldKeepASortValueContainingTheSeparator() {
        assertEquals("a##sort##value", CursorApiRequest.decode(cursor("a##sort##value").encode(), "name", "DESC").lastSortValue());
    }

    @Test
    void shouldMatchTheDirectionIgnoringCase() {
        assertEquals(SortDirection.DESC, CursorApiRequest.decode(cursor("v").encode(), "name", "desc").sortDirection());
    }

    @Test
    void shouldRejectACursorIssuedForAnotherSortField() {
        assertEquals("Query parameter 'cursor' does not match the sort and dir query parameters",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode(cursor("v").encode(), "type", "DESC")).getMessage());
    }

    @Test
    void shouldRejectACursorIssuedForAnotherDirection() {
        assertEquals("Query parameter 'cursor' does not match the sort and dir query parameters",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode(cursor("v").encode(), "name", "ASC")).getMessage());
    }

    @Test
    void shouldRejectAMissingCursor() {
        assertEquals("Query parameter 'cursor' is required",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode(null, "name", "DESC")).getMessage());
    }

    @Test
    void shouldRejectABlankCursor() {
        assertEquals("Query parameter 'cursor' is required",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode("  ", "name", "DESC")).getMessage());
    }

    @Test
    void shouldRejectACursorThatIsNotBase64() {
        assertEquals("Query parameter 'cursor' is not valid",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode("not base64!", "name", "DESC")).getMessage());
    }

    @Test
    void shouldRejectACursorWithoutTheSort() {
        String idAndValueOnly = Base64.getEncoder().encodeToString("an-id##a-sort-value".getBytes());

        assertEquals("Query parameter 'cursor' is not valid",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode(idAndValueOnly, "name", "DESC")).getMessage());
    }

    private static CursorApiRequest cursor(String lastSortValue) {
        return new CursorApiRequest("an-id", "name", SortDirection.DESC, lastSortValue);
    }
}
