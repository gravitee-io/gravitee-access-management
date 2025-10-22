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
package io.gravitee.am.management.handlers.management.api.sort;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SortParamConverterTest {

    private final SortParamConverter converter = new SortParamConverter();

    @Test
    void shouldParseValidAscendingValue() {
        SortParam result = converter.fromString("updatedAt.asc");

        assertNotNull(result);
        assertEquals("updatedAt", result.getSortBy());
        assertTrue(result.isAscending());
    }

    @Test
    void shouldParseValidDescendingValue() {
        SortParam result = converter.fromString("createdAt.desc");

        assertNotNull(result);
        assertEquals("createdAt", result.getSortBy());
        assertFalse(result.isAscending());
    }

    @Test
    void shouldHandleMissingDirectionAsAscending() {
        SortParam result = converter.fromString("id");

        assertNotNull(result);
        assertEquals("id", result.getSortBy());
        assertTrue(result.isAscending());
    }

    @Test
    void shouldReturnEmptyWhenInputIsNull() {
        SortParam result = converter.fromString(null);

        assertTrue(result.isEmpty());
    }


    @Test
    void shouldConvertSortParamToStringAscending() {
        SortParam sortParam = new SortParam("updatedAt", true);

        String result = converter.toString(sortParam);

        assertEquals("updatedAt.asc", result);
    }

    @Test
    void shouldConvertSortParamToStringDescending() {
        SortParam sortParam = new SortParam("createdAt", false);

        String result = converter.toString(sortParam);

        assertEquals("createdAt.desc", result);
    }

    @Test
    void shouldReturnNullWhenSortParamIsEmpty() {
        SortParam sortParam = SortParam.empty();

        String result = converter.toString(sortParam);

        assertNull(result);
    }
}