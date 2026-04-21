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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.CimdMetadataDocumentRepository;
import io.gravitee.am.service.EventService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CimdMetadataDocumentServiceImplTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String CLIENT_URL = "https://client.example.com/metadata";
    private static final String METADATA_JSON = "{\"client_id\":\"" + CLIENT_URL + "\"}";

    @Mock
    private CimdMetadataDocumentRepository repository;

    @Mock
    private EventService eventService;

    @InjectMocks
    private CimdMetadataDocumentServiceImpl service;

    private Domain domain;

    @Before
    public void setUp() {
        domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setName("test-domain");

        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "eventService", eventService);

        when(eventService.create(any(Event.class), any(Domain.class))).thenReturn(Single.just(new Event()));
    }

    @Test
    public void shouldCreateDocumentWhenNotExists() {
        when(repository.findByDomainAndClientId(DOMAIN_ID, CLIENT_URL)).thenReturn(Maybe.empty());
        when(repository.create(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        TestObserver<CimdMetadataDocument> obs = service.upsert(domain, CLIENT_URL, METADATA_JSON, Duration.ofHours(1)).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);

        ArgumentCaptor<CimdMetadataDocument> captor = ArgumentCaptor.forClass(CimdMetadataDocument.class);
        verify(repository).create(captor.capture());
        assertNotNull(captor.getValue().getExpiresAt());
        assertNotNull(captor.getValue().getFetchedAt());
        verify(repository, never()).update(any());
        verify(eventService).create(any(Event.class), any(Domain.class));
    }

    @Test
    public void shouldUpdateDocumentWhenAlreadyExists() {
        CimdMetadataDocument existing = existingDocument();
        when(repository.findByDomainAndClientId(DOMAIN_ID, CLIENT_URL)).thenReturn(Maybe.just(existing));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        TestObserver<CimdMetadataDocument> obs = service.upsert(domain, CLIENT_URL, METADATA_JSON, Duration.ofHours(2)).test();

        obs.assertComplete();
        obs.assertNoErrors();
        verify(repository).update(any());
        verify(repository, never()).create(any());
        verify(eventService).create(any(Event.class), any(Domain.class));
    }

    @Test
    public void shouldDeleteAndPublishUndeployEvent() {
        when(repository.deleteByDomainAndClientId(DOMAIN_ID, CLIENT_URL)).thenReturn(Completable.complete());

        TestObserver<Void> obs = service.delete(DOMAIN_ID, CLIENT_URL).test();

        obs.assertComplete();
        obs.assertNoErrors();
        verify(repository).deleteByDomainAndClientId(DOMAIN_ID, CLIENT_URL);
        verify(eventService).create(any(Event.class), any(Domain.class));
    }

    @Test
    public void shouldReturnEmptyWhenDocumentNotFoundInDb() {
        when(repository.findByDomainAndClientId(DOMAIN_ID, CLIENT_URL)).thenReturn(Maybe.empty());

        TestObserver<CimdMetadataDocument> obs = service.findByDomainAndClientId(DOMAIN_ID, CLIENT_URL).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertNoValues();
    }

    @Test
    public void shouldReturnDocumentWhenFoundInDb() {
        CimdMetadataDocument doc = existingDocument();
        when(repository.findByDomainAndClientId(DOMAIN_ID, CLIENT_URL)).thenReturn(Maybe.just(doc));

        TestObserver<CimdMetadataDocument> obs = service.findByDomainAndClientId(DOMAIN_ID, CLIENT_URL).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
    }

    private CimdMetadataDocument existingDocument() {
        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setId("existing-id");
        doc.setDomainId(DOMAIN_ID);
        doc.setClientId(CLIENT_URL);
        doc.setMetadata(METADATA_JSON);
        doc.setFetchedAt(new Date());
        doc.setExpiresAt(new Date(System.currentTimeMillis() + 3600_000));
        doc.setUpdatedAt(new Date());
        return doc;
    }
}
