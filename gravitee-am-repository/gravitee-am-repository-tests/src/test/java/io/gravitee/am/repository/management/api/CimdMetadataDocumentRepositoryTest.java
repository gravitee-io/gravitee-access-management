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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author GraviteeSource Team
 */
public class CimdMetadataDocumentRepositoryTest extends AbstractManagementTest {

    @Autowired
    private CimdMetadataDocumentRepository repository;

    @Test
    public void shouldCreateAndFindById() {
        CimdMetadataDocument doc = newDocument("domain-a", "https://client.example.com/md-" + UUID.randomUUID());
        CimdMetadataDocument created = repository.create(doc).blockingGet();

        TestObserver<CimdMetadataDocument> obs = repository.findById(created.getId()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(d -> d.getClientId().equals(doc.getClientId()));
        obs.assertValue(d -> d.getMetadata().equals(doc.getMetadata()));
    }

    @Test
    public void shouldReturnEmptyWhenFindByDomainAndClientIdMisses() {
        TestObserver<CimdMetadataDocument> obs =
                repository.findByDomainAndClientId("unknown-domain", "https://client.example.com/none").test();
        obs.awaitDone(5, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoValues();
        obs.assertNoErrors();
    }

    @Test
    public void shouldFindByDomainAndClientId() {
        String domainId = "domain-" + UUID.randomUUID();
        String clientId = "https://client.example.com/c-" + UUID.randomUUID();
        CimdMetadataDocument doc = newDocument(domainId, clientId);
        repository.create(doc).blockingGet();

        TestObserver<CimdMetadataDocument> obs = repository.findByDomainAndClientId(domainId, clientId).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(d -> clientId.equals(d.getClientId()) && domainId.equals(d.getDomainId()));
    }

    @Test
    public void shouldFindByDomainOnlyMatchingRows() {
        String domainA = "domain-a-" + UUID.randomUUID();
        String domainB = "domain-b-" + UUID.randomUUID();
        repository.create(newDocument(domainA, "https://a.example.com/1")).blockingGet();
        repository.create(newDocument(domainA, "https://a.example.com/2")).blockingGet();
        repository.create(newDocument(domainB, "https://b.example.com/1")).blockingGet();

        TestSubscriber<CimdMetadataDocument> sub = repository.findByDomain(domainA).test();
        sub.awaitDone(10, TimeUnit.SECONDS);
        sub.assertComplete();
        sub.assertNoErrors();
        sub.assertValueCount(2);
    }

    @Test
    public void shouldAllowSameClientIdInDifferentDomains() {
        String clientId = "https://shared.example.com/md";
        String domain1 = "d1-" + UUID.randomUUID();
        String domain2 = "d2-" + UUID.randomUUID();
        CimdMetadataDocument doc1 = newDocument(domain1, clientId);
        CimdMetadataDocument doc2 = newDocument(domain2, clientId);
        repository.create(doc1).blockingGet();
        repository.create(doc2).blockingGet();

        TestObserver<CimdMetadataDocument> o1 = repository.findByDomainAndClientId(domain1, clientId).test();
        o1.awaitDone(10, TimeUnit.SECONDS);
        o1.assertValue(d -> domain1.equals(d.getDomainId()));

        TestObserver<CimdMetadataDocument> o2 = repository.findByDomainAndClientId(domain2, clientId).test();
        o2.awaitDone(10, TimeUnit.SECONDS);
        o2.assertValue(d -> domain2.equals(d.getDomainId()));
    }

    @Test
    public void shouldUpdateDocument() {
        CimdMetadataDocument doc = newDocument("domain-u-" + UUID.randomUUID(), "https://u.example.com/" + UUID.randomUUID());
        CimdMetadataDocument created = repository.create(doc).blockingGet();

        created.setMetadata("{\"client_id\":\"updated\"}");
        created.setUpdatedAt(new Date());

        TestObserver<CimdMetadataDocument> obs = repository.update(created).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();

        TestObserver<CimdMetadataDocument> read = repository.findById(created.getId()).test();
        read.awaitDone(10, TimeUnit.SECONDS);
        read.assertValue(d -> "{\"client_id\":\"updated\"}".equals(d.getMetadata()));
    }

    @Test
    public void shouldDeleteById() {
        CimdMetadataDocument doc = newDocument("domain-del-" + UUID.randomUUID(), "https://del.example.com/" + UUID.randomUUID());
        CimdMetadataDocument created = repository.create(doc).blockingGet();

        TestObserver<Void> del = repository.delete(created.getId()).test();
        del.awaitDone(10, TimeUnit.SECONDS);
        del.assertComplete();
        del.assertNoErrors();

        TestObserver<CimdMetadataDocument> obs = repository.findById(created.getId()).test();
        obs.awaitDone(5, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoValues();
    }

    @Test
    public void shouldDeleteByDomainAndClientIdOnly() {
        String domainId = "domain-pair-" + UUID.randomUUID();
        String keepClient = "https://keep.example.com/" + UUID.randomUUID();
        String dropClient = "https://drop.example.com/" + UUID.randomUUID();
        CimdMetadataDocument keep = repository.create(newDocument(domainId, keepClient)).blockingGet();
        repository.create(newDocument(domainId, dropClient)).blockingGet();

        TestObserver<Void> del = repository.deleteByDomainAndClientId(domainId, dropClient).test();
        del.awaitDone(10, TimeUnit.SECONDS);
        del.assertComplete();

        TestObserver<CimdMetadataDocument> gone = repository.findByDomainAndClientId(domainId, dropClient).test();
        gone.awaitDone(5, TimeUnit.SECONDS);
        gone.assertNoValues();

        TestObserver<CimdMetadataDocument> still = repository.findById(keep.getId()).test();
        still.awaitDone(10, TimeUnit.SECONDS);
        still.assertValue(d -> keepClient.equals(d.getClientId()));
    }

    @Test
    public void shouldPurgeOnlyExpiredDocuments() {
        String domainId = "domain-purge-" + UUID.randomUUID();
        Date now = new Date();
        Date past = new Date(now.getTime() - 60_000);
        Date future = new Date(now.getTime() + 3600_000);

        CimdMetadataDocument expired = newDocument(domainId, "https://expired.example.com/" + UUID.randomUUID());
        expired.setFetchedAt(past);
        expired.setExpiresAt(past);
        expired.setUpdatedAt(past);
        CimdMetadataDocument expiredCreated = repository.create(expired).blockingGet();

        CimdMetadataDocument fresh = newDocument(domainId, "https://fresh.example.com/" + UUID.randomUUID());
        fresh.setFetchedAt(now);
        fresh.setExpiresAt(future);
        fresh.setUpdatedAt(now);
        CimdMetadataDocument freshCreated = repository.create(fresh).blockingGet();

        TestObserver<Void> purge = repository.purgeExpiredData().test();
        purge.awaitDone(10, TimeUnit.SECONDS);
        purge.assertComplete();
        purge.assertNoErrors();

        TestObserver<CimdMetadataDocument> expiredObs = repository.findById(expiredCreated.getId()).test();
        expiredObs.awaitDone(5, TimeUnit.SECONDS);
        expiredObs.assertNoValues();

        TestObserver<CimdMetadataDocument> freshObs = repository.findById(freshCreated.getId()).test();
        freshObs.awaitDone(10, TimeUnit.SECONDS);
        freshObs.assertValue(d -> freshCreated.getClientId().equals(d.getClientId()));
    }

    private static CimdMetadataDocument newDocument(String domainId, String clientId) {
        Date now = new Date();
        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setDomainId(domainId);
        doc.setClientId(clientId);
        doc.setMetadata("{\"client_id\":\"" + clientId + "\"}");
        doc.setFetchedAt(now);
        doc.setExpiresAt(new Date(now.getTime() + 3600_000));
        doc.setUpdatedAt(now);
        return doc;
    }
}
