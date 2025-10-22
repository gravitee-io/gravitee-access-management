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
package io.gravitee.am.gateway.handler.common.protectedresource;

import io.gravitee.am.gateway.handler.common.protectedresource.impl.ProtectedResourceSyncServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ProtectedResourceSyncServiceTest {

    private static Set<ProtectedResource> protectedResourceSet;

    @InjectMocks
    private ProtectedResourceSyncService protectedResourceSyncService = new ProtectedResourceSyncServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private ProtectedResourceManager protectedResourceManager;

    @BeforeClass
    public static void initializeProtectedResources() {
        protectedResourceSet = new HashSet<>();

        ProtectedResource domainAResourceA = new ProtectedResource();
        domainAResourceA.setId("aa");
        domainAResourceA.setDomainId("domainA");
        domainAResourceA.setClientId("domainAClientA");
        domainAResourceA.setName("Protected Resource A");

        ProtectedResource domainAResourceB = new ProtectedResource();
        domainAResourceB.setId("ab");
        domainAResourceB.setDomainId("domainA");
        domainAResourceB.setClientId("domainAClientB");
        domainAResourceB.setName("Protected Resource B");

        ProtectedResource domainBResourceA = new ProtectedResource();
        domainBResourceA.setId("ba");
        domainBResourceA.setDomainId("domainB");
        domainBResourceA.setClientId("domainBClientA");
        domainBResourceA.setName("Protected Resource B-A");

        ProtectedResource domainBResourceB = new ProtectedResource();
        domainBResourceB.setId("bb");
        domainBResourceB.setDomainId("domainB");
        domainBResourceB.setClientId("domainBClientB");
        domainBResourceB.setName("Protected Resource B-B");

        protectedResourceSet.add(domainAResourceA);
        protectedResourceSet.add(domainAResourceB);
        protectedResourceSet.add(domainBResourceA);
        protectedResourceSet.add(domainBResourceB);
    }

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn("domainA");
        when(protectedResourceManager.entities()).thenReturn(protectedResourceSet);
    }

    @Test
    public void findByClientId_resourceFound() {
        TestObserver<Client> test = protectedResourceSyncService.findByClientId("domainAClientA").test();
        test.assertComplete().assertNoErrors();
        test.assertValue(client -> client.getClientId().equals("domainAClientA"));
        test.assertValue(client -> client.getClientName().equals("Protected Resource A"));
        test.assertValue(client -> client.getDomain().equals("domainA"));
    }

    @Test
    public void findByClientId_resourceNotFound() {
        TestObserver<Client> test = protectedResourceSyncService.findByClientId("nonExistentClient").test();
        test.assertComplete().assertNoErrors().assertNoValues();
    }

    @Test
    public void findByClientId_wrongDomain() {
        // domainBClientA exists but in domainB, while current domain is domainA
        TestObserver<Client> test = protectedResourceSyncService.findByClientId("domainBClientA").test();
        test.assertComplete().assertNoErrors().assertNoValues();
    }

    @Test
    public void findByDomainAndClientId_resourceFound() {
        TestObserver<Client> test = protectedResourceSyncService.findByDomainAndClientId("domainA", "domainAClientA").test();
        test.assertComplete().assertNoErrors();
        test.assertValue(client -> client.getClientId().equals("domainAClientA"));
        test.assertValue(client -> client.getClientName().equals("Protected Resource A"));
        test.assertValue(client -> client.getDomain().equals("domainA"));
    }

    @Test
    public void findByDomainAndClientId_resourceNotFound() {
        TestObserver<Client> test = protectedResourceSyncService.findByDomainAndClientId("domainA", "nonExistentClient").test();
        test.assertComplete().assertNoErrors().assertNoValues();
    }

    @Test
    public void findByDomainAndClientId_crossDomain() {
        TestObserver<Client> test = protectedResourceSyncService.findByDomainAndClientId("domainB", "domainBClientA").test();
        test.assertComplete().assertNoErrors();
        test.assertValue(client -> client.getClientId().equals("domainBClientA"));
        test.assertValue(client -> client.getClientName().equals("Protected Resource B-A"));
        test.assertValue(client -> client.getDomain().equals("domainB"));
    }

    @Test
    public void findByDomainAndClientId_wrongDomain() {
        // domainAClientA exists but in domainA, requesting with domainB
        TestObserver<Client> test = protectedResourceSyncService.findByDomainAndClientId("domainB", "domainAClientA").test();
        test.assertComplete().assertNoErrors().assertNoValues();
    }

    @Test
    public void findByClientId_verifyClientConversion() {
        TestObserver<Client> test = protectedResourceSyncService.findByClientId("domainAClientA").test();
        test.assertComplete().assertNoErrors();
        test.assertValue(client -> client.getId().equals("aa"));
        test.assertValue(client -> client.getClientId().equals("domainAClientA"));
        test.assertValue(client -> client.getClientName().equals("Protected Resource A"));
        test.assertValue(client -> client.getDomain().equals("domainA"));
        test.assertValue(Client::isEnabled);
    }

    @Test
    public void findByClientId_multipleResourcesSameDomain() {
        TestObserver<Client> test1 = protectedResourceSyncService.findByClientId("domainAClientA").test();
        test1.assertComplete().assertNoErrors();
        test1.assertValue(client -> client.getClientId().equals("domainAClientA"));

        TestObserver<Client> test2 = protectedResourceSyncService.findByClientId("domainAClientB").test();
        test2.assertComplete().assertNoErrors();
        test2.assertValue(client -> client.getClientId().equals("domainAClientB"));
    }
}
