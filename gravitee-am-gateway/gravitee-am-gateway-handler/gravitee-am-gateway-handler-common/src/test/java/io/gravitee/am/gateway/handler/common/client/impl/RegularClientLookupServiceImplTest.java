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
package io.gravitee.am.gateway.handler.common.client.impl;

import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RegularClientLookupServiceImplTest {

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private ProtectedResourceSyncService protectedResourceSyncService;

    private ClientLookupService clientLookupService;

    @Before
    public void setUp() {
        clientLookupService = new DefaultClientLookupServiceImpl(clientSyncService);
    }

    @Test
    public void shouldFindClientById() {
        Client client = new Client();
        when(clientSyncService.findById("client-uuid")).thenReturn(Maybe.just(client));

        TestObserver<Client> observer = clientLookupService.findById("client-uuid").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(client);
        verifyNoInteractions(protectedResourceSyncService);
    }

    @Test
    public void shouldReturnEmptyWhenClientNotFoundById() {
        when(clientSyncService.findById("client-uuid")).thenReturn(Maybe.empty());

        TestObserver<Client> observer = clientLookupService.findById("client-uuid").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();
        verifyNoInteractions(protectedResourceSyncService);
    }

    @Test
    public void shouldReturnClientFromClientSyncService() {
        Client client = new Client();
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        TestObserver<Client> observer = clientLookupService.findByClientId("client-id").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(client);
        verifyNoInteractions(protectedResourceSyncService);
    }

    @Test
    public void shouldReturnEmptyWhenClientIsMissing() {
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.empty());

        TestObserver<Client> observer = clientLookupService.findByClientId("client-id").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();
        verifyNoInteractions(protectedResourceSyncService);
    }

    @Test
    public void shouldLookupClientByDomainWithoutFallback() {
        Client client = new Client();
        when(clientSyncService.findByDomainAndClientId("domain", "client-id")).thenReturn(Maybe.just(client));

        TestObserver<Client> observer = clientLookupService.findByDomainAndClientId("domain", "client-id").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(client);
        verifyNoInteractions(protectedResourceSyncService);
    }
}
