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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingAuthStoreTest {

    @Test
    void put_get_remove_roundtrip() {
        PendingAuthStore store = new PendingAuthStore();
        var rec = new PendingAuthStore.Pending("tid1", "stateJwt", "R1", 5, 9_999_999_999L, "hashABC",
                "http://gw/ciba/callback");
        store.put(rec);
        assertSame(rec, store.get("tid1"));
        assertEquals("R1", store.get("tid1").authReqId());
        assertEquals("http://gw/ciba/callback", store.get("tid1").callbackUrl());
        store.remove("tid1");
        assertNull(store.get("tid1"));
    }

    @Test
    void expiry_check_uses_epoch_seconds() {
        var expired = new PendingAuthStore.Pending("t", "s", "r", 5, 1L, "h", "http://gw/cb");
        assertTrue(expired.isExpired(2L));
        assertTrue(expired.isExpired(1L));   // now == expiresAt → expired (the >= boundary)
        assertFalse(expired.isExpired(0L));
    }
}
