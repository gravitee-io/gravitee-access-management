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
package io.gravitee.am.gateway.handler.common.client.cimd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class CimdClientIdsTest {

    @Test
    public void canonicalize_lowercasesSchemeAndHost_preservesPathAndQuery() {
        assertEquals(
                "https://client.example.com/metadata?x=1",
                ClientIds.canonicalize("HTTPS://CLIENT.EXAMPLE.COM/metadata?x=1"));
    }

    @Test
    public void canonicalize_opaqueId_unchanged() {
        assertEquals("my-app-client", ClientIds.canonicalize("my-app-client"));
    }

    @Test
    public void sameForLookup_urlShaped_equivalentAfterCanonicalization() {
        assertTrue(ClientIds.sameForLookup(
                "https://a.example.com/r",
                "HTTPS://A.EXAMPLE.COM/r"));
    }

    @Test
    public void sameForLookup_opaque_usesStringEquality() {
        assertTrue(ClientIds.sameForLookup("opaque-id", "opaque-id"));
        assertFalse(ClientIds.sameForLookup("opaque-id", "Opaque-Id"));
    }

    @Test
    public void sameForLookup_nullHandling() {
        assertTrue(ClientIds.sameForLookup(null, null));
        assertFalse(ClientIds.sameForLookup(null, "a"));
        assertFalse(ClientIds.sameForLookup("a", null));
    }

    @Test
    public void sameForLookup_mixedUrlAndOpaque_noAccidentalMatch() {
        assertFalse(ClientIds.sameForLookup("https://x.example.com/", "not-a-url"));
    }
}
