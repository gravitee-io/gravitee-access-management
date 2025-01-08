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

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CredentialRepositoryTest extends AbstractDataPlaneTest {

    @Autowired
    private CredentialRepository credentialRepository;

    @Test
    public void findByUserId() {
        // create credential
        Credential credential = buildCredential();
        credentialRepository.create(credential).blockingGet();

        // fetch credentials
        TestSubscriber<Credential> testObserver = credentialRepository
                .findByUserId(credential.getReferenceType(), credential.getReferenceId(), credential.getUserId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void findByUsername() {
        // create credential
        Credential credential = buildCredential();
        credentialRepository.create(credential).blockingGet();

        // fetch credentials
        TestSubscriber<Credential> testSubscriber = credentialRepository
                .findByUsername(credential.getReferenceType(), credential.getReferenceId(), credential.getUsername()).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void findByUsernameWithLimit() {
        // create credential
        Credential credential1 = buildCredential();
        Credential credential2 = buildCredential();

        credentialRepository.create(credential1).blockingGet();
        credentialRepository.create(credential2).blockingGet();

        // fetch credentials
        TestSubscriber<Credential> testSubscriber = credentialRepository
                .findByUsername(credential1.getReferenceType(), credential1.getReferenceId(), credential1.getUsername(), 1).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void findByCredentialId() {
        // create credential
        Credential credential = buildCredential();
        credentialRepository.create(credential).blockingGet();

        // fetch credentials
        TestSubscriber<Credential> testSubscriber = credentialRepository
                .findByCredentialId(credential.getReferenceType(), credential.getReferenceId(), credential.getCredentialId()).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    private Credential buildCredential() {
        Credential credential = new Credential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domainId");
        String randomStr = UUID.randomUUID().toString();
        credential.setCredentialId("cred"+ randomStr);
        credential.setUserId("uid"+ randomStr);
        credential.setUsername("uname"+ randomStr);
        credential.setPublicKey("pub"+ randomStr);
        credential.setAccessedAt(new Date());
        credential.setUserAgent("uagent"+ randomStr);
        credential.setIpAddress("ip"+ randomStr);
        return credential;
    }

    @Test
    public void testFindById() {
        // create credential
        Credential credential = buildCredential();
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<Credential> testObserver = credentialRepository.findById(credentialCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getId().equals(credentialCreated.getId()));
        testObserver.assertValue(c -> c.getCredentialId().equals(credential.getCredentialId()));
        testObserver.assertValue(c -> c.getPublicKey().equals(credential.getPublicKey()));
        testObserver.assertValue(c -> c.getReferenceId().equals(credential.getReferenceId()));
        testObserver.assertValue(c -> c.getReferenceType().equals(credential.getReferenceType()));
        testObserver.assertValue(c -> c.getUserId().equals(credential.getUserId()));
        testObserver.assertValue(c -> c.getUsername().equals(credential.getUsername()));
        testObserver.assertValue(c -> c.getIpAddress().equals(credential.getIpAddress()));
        testObserver.assertValue(c -> c.getUserAgent().equals(credential.getUserAgent()));
    }

    @Test
    public void testNotFoundById() {
        var observer = credentialRepository.findById("test").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        Credential credential = buildCredential();

        TestObserver<Credential> testObserver = credentialRepository.create(credential).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCredentialId().equals(credential.getCredentialId()));
    }

    @Test
    public void testUpdate() {
        // create credential
        Credential credential = buildCredential();
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // update credential
        Credential updateCredential = buildCredential();
        updateCredential.setId(credentialCreated.getId());
        updateCredential.setCredentialId("updateCredentialId");

        TestObserver<Credential> testObserver = credentialRepository.update(updateCredential).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCredentialId().equals(updateCredential.getCredentialId()));
    }

    @Test
    public void testDelete() {
        // create credential
        Credential credential = buildCredential();
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<Credential> testObserver = credentialRepository.findById(credentialCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCredentialId().equals(credentialCreated.getCredentialId()));

        // delete credential
        TestObserver testObserver1 = credentialRepository.delete(credentialCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch credential
        testObserver = credentialRepository.findById(credentialCreated.getId()).test();

        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }

    @Test
    public void testDeleteByUser() {
        // create credential
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domain-id");
        credential.setUserId("user-id");
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestSubscriber<Credential> testSubscriber = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(c -> c.getCredentialId().equals(credentialCreated.getCredentialId()));

        // delete credential
        TestObserver testObserver1 = credentialRepository.deleteByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch credential
        TestSubscriber<Credential> testSubscriber2 = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testSubscriber2.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber2.assertComplete();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertNoValues();
    }

    @Test
    public void testDeleteByUser_invalid_user() {
        // create credential
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domain-id");
        credential.setUserId("user-id");
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestSubscriber<Credential> testSubscriber = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(c -> c.getCredentialId().equals(credentialCreated.getCredentialId()));

        // delete credential
        TestObserver testObserver1 = credentialRepository.deleteByUserId(ReferenceType.DOMAIN, "domain-id", "wrong-user-id").test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch credential
        TestSubscriber<Credential> testSubscriber2 = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testSubscriber2.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber2.assertComplete();
        testSubscriber2.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(c -> c.getCredentialId().equals(credentialCreated.getCredentialId()));
    }

    @Test
    public void testDeleteByReference() {
        // create credentials
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domain-id");
        credential.setUserId("user-id");
        credentialRepository.create(credential).blockingGet();
        Credential credential2 = new Credential();
        credential2.setCredentialId("credentialId");
        credential2.setReferenceType(ReferenceType.DOMAIN);
        credential2.setReferenceId("domain-id");
        credential2.setUserId("user-id");
        credentialRepository.create(credential2).blockingGet();

        // fetch credential
        TestSubscriber<Credential> testSubscriber = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(2);

        // delete credentials
        TestObserver testObserver1 = credentialRepository.deleteByReference(ReferenceType.DOMAIN, "domain-id").test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch credential
        TestSubscriber<Credential> testSubscriber2 = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testSubscriber2.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber2.assertComplete();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertNoValues();
    }
}
