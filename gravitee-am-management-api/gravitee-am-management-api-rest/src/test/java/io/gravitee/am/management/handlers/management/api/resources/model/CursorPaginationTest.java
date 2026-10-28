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
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CursorPaginationTest {

    private static final String BASE_PATH = "/organizations/o/environments/e/domains/d/things";

    @Test
    void shouldKeepAPageSizeUpToTheMaximum() {
        assertEquals(1, CursorPagination.pageSize(1));
        assertEquals(100, CursorPagination.pageSize(100));
    }

    @Test
    void shouldReduceAPageSizeAboveTheMaximum() {
        assertEquals(100, CursorPagination.pageSize(200));
    }

    @Test
    void shouldStartFromTheFirstPageWithoutACursor() {
        CursorRequest first = new CursorRequest(null, null, SortDirection.ASC, "name", 2);

        assertEquals(first, CursorPagination.cursorRequest(null, "name", "ASC", () -> first, cursor -> {
            throw new AssertionError("no cursor to decode");
        }));
    }

    @Test
    void shouldContinueFromTheDecodedCursor() {
        String cursor = new CursorApiRequest("an-id", "name", SortDirection.DESC, "a-value").encode();

        CursorRequest request = CursorPagination.cursorRequest(cursor, "name", "DESC",
                () -> {
                    throw new AssertionError("a cursor was given");
                },
                decoded -> new CursorRequest(decoded.lastSortValue(), decoded.id(), decoded.sortDirection(), "name", 1));

        assertEquals("an-id", request.getLastId());
        assertEquals("a-value", request.getLastSortValue());
    }

    @Test
    void shouldRejectABlankCursor() {
        assertThrows(IllegalArgumentException.class, () -> CursorPagination.cursorRequest("", "name", "ASC",
                () -> new CursorRequest(null, null, SortDirection.ASC, "name", 0),
                decoded -> new CursorRequest(null, null, SortDirection.ASC, "name", 0)));
    }

    @Test
    void shouldHaveNoNextCursorOnTheLastPage() {
        assertNull(CursorPagination.nextCursorPath(null, mock(UriInfo.class), BASE_PATH));
    }

    @Test
    void shouldLinkTheNextPageOnTheBasePathKeepingTheQuery() {
        CursorRequest next = new CursorRequest("a-value", "an-id", SortDirection.ASC, "name", 3);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUriBuilder()).thenReturn(UriBuilder.fromUri("https://host/custom-entrypoint" + BASE_PATH)
                .queryParam("page", 2)
                .queryParam("q", "abc")
                .queryParam("cursor", "previous"));

        String path = CursorPagination.nextCursorPath(next, uriInfo, BASE_PATH);

        assertTrue(path.startsWith(BASE_PATH + "?"), path);
        assertTrue(path.contains("page=3"), path);
        assertTrue(path.contains("q=abc"), path);
        assertTrue(path.contains("cursor=" + URLEncoder.encode(CursorApiRequest.of(next).encode(), StandardCharsets.UTF_8)), path);
    }
}
