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
package io.gravitee.am.dataplane.api.repository;

import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author GraviteeSource Team
 */
public class CertificateCredentialRepositoryTest extends AbstractDataPlaneTest {

    @Autowired
    private CertificateCredentialRepository certificateCredentialRepository;

    @Test
    public void testFindById() {
        // create credential
        CertificateCredential credential = buildCertificateCredential();
        CertificateCredential credentialCreated = certificateCredentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<CertificateCredential> testObserver = certificateCredentialRepository.findById(credentialCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getId().equals(credentialCreated.getId()));
        testObserver.assertValue(c -> c.getCertificateThumbprint().equals(credential.getCertificateThumbprint()));
        testObserver.assertValue(c -> c.getCertificatePem().equals(credential.getCertificatePem()));
        testObserver.assertValue(c -> c.getReferenceId().equals(credential.getReferenceId()));
        testObserver.assertValue(c -> c.getReferenceType().equals(credential.getReferenceType()));
        testObserver.assertValue(c -> c.getUserId().equals(credential.getUserId()));
        testObserver.assertValue(c -> c.getUsername().equals(credential.getUsername()));
    }

    @Test
    public void testFindByUsername() {
        // create credential
        CertificateCredential credential = buildCertificateCredential();
        CertificateCredential credentialCreated = certificateCredentialRepository.create(credential).blockingGet();

        // fetch credential
        var testObserver = certificateCredentialRepository.findByUsername(ReferenceType.DOMAIN, credential.getReferenceId(), credential.getUsername()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getId().equals(credentialCreated.getId()));
        testObserver.assertValue(c -> c.getCertificateThumbprint().equals(credential.getCertificateThumbprint()));
        testObserver.assertValue(c -> c.getCertificatePem().equals(credential.getCertificatePem()));
        testObserver.assertValue(c -> c.getReferenceId().equals(credential.getReferenceId()));
        testObserver.assertValue(c -> c.getReferenceType().equals(credential.getReferenceType()));
        testObserver.assertValue(c -> c.getUserId().equals(credential.getUserId()));
        testObserver.assertValue(c -> c.getUsername().equals(credential.getUsername()));
    }

    @Test
    public void testNotFoundById() {
        var observer = certificateCredentialRepository.findById("test").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        CertificateCredential credential = buildCertificateCredential();

        TestObserver<CertificateCredential> testObserver = certificateCredentialRepository.create(credential).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCertificateThumbprint().equals(credential.getCertificateThumbprint()));
    }

    @Test
    public void testUpdate() {
        // create credential
        CertificateCredential credential = buildCertificateCredential();
        CertificateCredential credentialCreated = certificateCredentialRepository.create(credential).blockingGet();

        // update credential
        CertificateCredential updateCredential = buildCertificateCredential();
        updateCredential.setId(credentialCreated.getId());
        updateCredential.setCertificateThumbprint("updatedThumbprint");

        TestObserver<CertificateCredential> testObserver = certificateCredentialRepository.update(updateCredential).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCertificateThumbprint().equals(updateCredential.getCertificateThumbprint()));
    }

    @Test
    public void testDelete() {
        // create credential
        CertificateCredential credential = buildCertificateCredential();
        CertificateCredential credentialCreated = certificateCredentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<CertificateCredential> testObserver = certificateCredentialRepository.findById(credentialCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCertificateThumbprint().equals(credentialCreated.getCertificateThumbprint()));

        // delete credential
        TestObserver<?> testObserver1 = certificateCredentialRepository.delete(credentialCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch credential
        testObserver = certificateCredentialRepository.findById(credentialCreated.getId()).test();

        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }

    @Test
    public void findByUserId() {
        // create credential
        CertificateCredential credential = buildCertificateCredential();
        certificateCredentialRepository.create(credential).blockingGet();

        // fetch credentials
        TestSubscriber<CertificateCredential> testSubscriber = certificateCredentialRepository
                .findByUserId(credential.getReferenceType(), credential.getReferenceId(), credential.getUserId()).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void findByThumbprint() {
        // create credential
        CertificateCredential credential = buildCertificateCredential();
        certificateCredentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<CertificateCredential> testObserver = certificateCredentialRepository
                .findByThumbprint(credential.getReferenceType(), credential.getReferenceId(), credential.getCertificateThumbprint()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCertificateThumbprint().equals(credential.getCertificateThumbprint()));
    }

    @Test
    public void testDeleteByUserId() {
        // create credential
        CertificateCredential credential = buildCertificateCredential("domain-id", "user-id");
        CertificateCredential credentialCreated = certificateCredentialRepository.create(credential).blockingGet();

        // fetch credential
        TestSubscriber<CertificateCredential> testSubscriber = certificateCredentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(c -> c.getCertificateThumbprint().equals(credentialCreated.getCertificateThumbprint()));

        // delete credential
        TestObserver<?> testObserver1 = certificateCredentialRepository.deleteByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch credential
        TestSubscriber<CertificateCredential> testSubscriber2 = certificateCredentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testSubscriber2.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber2.assertComplete();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertNoValues();
    }

    @Test
    public void testDeleteByDomainAndUserAndId() {
        // Use unique identifiers to avoid test isolation issues
        TestIdentifiers ids = uniqueTestIdentifiers();

        // create credential for user1
        CertificateCredential credential1 = buildCertificateCredential(ids.domain1, ids.user1);
        CertificateCredential credential1Created = certificateCredentialRepository.create(credential1).blockingGet();

        // create credential for user2 in same domain
        CertificateCredential credential2 = buildCertificateCredential(ids.domain1, ids.user2);
        CertificateCredential credential2Created = certificateCredentialRepository.create(credential2).blockingGet();

        // create credential for user1 in different domain
        CertificateCredential credential3 = buildCertificateCredential(ids.domain2, ids.user1);
        CertificateCredential credential3Created = certificateCredentialRepository.create(credential3).blockingGet();

        // Verify all credentials exist
        TestSubscriber<CertificateCredential> testSubscriber = certificateCredentialRepository.findByUserId(ReferenceType.DOMAIN, ids.domain1, ids.user1).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);

        // Delete credential1 for user1 in domain1
        TestObserver<CertificateCredential> deleteObserver = certificateCredentialRepository
                .deleteByDomainAndUserAndId(ReferenceType.DOMAIN, ids.domain1, ids.user1, credential1Created.getId()).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertComplete();
        deleteObserver.assertNoErrors();
        deleteObserver.assertValue(c -> c.getId().equals(credential1Created.getId()));
        deleteObserver.assertValue(c -> c.getUserId().equals(ids.user1));

        // Verify credential1 is deleted
        TestObserver<CertificateCredential> findObserver = certificateCredentialRepository.findById(credential1Created.getId()).test();
        findObserver.awaitDone(10, TimeUnit.SECONDS);
        findObserver.assertComplete();
        findObserver.assertNoValues();
        findObserver.assertNoErrors();

        // Verify credential2 (user2 in domain1) still exists
        TestObserver<CertificateCredential> findObserver2 = certificateCredentialRepository.findById(credential2Created.getId()).test();
        findObserver2.awaitDone(10, TimeUnit.SECONDS);
        findObserver2.assertComplete();
        findObserver2.assertNoErrors();
        findObserver2.assertValue(c -> c.getId().equals(credential2Created.getId()));

        // Verify credential3 (user1 in domain2) still exists
        TestObserver<CertificateCredential> findObserver3 = certificateCredentialRepository.findById(credential3Created.getId()).test();
        findObserver3.awaitDone(10, TimeUnit.SECONDS);
        findObserver3.assertComplete();
        findObserver3.assertNoErrors();
        findObserver3.assertValue(c -> c.getId().equals(credential3Created.getId()));
    }

    @Test
    public void testDeleteByDomainAndUserAndId_credentialNotFound() {
        // Use unique identifiers to avoid test isolation issues
        TestIdentifiers ids = uniqueTestIdentifiers();
        
        // Try to delete non-existent credential
        TestObserver<CertificateCredential> deleteObserver = certificateCredentialRepository
                .deleteByDomainAndUserAndId(ReferenceType.DOMAIN, ids.domain1, ids.user1, "non-existent-id").test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertComplete();
        deleteObserver.assertNoValues();
        deleteObserver.assertNoErrors();
    }

    @Test
    public void testDeleteByDomainAndUserAndId_wrongUser() {
        // Use unique identifiers to avoid test isolation issues
        TestIdentifiers ids = uniqueTestIdentifiers();
        
        // create credential for user1
        CertificateCredential credential = buildCertificateCredential(ids.domain1, ids.user1);
        CertificateCredential credentialCreated = certificateCredentialRepository.create(credential).blockingGet();

        // Try to delete with wrong user ID
        TestObserver<CertificateCredential> deleteObserver = certificateCredentialRepository
                .deleteByDomainAndUserAndId(ReferenceType.DOMAIN, ids.domain1, ids.user2, credentialCreated.getId()).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertComplete();
        deleteObserver.assertNoValues();
        deleteObserver.assertNoErrors();

        // Verify credential still exists
        TestObserver<CertificateCredential> findObserver = certificateCredentialRepository.findById(credentialCreated.getId()).test();
        findObserver.awaitDone(10, TimeUnit.SECONDS);
        findObserver.assertComplete();
        findObserver.assertNoErrors();
        findObserver.assertValue(c -> c.getId().equals(credentialCreated.getId()));
    }

    @Test
    public void testDeleteByDomainAndUserAndId_wrongDomain() {
        // Use unique identifiers to avoid test isolation issues
        TestIdentifiers ids = uniqueTestIdentifiers();
        
        // create credential in domain1
        CertificateCredential credential = buildCertificateCredential(ids.domain1, ids.user1);
        CertificateCredential credentialCreated = certificateCredentialRepository.create(credential).blockingGet();

        // Try to delete with wrong domain ID
        TestObserver<CertificateCredential> deleteObserver = certificateCredentialRepository
                .deleteByDomainAndUserAndId(ReferenceType.DOMAIN, ids.domain2, ids.user1, credentialCreated.getId()).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertComplete();
        deleteObserver.assertNoValues();
        deleteObserver.assertNoErrors();

        // Verify credential still exists
        TestObserver<CertificateCredential> findObserver = certificateCredentialRepository.findById(credentialCreated.getId()).test();
        findObserver.awaitDone(10, TimeUnit.SECONDS);
        findObserver.assertComplete();
        findObserver.assertNoErrors();
        findObserver.assertValue(c -> c.getId().equals(credentialCreated.getId()));
    }

    @Test
    public void testDeleteByReference() {
        // Use unique identifiers to avoid test isolation issues
        TestIdentifiers ids = uniqueTestIdentifiers();
        
        // create credential for domain1
        CertificateCredential credential1 = buildCertificateCredential(ids.domain1, ids.user1);
        CertificateCredential credential1Created = certificateCredentialRepository.create(credential1).blockingGet();

        // create credential for domain2
        CertificateCredential credential2 = buildCertificateCredential(ids.domain2, ids.user1);
        CertificateCredential credential2Created = certificateCredentialRepository.create(credential2).blockingGet();

        // Verify credentials exist
        TestObserver<CertificateCredential> findObserver1 = certificateCredentialRepository.findById(credential1Created.getId()).test();
        findObserver1.awaitDone(10, TimeUnit.SECONDS);
        findObserver1.assertComplete();
        findObserver1.assertNoErrors();
        findObserver1.assertValue(c -> c.getId().equals(credential1Created.getId()));

        TestObserver<CertificateCredential> findObserver2 = certificateCredentialRepository.findById(credential2Created.getId()).test();
        findObserver2.awaitDone(10, TimeUnit.SECONDS);
        findObserver2.assertComplete();
        findObserver2.assertNoErrors();
        findObserver2.assertValue(c -> c.getId().equals(credential2Created.getId()));

        // Delete all credentials for domain1
        TestObserver<?> deleteObserver = certificateCredentialRepository.deleteByReference(ReferenceType.DOMAIN, ids.domain1).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertComplete();
        deleteObserver.assertNoErrors();

        // Verify credential1 (domain1) is deleted
        TestObserver<CertificateCredential> findObserver3 = certificateCredentialRepository.findById(credential1Created.getId()).test();
        findObserver3.awaitDone(10, TimeUnit.SECONDS);
        findObserver3.assertComplete();
        findObserver3.assertNoValues();
        findObserver3.assertNoErrors();

        // Verify credential2 (domain2) still exists
        TestObserver<CertificateCredential> findObserver4 = certificateCredentialRepository.findById(credential2Created.getId()).test();
        findObserver4.awaitDone(10, TimeUnit.SECONDS);
        findObserver4.assertComplete();
        findObserver4.assertNoErrors();
        findObserver4.assertValue(c -> c.getId().equals(credential2Created.getId()));
    }

    /**
     * Build a certificate credential with unique identifiers.
     * All identifiers use UUIDs to ensure test isolation.
     *
     * @param referenceId Optional reference ID. If null, a unique one will be generated.
     * @param userId Optional user ID. If null, a unique one will be generated.
     * @return A new CertificateCredential instance
     */
    private CertificateCredential buildCertificateCredential(String referenceId, String userId) {
        String randomStr = UUID.randomUUID().toString();
        Date expirationDate = new Date(System.currentTimeMillis() + 86400000); // Tomorrow

        Map<String, String> metadata = new HashMap<>();
        metadata.put("issuerDN", "CN=CA, O=Example Corp");
        metadata.put("keyUsage", "digitalSignature, keyEncipherment");

        CertificateCredential credential = new CertificateCredential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId(referenceId != null ? referenceId : "domain-" + randomStr);
        credential.setUserId(userId != null ? userId : "uid-" + randomStr);
        credential.setUsername("uname-" + randomStr);
        credential.setCertificatePem("-----BEGIN CERTIFICATE-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA" + randomStr + "\n-----END CERTIFICATE-----");
        credential.setCertificateThumbprint("thumbprint-" + randomStr);
        credential.setCertificateSubjectDN("CN=Test User " + randomStr);
        credential.setCertificateSerialNumber("serial-" + randomStr);
        credential.setCertificateExpiresAt(expirationDate);
        credential.setMetadata(metadata);
        credential.setAccessedAt(new Date());
        credential.setUserAgent("uagent-" + randomStr);
        credential.setIpAddress("ip-" + randomStr);
        return credential;
    }

    /**
     * Build a certificate credential with auto-generated unique identifiers.
     * Convenience method for tests that don't need specific referenceId or userId.
     */
    private CertificateCredential buildCertificateCredential() {
        return buildCertificateCredential(null, null);
    }

    /**
     * Generate unique test identifiers for a test case.
     * This ensures test isolation by using UUIDs for all identifiers.
     *
     * @return A TestIdentifiers object with unique domain and user IDs
     */
    private static TestIdentifiers uniqueTestIdentifiers() {
        String uniqueId = UUID.randomUUID().toString();
        return new TestIdentifiers(
                "domain-1-" + uniqueId,
                "domain-2-" + uniqueId,
                "user-1-" + uniqueId,
                "user-2-" + uniqueId
        );
    }

    /**
     * Helper class to hold unique test identifiers for a test case.
     */
    private static class TestIdentifiers {
        final String domain1;
        final String domain2;
        final String user1;
        final String user2;

        TestIdentifiers(String domain1, String domain2, String user1, String user2) {
            this.domain1 = domain1;
            this.domain2 = domain2;
            this.user1 = user1;
            this.user2 = user2;
        }
    }
}

