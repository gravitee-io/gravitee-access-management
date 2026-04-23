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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class ClientIdsTest {

    @Test
    public void isUrlShaped_acceptsHttpAndHttpsAtStart() {
        assertTrue(ClientIds.isUrlShaped("http://example.com"));
        assertTrue(ClientIds.isUrlShaped("https://example.com"));
        assertTrue(ClientIds.isUrlShaped("HTTP://EXAMPLE.COM/p"));
        assertTrue(ClientIds.isUrlShaped("HTTPS://EXAMPLE.COM/p"));
    }

    @Test
    public void isUrlShaped_rejectsNullEmptyAndNonUrl() {
        assertFalse(ClientIds.isUrlShaped(null));
        assertFalse(ClientIds.isUrlShaped(""));
        assertFalse(ClientIds.isUrlShaped("opaque-client"));
        assertFalse(ClientIds.isUrlShaped("urn:foo:bar"));
    }

    @Test
    public void isUrlShaped_rejectsFtpAndRequiresSchemeAtStart() {
        assertFalse(ClientIds.isUrlShaped("ftp://files.example/"));
        assertFalse(ClientIds.isUrlShaped(" not-http://trailing"));
        assertFalse(ClientIds.isUrlShaped("prefix-https://host"));
    }

    @Test
    public void isUrlShaped_rejectsHttpWithoutSlashSlash() {
        assertFalse(ClientIds.isUrlShaped("http:"));
        assertFalse(ClientIds.isUrlShaped("https"));
    }

    @Test
    public void canonicalize_returnsNullWhenInputNull() {
        assertNull(ClientIds.canonicalize(null));
    }

    @Test
    public void canonicalize_nonUrl_returnsUnchanged() {
        assertEquals("my-app", ClientIds.canonicalize("my-app"));
        assertEquals("", ClientIds.canonicalize(""));
    }

    @Test
    public void canonicalize_lowercasesSchemeAndHost_preservesPathAndQuery() {
        assertEquals(
                "https://client.example.com/metadata?x=1&y=a+b",
                ClientIds.canonicalize("HTTPS://CLIENT.EXAMPLE.COM/metadata?x=1&y=a+b"));
    }

    @Test
    public void canonicalize_includesPortWhenNotDefault() {
        assertEquals("http://example.com:8080/oauth", ClientIds.canonicalize("HTTP://EXAMPLE.COM:8080/oauth"));
    }

    @Test
    public void canonicalize_emptyPath_usesEmptyPathString() {
        assertEquals("https://h.example", ClientIds.canonicalize("https://H.EXAMPLE"));
    }

    @Test
    public void sameForLookup_bothUrl_equivalentWhenHostsDifferInCase() {
        assertTrue(ClientIds.sameForLookup(
                "https://a.example.com/r",
                "HTTPS://A.EXAMPLE.COM/r"));
    }

    @Test
    public void sameForLookup_httpAndHttps_notEqual() {
        assertFalse(ClientIds.sameForLookup("http://x.example/p", "https://x.example/p"));
    }

    @Test
    public void sameForLookup_pathsMustMatch() {
        assertFalse(ClientIds.sameForLookup("https://x.example/a", "https://x.example/b"));
    }

    @Test
    public void sameForLookup_trailingPathSlashMatters() {
        assertFalse(ClientIds.sameForLookup("https://x.example", "https://x.example/"));
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
    public void sameForLookup_mixedUrlAndOpaque_doesNotMatch() {
        assertFalse(ClientIds.sameForLookup("https://x.example.com/", "not-a-url"));
    }

    @Test
    public void sameForLookup_bothUrl_oneOpaqueButLooksLikePath_stillUrlBranch() {
        // If one side is URL-shaped, we compare via canonicalize on both; opaque string unchanged.
        assertFalse(ClientIds.sameForLookup("https://a.example.com/", "a.example.com"));
    }

    @Test
    public void canonicalize_urnStyle_notUrlShaped_unchanged() {
        assertEquals("urn:acme:client", ClientIds.canonicalize("urn:acme:client"));
    }

    @Test
    public void canonicalize_rawPathPercentEncoding_stableUnderCaseOnlyChange() {
        String lowerHost = "https://ns.example/oidc%2Freg";
        String upperHost = "HTTPS://ns.example/oidc%2Freg";
        assertEquals(ClientIds.canonicalize(lowerHost), ClientIds.canonicalize(upperHost));
    }
}
