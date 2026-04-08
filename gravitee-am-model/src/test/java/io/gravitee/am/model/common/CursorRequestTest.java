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

import io.gravitee.am.model.common.CursorRequest.SortDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CursorRequestTest {

    @Test
    void firstPage_shouldHaveNullCursorFields() {
        var req = CursorRequest.firstPage(SortDirection.ASC, 50);
        assertTrue(req.isFirstPage());
        assertNull(req.getLastSortValue());
        assertNull(req.getLastId());
        assertEquals(SortDirection.ASC, req.getDirection());
        assertEquals(50, req.getLimit());
    }

    @Test
    void encodeDecode_roundTrip() {
        String encoded = CursorRequest.encode("2026-04-01T12:00:00Z", "abc123", SortDirection.DESC);
        assertNotNull(encoded);
        assertFalse(encoded.contains("=")); // URL-safe, no padding

        var req = CursorRequest.from(encoded, SortDirection.ASC, 25);
        assertFalse(req.isFirstPage());
        assertEquals("2026-04-01T12:00:00Z", req.getLastSortValue());
        assertEquals("abc123", req.getLastId());
        assertEquals(SortDirection.DESC, req.getDirection());
        assertEquals(25, req.getLimit());
    }

    @Test
    void from_nullCursor_returnsFirstPage() {
        var req = CursorRequest.from(null, SortDirection.ASC, 50);
        assertTrue(req.isFirstPage());
        assertEquals(SortDirection.ASC, req.getDirection());
    }

    @Test
    void from_blankCursor_returnsFirstPage() {
        var req = CursorRequest.from("   ", SortDirection.DESC, 30);
        assertTrue(req.isFirstPage());
        assertEquals(SortDirection.DESC, req.getDirection());
    }

    @Test
    void from_invalidCursor_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                CursorRequest.from("not-a-valid-cursor", SortDirection.ASC, 50));
    }

    @Test
    void limit_isClamped() {
        var tooHigh = CursorRequest.firstPage(SortDirection.ASC, 999);
        assertEquals(CursorRequest.getMaxLimit(), tooHigh.getLimit());

        var tooLow = CursorRequest.firstPage(SortDirection.ASC, 0);
        assertEquals(1, tooLow.getLimit());

        var negative = CursorRequest.firstPage(SortDirection.ASC, -5);
        assertEquals(1, negative.getLimit());
    }

    @Test
    void encode_withSpecialCharacters() {
        String encoded = CursorRequest.encode("My Domain (test)", "id-with-dashes", SortDirection.ASC);
        var req = CursorRequest.from(encoded, SortDirection.ASC, 50);
        assertEquals("My Domain (test)", req.getLastSortValue());
        assertEquals("id-with-dashes", req.getLastId());
    }

    @Test
    void encode_withUnicodeCharacters() {
        String encoded = CursorRequest.encode("domaine-francais", "id-123", SortDirection.ASC);
        var req = CursorRequest.from(encoded, SortDirection.ASC, 50);
        assertEquals("domaine-francais", req.getLastSortValue());
    }

    @Test
    void sortDirection_isAscending() {
        assertTrue(SortDirection.ASC.isAscending());
        assertFalse(SortDirection.DESC.isAscending());
    }
}
