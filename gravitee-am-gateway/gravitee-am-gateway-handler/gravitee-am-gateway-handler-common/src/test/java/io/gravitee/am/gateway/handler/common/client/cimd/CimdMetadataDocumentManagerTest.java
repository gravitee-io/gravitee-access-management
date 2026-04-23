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

import io.gravitee.am.common.event.CimdMetadataEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CimdMetadataDocumentManagerTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String CLIENT_URL = "https://client.example.com/metadata";

    @Mock
    private CimdMetadataDocumentService cimdMetadataDocumentService;

    @Mock
    private EventManager eventManager;

    private CimdMetadataDocumentManager manager;
    private Domain domain;

    @Before
    public void setUp() {
        domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setName("test-domain");

        CIMDSettings settings = new CIMDSettings();
        manager = new CimdMetadataDocumentManager(settings);

        ReflectionTestUtils.setField(manager, "domain", domain);
        ReflectionTestUtils.setField(manager, "eventManager", eventManager);
        ReflectionTestUtils.setField(manager, "cimdMetadataDocumentService", cimdMetadataDocumentService);
    }

    @Test
    public void shouldReturnEmptyWhenCacheMiss() {
        Optional<CimdMetadataDocument> result = manager.get(CLIENT_URL);
        assertFalse(result.isPresent());
    }

    @Test
    public void shouldReturnDocumentAfterPut() {
        CimdMetadataDocument doc = validDocument();
        manager.put(CLIENT_URL, doc);

        Optional<CimdMetadataDocument> result = manager.get(CLIENT_URL);
        assertTrue(result.isPresent());
    }

    @Test
    public void shouldReturnDocumentAfterPutRaw() {
        final String rawMetadata = "{\"client_id\":\"" + CLIENT_URL + "\",\"redirect_uris\":[\"https://cb.example.com\"]}";
        manager.put(CLIENT_URL, new io.vertx.core.json.JsonObject(rawMetadata), Duration.ofHours(1));

        Optional<CimdMetadataDocument> result = manager.get(CLIENT_URL);
        assertTrue(result.isPresent());
        assertEquals(CLIENT_URL, result.get().getClientId());
        assertEquals(DOMAIN_ID, result.get().getDomainId());
        assertEquals(rawMetadata, result.get().getMetadata());
        assertNotNull(result.get().getExpiresAt());
        assertTrue(result.get().getExpiresAt().after(new Date()));
    }

    @Test
    public void shouldReturnEmptyForExpiredDocument() {
        CimdMetadataDocument doc = expiredDocument();
        manager.put(CLIENT_URL, doc);

        Optional<CimdMetadataDocument> result = manager.get(CLIENT_URL);
        assertFalse(result.isPresent());
    }

    @Test
    public void shouldEvictOnUpdateEvent() {
        manager.put(CLIENT_URL, validDocument());

        manager.onEvent(new SimpleEvent<>(CimdMetadataEvent.UPDATE, payload(CLIENT_URL)));

        assertFalse(manager.get(CLIENT_URL).isPresent());
    }

    @Test
    public void shouldEvictOnUndeployEvent() {
        manager.put(CLIENT_URL, validDocument());

        manager.onEvent(new SimpleEvent<>(CimdMetadataEvent.UNDEPLOY, payload(CLIENT_URL)));

        assertFalse(manager.get(CLIENT_URL).isPresent());
    }

    @Test
    public void shouldNotEvictForDifferentDomain() {
        manager.put(CLIENT_URL, validDocument());

        Payload foreignPayload = new Payload(CLIENT_URL, ReferenceType.DOMAIN, "other-domain", io.gravitee.am.common.event.Action.UPDATE);
        manager.onEvent(new SimpleEvent<>(CimdMetadataEvent.UPDATE, foreignPayload));

        assertTrue(manager.get(CLIENT_URL).isPresent());
    }

    @Test
    public void shouldPreLoadNonExpiredDocumentsOnAfterPropertiesSet() {
        CimdMetadataDocument doc = validDocument();
        when(cimdMetadataDocumentService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(doc));

        manager.afterPropertiesSet();

        verify(cimdMetadataDocumentService).findByDomain(DOMAIN_ID);
        assertTrue(manager.get(CLIENT_URL).isPresent());
    }

    @Test
    public void shouldNotLoadExpiredDocumentsDuringPreLoad() {
        CimdMetadataDocument doc = expiredDocument();
        when(cimdMetadataDocumentService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(doc));

        manager.afterPropertiesSet();

        assertFalse(manager.get(CLIENT_URL).isPresent());
    }

    // --- logo cache tests ---

    @Test
    public void shouldReturnEmptyLogoWhenNotCached() {
        assertFalse(manager.getLogoByClientId(CLIENT_URL).isPresent());
    }

    @Test
    public void shouldReturnLogoAfterPutLogo() {
        byte[] bytes = {(byte) 0x89, 0x50, 0x4E, 0x47};
        manager.put(CLIENT_URL, validDocument());
        manager.putLogo(CLIENT_URL, new CachedLogo(bytes, "image/png", 3600L));

        assertTrue(manager.getLogoByClientId(CLIENT_URL).isPresent());
        assertEquals("image/png", manager.getLogoByClientId(CLIENT_URL).get().contentType());
        assertEquals(3600L, manager.getLogoByClientId(CLIENT_URL).get().maxAgeSeconds());
    }

    @Test
    public void shouldNotStoreLogo_WhenNoMetadataEntryExists() {
        // putLogo is a no-op without a corresponding metadata entry
        manager.putLogo(CLIENT_URL, new CachedLogo(new byte[]{1, 2, 3}, "image/png", 3600L));

        assertFalse(manager.getLogoByClientId(CLIENT_URL).isPresent());
    }

    @Test
    public void shouldEvictLogoOnUpdateEvent() {
        manager.put(CLIENT_URL, validDocument());
        manager.putLogo(CLIENT_URL, new CachedLogo(new byte[]{1, 2, 3}, "image/png", 3600L));

        manager.onEvent(new SimpleEvent<>(CimdMetadataEvent.UPDATE, payload(CLIENT_URL)));

        assertFalse(manager.get(CLIENT_URL).isPresent());
        assertFalse(manager.getLogoByClientId(CLIENT_URL).isPresent());
    }

    @Test
    public void shouldEvictLogoOnUndeployEvent() {
        manager.put(CLIENT_URL, validDocument());
        manager.putLogo(CLIENT_URL, new CachedLogo(new byte[]{1, 2, 3}, "image/png", 3600L));

        manager.onEvent(new SimpleEvent<>(CimdMetadataEvent.UNDEPLOY, payload(CLIENT_URL)));

        assertFalse(manager.get(CLIENT_URL).isPresent());
        assertFalse(manager.getLogoByClientId(CLIENT_URL).isPresent());
    }

    private CimdMetadataDocument validDocument() {
        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setClientId(CLIENT_URL);
        doc.setDomainId(DOMAIN_ID);
        doc.setMetadata("{\"client_id\":\"" + CLIENT_URL + "\",\"redirect_uris\":[\"https://cb.example.com\"]}");
        doc.setFetchedAt(new Date());
        doc.setExpiresAt(new Date(System.currentTimeMillis() + 86400_000));
        return doc;
    }

    private CimdMetadataDocument expiredDocument() {
        CimdMetadataDocument doc = validDocument();
        doc.setExpiresAt(new Date(System.currentTimeMillis() - 1000));
        return doc;
    }

    private Payload payload(String clientId) {
        return new Payload(clientId, ReferenceType.DOMAIN, DOMAIN_ID, io.gravitee.am.common.event.Action.UPDATE);
    }

    // --- detectMimeType ---

    @Test
    public void detectMimeType_shouldReturnPngWhenMagicMatches() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertEquals("image/png", CimdMetadataDocumentManager.detectMimeType(png));
    }

    @Test
    public void detectMimeType_shouldReturnJpegWhenMagicMatches() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        assertEquals("image/jpeg", CimdMetadataDocumentManager.detectMimeType(jpeg));
    }

    @Test
    public void detectMimeType_shouldReturnGifWhenMagicMatches() {
        byte[] gif = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
        assertEquals("image/gif", CimdMetadataDocumentManager.detectMimeType(gif));
    }

    @Test
    public void detectMimeType_shouldReturnWebpWhenRiffWebpHeaderPresent() {
        // RIFF + 4-byte size + "WEBP"
        byte[] webp = {
                0x52, 0x49, 0x46, 0x46,
                0x00, 0x00, 0x00, 0x00,
                0x57, 0x45, 0x42, 0x50
        };
        assertEquals("image/webp", CimdMetadataDocumentManager.detectMimeType(webp));
    }

    @Test
    public void detectMimeType_shouldReturnSvgXmlWhenBufferStartsWithSvgTag() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        assertEquals("image/svg+xml", CimdMetadataDocumentManager.detectMimeType(svg));
    }

    @Test
    public void detectMimeType_shouldReturnSvgXmlWhenBufferStartsWithXmlDeclaration() {
        byte[] svg = "<?xml version=\"1.0\"?><svg></svg>".getBytes(StandardCharsets.UTF_8);
        assertEquals("image/svg+xml", CimdMetadataDocumentManager.detectMimeType(svg));
    }

    @Test
    public void detectMimeType_shouldReturnSvgXmlWhenUtf8BomPrecedesSvgTag() {
        byte[] inner = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] combined = new byte[bom.length + inner.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(inner, 0, combined, bom.length, inner.length);
        assertEquals("image/svg+xml", CimdMetadataDocumentManager.detectMimeType(combined));
    }

    @Test
    public void detectMimeType_shouldReturnSvgXmlWhenUtf8BomAndWhitespacePrecedeXmlDeclaration() {
        byte[] inner = "<?xml version=\"1.0\"?><svg/>".getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] prefix = "\n  ".getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[bom.length + prefix.length + inner.length];
        int p = 0;
        System.arraycopy(bom, 0, combined, p, bom.length);
        p += bom.length;
        System.arraycopy(prefix, 0, combined, p, prefix.length);
        p += prefix.length;
        System.arraycopy(inner, 0, combined, p, inner.length);
        assertEquals("image/svg+xml", CimdMetadataDocumentManager.detectMimeType(combined));
    }

    @Test
    public void detectMimeType_shouldReturnSvgXmlWhenLeadingWhitespaceBeforeSvgTag() {
        byte[] svg = "\r\n  <svg></svg>".getBytes(StandardCharsets.UTF_8);
        assertEquals("image/svg+xml", CimdMetadataDocumentManager.detectMimeType(svg));
    }

    @Test
    public void detectMimeType_shouldReturnOctetStreamWhenSvgTagPrefixIncomplete() {
        assertEquals("application/octet-stream", CimdMetadataDocumentManager.detectMimeType("<sv".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void detectMimeType_shouldReturnOctetStreamWhenXmlDeclarationIncomplete() {
        assertEquals("application/octet-stream", CimdMetadataDocumentManager.detectMimeType("<?xm".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void detectMimeType_shouldReturnOctetStreamWhenXmlDeclarationIsNotLowercase() {
        byte[] notLowercase = "<?XML version=\"1.0\"?><svg/>".getBytes(StandardCharsets.UTF_8);
        assertEquals("application/octet-stream", CimdMetadataDocumentManager.detectMimeType(notLowercase));
    }

    @Test
    public void detectMimeType_shouldReturnJpegForMinimumTwoByteSignature() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8};
        assertEquals("image/jpeg", CimdMetadataDocumentManager.detectMimeType(jpeg));
    }

    @Test
    public void detectMimeType_shouldReturnOctetStreamForEmptyBuffer() {
        assertEquals("application/octet-stream", CimdMetadataDocumentManager.detectMimeType(new byte[0]));
    }

    @Test
    public void detectMimeType_shouldReturnOctetStreamForUnknownContent() {
        byte[] random = {0x00, 0x01, 0x02, 0x03, 0x04};
        assertEquals("application/octet-stream", CimdMetadataDocumentManager.detectMimeType(random));
    }

    @Test
    public void detectMimeType_shouldReturnOctetStreamWhenWebpBufferTooShort() {
        // RIFF prefix but fewer than 12 bytes — no format matches
        byte[] tooShort = {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42};
        assertEquals("application/octet-stream", CimdMetadataDocumentManager.detectMimeType(tooShort));
    }
}
