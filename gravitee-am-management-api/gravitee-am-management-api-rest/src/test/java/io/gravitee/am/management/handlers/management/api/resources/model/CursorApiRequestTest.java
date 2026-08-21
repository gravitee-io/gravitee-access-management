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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorApiRequestTest {

    @Test
    void shouldRoundTripAnEncodedCursor() {
        CursorApiRequest decoded = CursorApiRequest.decode(new CursorApiRequest("an-id", "a-sort-value").encode());

        assertEquals("an-id", decoded.id());
        assertEquals("a-sort-value", decoded.lastSortValue());
    }

    @Test
    void shouldRejectAMissingCursor() {
        assertEquals("Query parameter 'cursor' is required",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode(null)).getMessage());
    }

    @Test
    void shouldRejectABlankCursor() {
        assertEquals("Query parameter 'cursor' is required",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode("  ")).getMessage());
    }

    @Test
    void shouldRejectACursorThatIsNotBase64() {
        assertEquals("Query parameter 'cursor' is not valid",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode("not base64!")).getMessage());
    }

    @Test
    void shouldRejectACursorWithoutBothParts() {
        String noSeparator = java.util.Base64.getEncoder().encodeToString("just-an-id".getBytes());

        assertEquals("Query parameter 'cursor' is not valid",
                assertThrows(IllegalArgumentException.class, () -> CursorApiRequest.decode(noSeparator)).getMessage());
    }
}
