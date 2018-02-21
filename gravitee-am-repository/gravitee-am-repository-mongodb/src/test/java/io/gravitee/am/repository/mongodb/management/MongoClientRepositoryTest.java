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

import io.gravitee.am.model.Client;
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoClientRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create client
        Client client = new Client();
        client.setClientId("testClientId");
        client.setDomain("testDomain");
        clientRepository.create(client).blockingGet();

        // fetch clients
        TestObserver<Set<Client>> testObserver = clientRepository.findByDomain("testDomain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(clients -> clients.size() == 1);
    }

    @Test
    public void testFindByDomainPagination() throws TechnicalException {
        // create client 1
        Client client = new Client();
        client.setClientId("testClientId");
        client.setDomain("testDomainPagination");
        clientRepository.create(client).blockingGet();

        // create client 2
        Client client2 = new Client();
        client2.setClientId("testClientId2");
        client2.setDomain("testDomainPagination");
        clientRepository.create(client).blockingGet();

        TestObserver<Page<Client>> testObserver = clientRepository.findByDomain("testDomainPagination", 1, 1).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(pageClients -> pageClients.getTotalCount() == 2 && pageClients.getData().size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create client
        Client client = new Client();
        client.setClientId("testClientId");
        Client clientCreated = clientRepository.create(client).blockingGet();

        // fetch client
        TestObserver<Client> testObserver = clientRepository.findById(clientCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getClientId().equals("testClientId"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        clientRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Client client = new Client();
        client.setClientId("testClientId");
        client.setIdTokenCustomClaims(Collections.singletonMap("name", "johndoe"));

        TestObserver<Client> testObserver = clientRepository.create(client).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getClientId().equals(client.getClientId()) && c.getIdTokenCustomClaims().containsKey("name"));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create client
        Client client = new Client();
        client.setClientId("testClientId");
        Client clientCreated = clientRepository.create(client).blockingGet();

        // update client
        Client updatedClient = new Client();
        updatedClient.setId(clientCreated.getId());
        updatedClient.setClientId("testUpdatedClientId");

        TestObserver<Client> testObserver = clientRepository.update(updatedClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getClientId().equals(updatedClient.getClientId()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create client
        Client client = new Client();
        client.setClientId("testClientId");
        Client clientCreated = clientRepository.create(client).blockingGet();

        // fetch client
        TestObserver<Client> testObserver = clientRepository.findById(clientCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(c -> c.getClientId().equals(client.getClientId()));

        // delete client
        TestObserver<Irrelevant> testObserver1 = clientRepository.delete(clientCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch client
        clientRepository.findById(clientCreated.getId()).test().assertEmpty();
    }

}
