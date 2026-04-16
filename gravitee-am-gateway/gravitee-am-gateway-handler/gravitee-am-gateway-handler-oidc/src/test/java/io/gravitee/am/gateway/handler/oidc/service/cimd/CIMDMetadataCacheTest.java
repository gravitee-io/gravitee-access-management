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
package io.gravitee.am.gateway.handler.oidc.service.cimd;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CIMDMetadataCacheTest {

    @Test
    void should_return_empty_on_cache_miss() {
        CIMDMetadataCache cache = new CIMDMetadataCache(100, 3600);
        Optional<CIMDMetadataDocument> result = cache.get("domain1", "https://agent.example.com");
        assertTrue(result.isEmpty());
    }

    @Test
    void should_return_cached_document() {
        CIMDMetadataCache cache = new CIMDMetadataCache(100, 3600);
        CIMDMetadataDocument doc = new CIMDMetadataDocument();
        doc.setSoftwareId("blueprint-123");

        cache.put("domain1", "https://agent.example.com", doc);
        Optional<CIMDMetadataDocument> result = cache.get("domain1", "https://agent.example.com");

        assertTrue(result.isPresent());
        assertEquals("blueprint-123", result.get().getSoftwareId());
    }

    @Test
    void should_scope_by_domain() {
        CIMDMetadataCache cache = new CIMDMetadataCache(100, 3600);
        CIMDMetadataDocument doc = new CIMDMetadataDocument();
        doc.setSoftwareId("blueprint-123");

        cache.put("domain1", "https://agent.example.com", doc);

        // Same URI, different domain — should miss
        Optional<CIMDMetadataDocument> result = cache.get("domain2", "https://agent.example.com");
        assertTrue(result.isEmpty());
    }

    @Test
    void should_evict_when_max_entries_exceeded() {
        CIMDMetadataCache cache = new CIMDMetadataCache(2, 3600);
        CIMDMetadataDocument doc1 = new CIMDMetadataDocument();
        doc1.setSoftwareId("first");
        CIMDMetadataDocument doc2 = new CIMDMetadataDocument();
        doc2.setSoftwareId("second");
        CIMDMetadataDocument doc3 = new CIMDMetadataDocument();
        doc3.setSoftwareId("third");

        cache.put("d", "uri1", doc1);
        cache.put("d", "uri2", doc2);
        cache.put("d", "uri3", doc3); // should evict uri1

        assertTrue(cache.get("d", "uri1").isEmpty());
        assertTrue(cache.get("d", "uri3").isPresent());
        assertEquals(2, cache.size());
    }

    @Test
    void should_expire_entries_after_ttl() {
        // TTL of 0 seconds means entries expire immediately
        CIMDMetadataCache cache = new CIMDMetadataCache(100, 0);
        CIMDMetadataDocument doc = new CIMDMetadataDocument();
        doc.setSoftwareId("blueprint-123");

        cache.put("domain1", "https://agent.example.com", doc);

        // Should be expired immediately
        Optional<CIMDMetadataDocument> result = cache.get("domain1", "https://agent.example.com");
        assertTrue(result.isEmpty());
    }

    @Test
    void should_invalidate_entry() {
        CIMDMetadataCache cache = new CIMDMetadataCache(100, 3600);
        CIMDMetadataDocument doc = new CIMDMetadataDocument();
        cache.put("domain1", "https://agent.example.com", doc);

        cache.invalidate("domain1", "https://agent.example.com");

        assertTrue(cache.get("domain1", "https://agent.example.com").isEmpty());
    }

    @Test
    void should_clear_all_entries() {
        CIMDMetadataCache cache = new CIMDMetadataCache(100, 3600);
        cache.put("d1", "uri1", new CIMDMetadataDocument());
        cache.put("d2", "uri2", new CIMDMetadataDocument());

        cache.clear();
        assertEquals(0, cache.size());
    }
}
