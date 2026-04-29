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
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataService;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CimdAwareClientLookupServiceImplTest {

    @Mock
    private CimdMetadataService cimdMetadataService;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private ProtectedResourceSyncService protectedResourceSyncService;

    private ClientLookupService complexClientLookupService;
    private ClientLookupService regularClientLookupService;

    @Before
    public void setUp() {
        complexClientLookupService = new CimdAwareClientLookupServiceImpl(clientSyncService, protectedResourceSyncService, cimdMetadataService, "software-template-id");
        regularClientLookupService = new CimdAwareClientLookupServiceImpl(clientSyncService, cimdMetadataService, "software-template-id");
        when(protectedResourceSyncService.findByClientId(anyString())).thenReturn(Maybe.empty());
        when(protectedResourceSyncService.findByDomainAndClientId(anyString(), anyString())).thenReturn(Maybe.empty());
    }

    @Test
    public void shouldFindTemplateClientByIdWithoutInvokingCimd() {
        // When a CIMD user's session stores user.getClient() = templateId (the internal UUID
        // inherited by the synthesized client), looking up by that UUID resolves the template
        // directly without triggering remote CIMD metadata resolution.
        Client template = new Client();
        template.setId("software-template-id");
        when(clientSyncService.findById("software-template-id")).thenReturn(Maybe.just(template));

        TestObserver<Client> observer = complexClientLookupService.findById("software-template-id").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(template);
        verifyNoInteractions(cimdMetadataService);
    }

    @Test
    public void shouldReturnEmptyWhenTemplateNotFoundById() {
        when(clientSyncService.findById("software-template-id")).thenReturn(Maybe.empty());

        TestObserver<Client> observer = complexClientLookupService.findById("software-template-id").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();
        verifyNoInteractions(cimdMetadataService);
    }

    @Test
    public void shouldReturnRegisteredClientWithoutCallingCimd() {
        Client registeredClient = new Client();
        when(clientSyncService.findByClientId("registered-client")).thenReturn(Maybe.just(registeredClient));

        TestObserver<Client> observer = complexClientLookupService.findByClientId("registered-client").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(registeredClient);
        verify(clientSyncService, never()).findById(anyString());
        verifyNoInteractions(cimdMetadataService);
    }

    @Test
    public void shouldFallbackToCimdWhenClientIsNotRegistered() {
        String clientId = "https://localhost/cimd-client";
        Client template = new Client();
        Client synthesized = new Client();
        synthesized.setClientId(clientId);

        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.empty());
        when(clientSyncService.findById("software-template-id")).thenReturn(Maybe.just(template));
        when(cimdMetadataService.resolveClient(clientId, template)).thenReturn(Maybe.just(synthesized));

        TestObserver<Client> observer = complexClientLookupService.findByClientId(clientId).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(synthesized);
        verify(clientSyncService).findById("software-template-id");
        verify(cimdMetadataService).resolveClient(clientId, template);
    }

    @Test
    public void shouldReturnErrorWhenTemplateCannotBeFound() {
        String clientId = "https://localhost/cimd-client";

        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.empty());
        when(clientSyncService.findById("software-template-id")).thenReturn(Maybe.empty());

        TestObserver<Client> observer = complexClientLookupService.findByClientId(clientId).test();

        observer.assertError(throwable -> throwable instanceof InvalidClientMetadataException && throwable.getMessage().contains("software-template-id"));
        verifyNoInteractions(cimdMetadataService);
    }

    @Test
    public void shouldUseSameFallbackForFindByDomainAndClientId() {
        String clientId = "https://localhost/cimd-client";
        Client template = new Client();
        Client synthesized = new Client();

        when(clientSyncService.findByDomainAndClientId("domain-id", clientId)).thenReturn(Maybe.empty());
        when(clientSyncService.findById("software-template-id")).thenReturn(Maybe.just(template));
        when(cimdMetadataService.resolveClient(clientId, template)).thenReturn(Maybe.just(synthesized));

        TestObserver<Client> observer = complexClientLookupService.findByDomainAndClientId("domain-id", clientId).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(synthesized);
        verify(clientSyncService).findById("software-template-id");
    }

    @Test
    public void shouldUseTemplateIdResolvedAtConstructionTime() {
        String clientId = "https://localhost/cimd-client";
        Client template = new Client();
        Client synthesized = new Client();

        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.empty());
        when(clientSyncService.findById("software-template-id")).thenReturn(Maybe.just(template));
        when(cimdMetadataService.resolveClient(clientId, template)).thenReturn(Maybe.just(synthesized));

        TestObserver<Client> observer = complexClientLookupService.findByClientId(clientId).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(synthesized);
        verify(clientSyncService).findById("software-template-id");
        verify(clientSyncService, never()).findById("updated-software-id");
    }

    @Test
    public void shouldFallbackToProtectedResourceBeforeCimdWhenUsingComplexLookup() {
        String clientId = "https://localhost/protected-resource";
        Client protectedResourceClient = new Client();

        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.empty());
        when(protectedResourceSyncService.findByClientId(clientId)).thenReturn(Maybe.just(protectedResourceClient));

        TestObserver<Client> observer = complexClientLookupService.findByClientId(clientId).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(protectedResourceClient);
        verify(protectedResourceSyncService).findByClientId(clientId);
        verify(clientSyncService, never()).findById("software-template-id");
        verifyNoInteractions(cimdMetadataService);
    }

    @Test
    public void shouldNotUseProtectedResourceFallbackWhenUsingRegularLookup() {
        String clientId = "https://localhost/cimd-client";
        Client template = new Client();
        Client synthesized = new Client();

        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.empty());
        when(clientSyncService.findById("software-template-id")).thenReturn(Maybe.just(template));
        when(cimdMetadataService.resolveClient(clientId, template)).thenReturn(Maybe.just(synthesized));

        TestObserver<Client> observer = regularClientLookupService.findByClientId(clientId).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(synthesized);
        verifyNoInteractions(protectedResourceSyncService);
        verify(clientSyncService).findById("software-template-id");
    }
}
