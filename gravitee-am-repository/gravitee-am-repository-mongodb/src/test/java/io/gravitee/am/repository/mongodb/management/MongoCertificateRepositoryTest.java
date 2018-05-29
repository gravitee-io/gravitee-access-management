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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.Certificate;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoCertificateRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private CertificateRepository certificateRepository;

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create certificate
        Certificate certificate = new Certificate();
        certificate.setName("testName");
        certificate.setDomain("testDomain");
        certificateRepository.create(certificate).blockingGet();

        // fetch certificates
        TestObserver<Set<Certificate>> testObserver = certificateRepository.findByDomain("testDomain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(certificates -> certificates.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create certificate
        Certificate certificate = new Certificate();
        certificate.setName("testName");
        Certificate certificateCreated = certificateRepository.create(certificate).blockingGet();

        // fetch certificate
        TestObserver<Certificate> testObserver = certificateRepository.findById(certificateCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals("testName"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        certificateRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Certificate certificate = new Certificate();
        certificate.setName("testName");

        TestObserver<Certificate> testObserver = certificateRepository.create(certificate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(domainCreated -> domainCreated.getName().equals(certificate.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create certificate
        Certificate certificate = new Certificate();
        certificate.setName("testName");
        Certificate certificateCreated = certificateRepository.create(certificate).blockingGet();

        // update certificate
        Certificate updatedCertificate = new Certificate();
        updatedCertificate.setId(certificateCreated.getId());
        updatedCertificate.setName("testUpdatedName");

        TestObserver<Certificate> testObserver = certificateRepository.update(updatedCertificate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals(updatedCertificate.getName()));

    }

    @Test
    public void testDelete() throws TechnicalException {
        // create certificate
        Certificate certificate = new Certificate();
        certificate.setName("testName");
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
