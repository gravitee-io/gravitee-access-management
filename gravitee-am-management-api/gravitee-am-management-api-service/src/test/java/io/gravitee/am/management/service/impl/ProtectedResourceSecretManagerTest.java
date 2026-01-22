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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.ProtectedResourceSecretEvent;
import io.gravitee.am.management.service.ClientSecretNotifierService;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Completable.complete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.*;

public class ProtectedResourceSecretManagerTest {

    @Mock
    private ProtectedResourceService protectedResourceService;

    @Mock
    private EventManager eventManager;

    @Mock
    private ClientSecretNotifierService clientSecretNotifierService;

    @InjectMocks
    private ProtectedResourceSecretManager manager;

    @Before
    public void setUp() throws Exception {
        openMocks(this);
    }

    @Test
    public void shouldInitClientSecrets() throws Exception {
        ClientSecret clientSecret1 = new ClientSecret();
        clientSecret1.setId("id1");
        ClientSecret clientSecret2 = new ClientSecret();
        clientSecret2.setId("id2");

        ProtectedResource resource = new ProtectedResource();
        resource.setClientSecrets(List.of(clientSecret1, clientSecret2));

        when(protectedResourceService.findAll()).thenReturn(Flowable.just(resource));
        when(clientSecretNotifierService.unregisterClientSecretExpiration(eq("id1"))).thenReturn(complete());
        when(clientSecretNotifierService.unregisterClientSecretExpiration(eq("id2"))).thenReturn(complete());
        when(clientSecretNotifierService.registerClientSecretExpiration(any(ProtectedResource.class), any())).thenReturn(complete());

        manager.doStart();

        verify(clientSecretNotifierService, timeout(1000).times(1)).unregisterClientSecretExpiration(eq("id1"));
        verify(clientSecretNotifierService, timeout(1000).times(1)).unregisterClientSecretExpiration(eq("id2"));
        verify(clientSecretNotifierService, timeout(1000).times(1)).registerClientSecretExpiration(any(ProtectedResource.class), eq(clientSecret1));
        verify(clientSecretNotifierService, timeout(1000).times(1)).registerClientSecretExpiration(any(ProtectedResource.class), eq(clientSecret2));
    }

    @Test
    public void shouldCreateClientSecretNotifications() throws Exception {
        ClientSecret clientSecret1 = new ClientSecret();
        clientSecret1.setId("id1");

        ProtectedResource resource = new ProtectedResource();
        resource.setId("resourceId");
        resource.setClientSecrets(List.of(clientSecret1));

        when(protectedResourceService.findById("resourceId")).thenReturn(Maybe.just(resource));

        when(clientSecretNotifierService.registerClientSecretExpiration(any(ProtectedResource.class), any())).thenReturn(complete());

        TestObserver<Void> observer = manager.handle(new SimpleEvent<>(ProtectedResourceSecretEvent.CREATE, new Payload(clientSecret1.getId(), ReferenceType.PROTECTED_RESOURCE, resource.getId(), Action.CREATE))).test();
        
        observer.await(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(clientSecretNotifierService, times(1)).registerClientSecretExpiration(any(ProtectedResource.class), eq(clientSecret1));
    }

    @Test
    public void shouldUpdateClientSecretNotifications() throws Exception {
        ClientSecret clientSecret1 = new ClientSecret();
        clientSecret1.setId("id1");

        ProtectedResource resource = new ProtectedResource();
        resource.setId("resourceId");
        resource.setClientSecrets(List.of(clientSecret1));

        when(protectedResourceService.findById("resourceId")).thenReturn(Maybe.just(resource));

        when(clientSecretNotifierService.registerClientSecretExpiration(any(ProtectedResource.class), any())).thenReturn(complete());
        when(clientSecretNotifierService.unregisterClientSecretExpiration("id1")).thenReturn(complete());
        when(clientSecretNotifierService.deleteClientSecretExpirationAcknowledgement("id1")).thenReturn(complete());

        TestObserver<Void> observer = manager.handle(new SimpleEvent<>(ProtectedResourceSecretEvent.RENEW, new Payload(clientSecret1.getId(), ReferenceType.PROTECTED_RESOURCE, resource.getId(), Action.UPDATE))).test();

        observer.await(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(clientSecretNotifierService, times(1)).unregisterClientSecretExpiration(eq("id1"));
        verify(clientSecretNotifierService, times(1)).deleteClientSecretExpirationAcknowledgement(eq("id1"));
        verify(clientSecretNotifierService, times(1)).registerClientSecretExpiration(any(ProtectedResource.class), eq(clientSecret1));
    }

    @Test
    public void shouldDestroyClientSecretNotifications() throws Exception {
        ClientSecret clientSecret1 = new ClientSecret();
        clientSecret1.setId("id1");

        ProtectedResource resource = new ProtectedResource();
        resource.setId("resourceId");
        resource.setClientSecrets(List.of(clientSecret1));

        when(clientSecretNotifierService.unregisterClientSecretExpiration(eq("id1"))).thenReturn(complete());
        when(clientSecretNotifierService.deleteClientSecretExpirationAcknowledgement("id1")).thenReturn(complete());

        TestObserver<Void> observer = manager.handle(new SimpleEvent<>(ProtectedResourceSecretEvent.DELETE, new Payload(clientSecret1.getId(), ReferenceType.PROTECTED_RESOURCE, resource.getId(), Action.DELETE))).test();
                
        observer.await(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(clientSecretNotifierService, times(1)).unregisterClientSecretExpiration(eq("id1"));
        verify(clientSecretNotifierService, times(1)).deleteClientSecretExpirationAcknowledgement(eq("id1"));
    }
}
