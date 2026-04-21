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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ComplexClientLookupServiceImplTest {

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private ProtectedResourceSyncService protectedResourceSyncService;

    private ClientLookupService clientLookupService;

    @Before
    public void setUp() {
        clientLookupService = new DefaultClientLookupServiceImpl(clientSyncService, protectedResourceSyncService);
    }

    @Test
    public void shouldReturnClientFromClientSyncServiceWithoutFallback() {
        Client client = new Client();
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        TestObserver<Client> observer = clientLookupService.findByClientId("client-id").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(client);
        verify(protectedResourceSyncService, never()).findByClientId("client-id");
    }

    @Test
    public void shouldFallbackToProtectedResourceWhenClientIsMissing() {
        Client protectedResourceClient = new Client();
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.empty());
        when(protectedResourceSyncService.findByClientId("client-id")).thenReturn(Maybe.just(protectedResourceClient));

        TestObserver<Client> observer = clientLookupService.findByClientId("client-id").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(protectedResourceClient);
        verify(protectedResourceSyncService).findByClientId("client-id");
    }

    @Test
    public void shouldFallbackToProtectedResourceForDomainLookup() {
        Client protectedResourceClient = new Client();
        when(clientSyncService.findByDomainAndClientId("domain", "client-id")).thenReturn(Maybe.empty());
        when(protectedResourceSyncService.findByDomainAndClientId("domain", "client-id")).thenReturn(Maybe.just(protectedResourceClient));

        TestObserver<Client> observer = clientLookupService.findByDomainAndClientId("domain", "client-id").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(protectedResourceClient);
        verify(protectedResourceSyncService).findByDomainAndClientId("domain", "client-id");
    }
}
