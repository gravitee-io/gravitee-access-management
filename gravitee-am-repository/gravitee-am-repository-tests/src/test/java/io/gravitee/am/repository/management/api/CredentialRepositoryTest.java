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

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CredentialRepositoryTest extends AbstractManagementTest {

    @Autowired
    private CredentialRepository credentialRepository;

    @Test
    public void findByUserId() throws TechnicalException {
        // create credential
        Credential credential = buildCredential();
        credentialRepository.create(credential).blockingGet();

        // fetch credentials
        TestObserver<List<Credential>> testObserver = credentialRepository
                .findByUserId(credential.getReferenceType(), credential.getReferenceId(), credential.getUserId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(credentials -> credentials.size() == 1);
    }

    @Test
    public void findByUsername() throws TechnicalException {
        // create credential
        Credential credential = buildCredential();
        credentialRepository.create(credential).blockingGet();

        // fetch credentials
        TestObserver<List<Credential>> testObserver = credentialRepository
                .findByUsername(credential.getReferenceType(), credential.getReferenceId(), credential.getUsername()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(credentials -> credentials.size() == 1);
    }

    @Test
    public void findByCredentialId() throws TechnicalException {
        // create credential
        Credential credential = buildCredential();
        credentialRepository.create(credential).blockingGet();

        // fetch credentials
        TestObserver<List<Credential>> testObserver = credentialRepository
                .findByCredentialId(credential.getReferenceType(), credential.getReferenceId(), credential.getCredentialId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(credentials -> credentials.size() == 1);
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
    public void testFindById() throws TechnicalException {
        // create credential
        Credential credential = buildCredential();
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<Credential> testObserver = credentialRepository.findById(credentialCreated.getId()).test();
        testObserver.awaitTerminalEvent();

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
    public void testNotFoundById() throws TechnicalException {
        credentialRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Credential credential = buildCredential();

        TestObserver<Credential> testObserver = credentialRepository.create(credential).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCredentialId().equals(credential.getCredentialId()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create credential
        Credential credential = buildCredential();
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // update credential
        Credential updateCredential = buildCredential();
        updateCredential.setId(credentialCreated.getId());
        updateCredential.setCredentialId("updateCredentialId");

        TestObserver<Credential> testObserver = credentialRepository.update(updateCredential).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCredentialId().equals(updateCredential.getCredentialId()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create credential
        Credential credential = buildCredential();
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<Credential> testObserver = credentialRepository.findById(credentialCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCredentialId().equals(credentialCreated.getCredentialId()));

        // delete credential
        TestObserver testObserver1 = credentialRepository.delete(credentialCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch credential
        credentialRepository.findById(credentialCreated.getId()).test().assertEmpty();
    }

    @Test
    public void testDeleteByUser() throws TechnicalException {
        // create credential
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domain-id");
        credential.setUserId("user-id");
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<List<Credential>> testObserver = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(l -> l.size() == 1 && l.get(0).getCredentialId().equals(credentialCreated.getCredentialId()));

        // delete credential
        TestObserver testObserver1 = credentialRepository.deleteByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testObserver1.awaitTerminalEvent();

        // fetch credential
        TestObserver<List<Credential>> testObserver2 = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testObserver2.awaitTerminalEvent();
        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
        testObserver2.assertValue(l -> l.isEmpty());
    }

    @Test
    public void testDeleteByUser_invalid_user() throws TechnicalException {
        // create credential
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domain-id");
        credential.setUserId("user-id");
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<List<Credential>> testObserver = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(l -> l.size() == 1 && l.get(0).getCredentialId().equals(credentialCreated.getCredentialId()));

        // delete credential
        TestObserver testObserver1 = credentialRepository.deleteByUserId(ReferenceType.DOMAIN, "domain-id", "wrong-user-id").test();
        testObserver1.awaitTerminalEvent();

        // fetch credential
        TestObserver<List<Credential>> testObserver2 = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testObserver2.awaitTerminalEvent();
        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
        testObserver.assertValue(l -> l.size() == 1 && l.get(0).getCredentialId().equals(credentialCreated.getCredentialId()));
    }

    @Test
    public void testDeleteByAaguid() throws TechnicalException {
        // create credential
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domain-id");
        credential.setUserId("user-id");
        credential.setAaguid("aaguid");
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<List<Credential>> testObserver = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(l -> l.size() == 1 && l.get(0).getCredentialId().equals(credentialCreated.getCredentialId()));

        // delete credential
        TestObserver testObserver1 = credentialRepository.deleteByAaguid(ReferenceType.DOMAIN, "domain-id", "aaguid").test();
        testObserver1.awaitTerminalEvent();

        // fetch credential
        TestObserver<List<Credential>> testObserver2 = credentialRepository.findByUserId(ReferenceType.DOMAIN, "domain-id", "user-id").test();
        testObserver2.awaitTerminalEvent();
        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
        testObserver2.assertValue(l -> l.isEmpty());
    }
}
