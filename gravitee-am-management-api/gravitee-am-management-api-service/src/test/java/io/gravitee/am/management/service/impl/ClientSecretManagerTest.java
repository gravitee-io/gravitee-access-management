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
import io.gravitee.am.common.event.ApplicationSecretEvent;
import io.gravitee.am.management.service.ClientSecretNotifierService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.ApplicationSecretService;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static io.reactivex.rxjava3.core.Completable.complete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;


public class ClientSecretManagerTest {


    @Mock
    private ApplicationService applicationService;

    @Mock
    private ApplicationSecretService applicationSecretService;

    @Mock
    private EventManager eventManager;

    @Mock
    private ClientSecretNotifierService clientSecretNotifierService;

    @InjectMocks
    private ClientSecretManager manager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldInitClientSecrets() throws Exception {
        ClientSecret clientSecret1 = new ClientSecret();
        clientSecret1.setId("id1");
        ClientSecret clientSecret2 = new ClientSecret();
        clientSecret2.setId("id2");

        Application application = new Application();
        application.setSecrets(List.of(clientSecret1, clientSecret2));

        Mockito.when(applicationService.findAll()).thenReturn(Flowable.just(application));
        Mockito.when(clientSecretNotifierService.unregisterClientSecretExpiration(eq("id1"))).thenReturn(complete());
        Mockito.when(clientSecretNotifierService.unregisterClientSecretExpiration(eq("id2"))).thenReturn(complete());
        Mockito.when(clientSecretNotifierService.registerClientSecretExpiration(any(), any())).thenReturn(complete());

        manager.doStart();
    }

    @Test
    public void shouldCreateClientSecretNotifications() {
        ClientSecret clientSecret1 = new ClientSecret();
        clientSecret1.setId("id1");

        Application application = new Application();
        application.setId("applicationId");
        application.setSecrets(List.of(clientSecret1));

        Mockito.when(applicationService.findById("applicationId")).thenReturn(Maybe.just(application));
        Mockito.when(applicationSecretService.findById(application.getId(), clientSecret1.getId())).thenReturn(Maybe.just(clientSecret1));

        Mockito.when(clientSecretNotifierService.registerClientSecretExpiration(any(), any())).thenReturn(complete());
        Mockito.when(clientSecretNotifierService.unregisterClientSecretExpiration("id1")).thenReturn(complete());


        manager.handle(new SimpleEvent<>(ApplicationSecretEvent.CREATE, new Payload(clientSecret1.getId(), ReferenceType.APPLICATION, application.getId(), Action.CREATE)))
                .test()
                .assertComplete();
    }

    @Test
    public void shouldUpdateClientSecretNotifications() {
        ClientSecret clientSecret1 = new ClientSecret();
        clientSecret1.setId("id1");

        Application application = new Application();
        application.setId("applicationId");
        application.setSecrets(List.of(clientSecret1));

        Mockito.when(applicationService.findById("applicationId")).thenReturn(Maybe.just(application));
        Mockito.when(applicationSecretService.findById(application.getId(), clientSecret1.getId())).thenReturn(Maybe.just(clientSecret1));

        Mockito.when(clientSecretNotifierService.registerClientSecretExpiration(any(), any())).thenReturn(complete());
        Mockito.when(clientSecretNotifierService.unregisterClientSecretExpiration("id1")).thenReturn(complete());
        Mockito.when(clientSecretNotifierService.deleteClientSecretExpirationAcknowledgement("id1")).thenReturn(complete());


        manager.handle(new SimpleEvent<>(ApplicationSecretEvent.RENEW, new Payload(clientSecret1.getId(), ReferenceType.APPLICATION, application.getId(), Action.UPDATE)))
                .test()
                .assertComplete();
    }

    @Test
    public void shouldDestroyClientSecretNotifications() {
        ClientSecret clientSecret1 = new ClientSecret();
        clientSecret1.setId("id1");

        Application application = new Application();
        application.setId("applicationId");
        application.setSecrets(List.of(clientSecret1));

        Mockito.when(applicationService.findById("applicationId")).thenReturn(Maybe.just(application));

        Completable spy = Mockito.spy(complete());

        Mockito.when(clientSecretNotifierService.registerClientSecretExpiration(any(), any())).thenReturn(spy);
        Mockito.when(clientSecretNotifierService.unregisterClientSecretExpiration(eq("id1"))).thenReturn(complete());
        Mockito.when(clientSecretNotifierService.deleteClientSecretExpirationAcknowledgement("id1")).thenReturn(complete());

        manager.handle(new SimpleEvent<>(ApplicationSecretEvent.DELETE, new Payload(clientSecret1.getId(), ReferenceType.APPLICATION, application.getId(), Action.DELETE)))
                .test()
                .assertComplete();

    }

}