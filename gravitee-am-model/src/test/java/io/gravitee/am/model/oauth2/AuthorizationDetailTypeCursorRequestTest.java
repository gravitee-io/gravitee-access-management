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
package io.gravitee.am.model.oauth2;

import io.gravitee.am.model.cursor.CursorRequest.SortDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationDetailTypeCursorRequestTest {

    private static final AuthorizationDetailType LAST = AuthorizationDetailType.builder()
            .id("id-1")
            .type("payment_initiation")
            .name("Payment initiation")
            .build();

    @Test
    void shouldStartOnTheFirstPage() {
        AuthorizationDetailTypeCursorRequest cursor = AuthorizationDetailTypeCursorRequest.initialCursor("name", "desc", 2, "pay*");

        assertTrue(cursor.isFirstPage());
        assertEquals(SortDirection.DESC, cursor.getSortDirection());
        assertEquals("name", cursor.getSortField());
        assertEquals(2, cursor.getPage());
        assertEquals("pay*", cursor.getQuery());
    }

    @Test
    void shouldContinueAfterTheLastTypeWhenSortedByType() {
        AuthorizationDetailTypeCursorRequest next = AuthorizationDetailTypeCursorRequest.initialCursor("type", "ASC", 0, "pay*").next(LAST);

        assertEquals("payment_initiation", next.getLastSortValue());
        assertEquals("id-1", next.getLastId());
        assertEquals(1, next.getPage());
        assertEquals("type", next.getSortField());
        assertEquals("pay*", next.getQuery());
    }

    @Test
    void shouldContinueAfterTheLastNameWhenSortedByName() {
        AuthorizationDetailTypeCursorRequest next = AuthorizationDetailTypeCursorRequest.initialCursor("name", "ASC", 0, null).next(LAST);

        assertEquals("Payment initiation", next.getLastSortValue());
        assertNull(next.getQuery());
    }

    @Test
    void shouldRejectAnUnknownSortField() {
        assertEquals("Invalid sort field: updatedAt",
                assertThrows(IllegalArgumentException.class,
                        () -> AuthorizationDetailTypeCursorRequest.initialCursor("updatedAt", "ASC", 0, null)).getMessage());
    }

    @Test
    void shouldRejectAnUnknownDirection() {
        assertThrows(IllegalArgumentException.class, () -> AuthorizationDetailTypeCursorRequest.initialCursor("type", "sideways", 0, null));
    }
}
