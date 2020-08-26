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

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.CredentialRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoCredentialRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private CredentialRepository credentialRepository;

    @Override
    public String collectionName() {
        return "webauthn_credentials";
    }

    @Test
    public void findByUserId() throws TechnicalException {
        // create credential
        Credential credential = new Credential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domainId");
        credential.setCredentialId("credentialId");
        credential.setUserId("userId");
        credential.setUsername("username");
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
        Credential credential = new Credential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domainId");
        credential.setCredentialId("credentialId");
        credential.setUserId("userId");
        credential.setUsername("username");
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
        Credential credential = new Credential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domainId");
        credential.setCredentialId("credentialId");
        credential.setUserId("userId");
        credential.setUsername("username");
        credentialRepository.create(credential).blockingGet();

        // fetch credentials
        TestObserver<List<Credential>> testObserver = credentialRepository
                .findByCredentialId(credential.getReferenceType(), credential.getReferenceId(), credential.getCredentialId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(credentials -> credentials.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create credential
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // fetch credential
        TestObserver<Credential> testObserver = credentialRepository.findById(credentialCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCredentialId().equals("credentialId"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        credentialRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");

        TestObserver<Credential> testObserver = credentialRepository.create(credential).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getCredentialId().equals("credentialId"));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create credential
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");
        Credential credentialCreated = credentialRepository.create(credential).blockingGet();

        // update credential
        Credential updateCredential = new Credential();
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
        Credential credential = new Credential();
        credential.setCredentialId("credentialId");
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

}
