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
        manager.put(CLIENT_URL, rawMetadata, Duration.ofHours(1));

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
}
