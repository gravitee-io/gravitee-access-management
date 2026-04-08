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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CursorPageTest {

    @Test
    void constructor_copiesData() {
        var mutableList = new java.util.ArrayList<>(List.of("a", "b", "c"));
        var page = new CursorPage<>(mutableList, "cursor123");

        mutableList.add("d");
        assertEquals(3, page.getData().size());
        assertEquals(List.of("a", "b", "c"), page.getData());
    }

    @Test
    void constructor_nullData_returnsEmptyList() {
        var page = new CursorPage<String>(null, null);
        assertNotNull(page.getData());
        assertTrue(page.getData().isEmpty());
    }

    @Test
    void empty_returnsEmptyPage() {
        var page = CursorPage.empty();
        assertTrue(page.getData().isEmpty());
        assertNull(page.getNextCursor());
        assertFalse(page.isHasNext());
        assertNull(page.getTotalCount());
    }

    @Test
    void hasNext_derivedFromNextCursor() {
        var withNext = new CursorPage<>(List.of("a"), "next");
        assertTrue(withNext.isHasNext());
        assertEquals("next", withNext.getNextCursor());

        var withoutNext = new CursorPage<>(List.of("a"), null);
        assertFalse(withoutNext.isHasNext());
        assertNull(withoutNext.getNextCursor());
    }

    @Test
    void data_isImmutable() {
        var page = new CursorPage<>(List.of("a", "b"), "cursor");
        assertThrows(UnsupportedOperationException.class, () -> page.getData().add("c"));
    }

    @Test
    void withTotalCount_returnsNewInstance() {
        var page = new CursorPage<>(List.of("a"), "cursor");
        assertNull(page.getTotalCount());

        var withCount = page.withTotalCount(42L);
        assertEquals(42L, withCount.getTotalCount());
        assertEquals(List.of("a"), withCount.getData());
        assertEquals("cursor", withCount.getNextCursor());
        assertTrue(withCount.isHasNext());

        // Original unchanged
        assertNull(page.getTotalCount());
    }

    @Test
    void totalCount_includedInConstructor() {
        var page = new CursorPage<>(List.of("a"), "cursor", 100L);
        assertEquals(100L, page.getTotalCount());
    }
}
