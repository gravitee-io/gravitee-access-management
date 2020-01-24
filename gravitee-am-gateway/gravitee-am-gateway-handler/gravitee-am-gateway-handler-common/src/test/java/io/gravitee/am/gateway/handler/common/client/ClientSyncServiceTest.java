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
package io.gravitee.am.gateway.handler.common.client;

import io.gravitee.am.gateway.core.manager.EntityManager;
import io.gravitee.am.gateway.handler.common.client.impl.ClientSyncServiceImpl;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientSyncServiceTest {

    private static Set<Client> clientSet;

    @InjectMocks
    private ClientSyncService clientSyncService = new ClientSyncServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private EntityManager<Client> clientManager;

    @BeforeClass
    public static void initializeClients() {
        clientSet = new HashSet<>();

        Client domainAClientA = new Client();
        domainAClientA.setId("aa");
        domainAClientA.setDomain("domainA");
        domainAClientA.setClientId("domainAClientA");

        Client domainAClientB = new Client();
        domainAClientB.setId("ab");
        domainAClientB.setDomain("domainA");
        domainAClientB.setClientId("domainAClientB");
        domainAClientB.setTemplate(true);

        Client domainAClientC = new Client();
        domainAClientC.setId("ac");
        domainAClientC.setDomain("domainA");
        domainAClientC.setClientId("domainAClientC");
        domainAClientC.setTemplate(true);

        Client domainBClientA = new Client();
        domainBClientA.setId("ba");
        domainBClientA.setDomain("domainB");
        domainBClientA.setClientId("domainBClientA");

        Client domainBClientB = new Client();
        domainBClientB.setId("bb");
        domainBClientB.setDomain("domainB");
        domainBClientB.setClientId("domainBClientB");
        domainBClientB.setTemplate(true);

        Client domainBClientC = new Client();
        domainBClientC.setId("bc");
        domainBClientC.setDomain("domainB");
        domainBClientC.setClientId("domainBClientC");
        domainBClientC.setTemplate(true);

        clientSet.add(domainAClientA);
        clientSet.add(domainAClientB);
        clientSet.add(domainAClientC);
        clientSet.add(domainBClientA);
        clientSet.add(domainBClientB);
        clientSet.add(domainBClientC);
    }

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn("domainA");
        when(clientManager.entities()).thenReturn(clientSet);
    }

    @Test
    public void findById_clientFound() {
        when(clientManager.get("aa")).thenReturn(clientSet.stream().filter(c -> c.getId().equals("aa")).findFirst().get());
        TestObserver<Client> test = clientSyncService.findById("aa").test();
        test.assertComplete().assertNoErrors();
        test.assertValue(client -> client.getClientId().equals("domainAClientA"));
    }

    @Test
    public void findById_clientNotFound() {
        TestObserver<Client> test = clientSyncService.findById("aa").test();
        test.assertComplete().assertNoErrors().assertNoValues();
    }

    @Test
    public void findByClientId_clientFound() {
        TestObserver<Client> test = clientSyncService.findByClientId("domainAClientA").test();
        test.assertComplete().assertNoErrors();
        test.assertValue(client -> client.getClientId().equals("domainAClientA"));
    }

    @Test
    public void findByClientId_templateShouldNotBeThere() {
        TestObserver<Client> test = clientSyncService.findByClientId("domainAClientB").test();
        test.assertComplete().assertNoErrors().assertNoValues();
    }

    @Test
    public void findTemplates() {
        TestObserver<List<Client>> test = clientSyncService.findTemplates().test();
        test.assertComplete().assertNoErrors();
        test.assertValue(clients -> clients!=null && clients.size()==2);
    }

    @Test
    public void addDynamicClientRegistred_ok() {
        //Template should not added
        Client existingClient = new Client();
        existingClient.setId("aa");
        existingClient.setDomain("domainA");
        existingClient.setClientId("domainAClientA");

        clientSyncService.addDynamicClientRegistred(existingClient);
        TestObserver<Client> test = clientSyncService.findByClientId("domainAClientA").test();
        test.assertComplete().assertNoErrors();
        test.assertValue(client -> client.getClientId().equals("domainAClientA"));
    }

    @Test
    public void addDynamicClientRegistred_ShouldRejectTemplate() {
        //Template should not added
        Client template = new Client();
        template.setDomain("domainA");
        template.setClientId("template");
        template.setTemplate(true);

        doNothing().when(clientManager).deploy(template);
        clientSyncService.addDynamicClientRegistred(template);
        verify(clientManager, times(1)).deploy(template);
    }

    @Test
    public void removeDynamicClientRegistred() {
        Client client = new Client();
        client.setDomain("domainA");
        client.setId("aa");

        doNothing().when(clientManager).undeploy(client.getId());
        clientSyncService.removeDynamicClientRegistred(client);
        verify(clientManager, times(1)).undeploy(client.getId());
    }
}
