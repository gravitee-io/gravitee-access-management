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

import io.gravitee.am.model.Certificate;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateRepositoryTest extends AbstractManagementTest {

    @Autowired
    private CertificateRepository certificateRepository;

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create certificate
        Certificate certificate = buildCertificate();
        certificate.setDomain("DomainTestFindByDomain");

        certificateRepository.create(certificate).blockingGet();

        // fetch certificates
        TestSubscriber<Certificate> testSubscriber = certificateRepository.findByDomain("DomainTestFindByDomain").test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    private Certificate  buildCertificate() {
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        data.put("key2", "value2");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        metadata.put("key2", data);
        metadata.put("file", "TEST_BYTE_ARRAY".getBytes());

        Certificate certificate = new Certificate();
        certificate.setName("testName");
        certificate.setDomain("testDomain");
        certificate.setConfiguration("{configuration in json format}");
        certificate.setType("PEM");
        certificate.setCreatedAt(new Date());
        certificate.setUpdatedAt(new Date());
        certificate.setExpiresAt(new Date());
        certificate.setMetadata(metadata);
        return certificate;
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create certificate
        Certificate certificate = buildCertificate();
        certificate.setName("testFindById");
        Certificate certificateCreated = certificateRepository.create(certificate).blockingGet();

        // fetch certificate
        TestObserver<Certificate> testObserver = certificateRepository.findById(certificateCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals("testFindById"));
        testObserver.assertValue(d -> d.getId().equals(certificateCreated.getId()));
        testObserver.assertValue(d -> d.getConfiguration().equals(certificate.getConfiguration()));
        testObserver.assertValue(d -> d.getDomain().equals(certificate.getDomain()));
        testObserver.assertValue(d -> d.getType().equals(certificate.getType()));
        testObserver.assertValue(d -> certificate.getMetadata().size() == d.getMetadata().size());
        testObserver.assertValue(d -> certificate.getMetadata().keySet().containsAll(d.getMetadata().keySet()));
        testObserver.assertValue(d -> !certificate.getMetadata().containsKey("file") || certificate.getMetadata().get("file") instanceof byte[]);
        testObserver.assertValue(d -> d.getExpiresAt() != null);
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        certificateRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Certificate certificate = buildCertificate();

        TestObserver<Certificate> testObserver = certificateRepository.create(certificate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(domainCreated -> domainCreated.getName().equals(certificate.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create certificate
        Certificate certificate = buildCertificate();
        Certificate certificateCreated = certificateRepository.create(certificate).blockingGet();

        // update certificate
        Certificate updatedCertificate = new Certificate(certificateCreated);
        updatedCertificate.setId(certificateCreated.getId());
        updatedCertificate.setName("testUpdatedName");

        TestObserver<Certificate> testObserver = certificateRepository.update(updatedCertificate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals(updatedCertificate.getName()));
    }

    @Test
    public void testUpdateExpirationDate() throws TechnicalException {
        Certificate certificate = buildCertificate();
        Certificate certificateCreated = certificateRepository.create(certificate).blockingGet();

        final Date exp = new Date(Instant.now().plus(5, ChronoUnit.DAYS).toEpochMilli());

        final TestObserver<Void> testObserver = certificateRepository.updateExpirationDate(certificateCreated.getId(), exp).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();

        TestObserver<Certificate> testUpdatedValueObs = certificateRepository.findById(certificateCreated.getId()).test();
        testUpdatedValueObs.awaitTerminalEvent();
        testUpdatedValueObs.assertComplete();
        testUpdatedValueObs.assertNoErrors();
        testUpdatedValueObs.assertValue(d -> d.getId().equals(certificateCreated.getId()));
        testUpdatedValueObs.assertValue(d -> d.getExpiresAt().equals(exp));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create certificate
        Certificate certificate = buildCertificate();
        Certificate certificateCreated = certificateRepository.create(certificate).blockingGet();

        // fetch certificate
        TestObserver<Certificate> testObserver = certificateRepository.findById(certificateCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals(certificateCreated.getName()));

        // delete domain
        TestObserver testObserver1 = certificateRepository.delete(certificateCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch domain
        certificateRepository.findById(certificateCreated.getId()).test().assertEmpty();
    }
}
