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

import io.gravitee.am.authorizationengine.api.AuthorizationEngineProvider;
import io.gravitee.am.common.event.AuthorizationEngineEvent;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.authorizationengine.core.AuthorizationEnginePluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.service.AuthorizationEngineService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventManager;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationEngineManagerImplTest {

    @Mock
    private AuthorizationEnginePluginManager authorizationEnginePluginManager;

    @Mock
    private AuthorizationEngineService authorizationEngineService;

    @Mock
    private EventManager eventManager;

    @Mock
    private AuthorizationEngineProvider mockProvider;

    @InjectMocks
    private AuthorizationEngineManagerImpl manager;

    private AuthorizationEngine testEngine;

    @BeforeEach
    void setUp() {
        testEngine = new AuthorizationEngine();
        testEngine.setId("engine-id");
        testEngine.setName("Test Engine");
        testEngine.setType("openfga");
        testEngine.setReferenceType(ReferenceType.DOMAIN);
        testEngine.setReferenceId("domain-id");
        testEngine.setConfiguration("{\"connectionUri\":\"http://localhost:8080\"}");
    }

    private void startManager() {
        try {
            manager.doStart();
        } catch (Exception e) {
            fail("Failed to start manager");
        }
    }
    
    @Test
    void shouldLoadAllEnginesOnStart() throws Exception {
        // given
        AuthorizationEngine engine1 = new AuthorizationEngine();
        engine1.setId("engine-1");
        engine1.setName("Engine 1");
        engine1.setType("openfga");

        AuthorizationEngine engine2 = new AuthorizationEngine();
        engine2.setId("engine-2");
        engine2.setName("Engine 2");
        engine2.setType("openfga");

        when(authorizationEngineService.findAll())
                .thenReturn(Flowable.just(engine1, engine2));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);

        // when
        manager.doStart();

        // then
        verify(authorizationEngineService, times(1)).findAll();
        verify(authorizationEnginePluginManager, times(2)).create(any(ProviderConfiguration.class));
    }

    @Test
    void shouldSubscribeToEventsOnStart() throws Exception {
        // given
        when(authorizationEngineService.findAll())
                .thenReturn(Flowable.empty());

        // when
        manager.doStart();

        // then
        verify(eventManager, times(1)).subscribeForEvents(eq(manager), eq(AuthorizationEngineEvent.class));
    }

    @Test
    void shouldHandleErrorsDuringStartup() throws Exception {
        // given
        AuthorizationEngine badEngine = new AuthorizationEngine();
        badEngine.setId("bad-engine");
        badEngine.setType("invalid-type");

        when(authorizationEngineService.findAll())
                .thenReturn(Flowable.just(badEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenThrow(new RuntimeException("Plugin error"));

        // when - should not throw exception
        assertDoesNotThrow(() -> manager.doStart());

        // then - event subscription should still happen
        verify(eventManager, times(1)).subscribeForEvents(eq(manager), eq(AuthorizationEngineEvent.class));
    }

    @Test
    void shouldGetProviderById() {
        // given
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);

        when(authorizationEngineService.findAll())
                .thenReturn(Flowable.just(testEngine));

        startManager();

        // when
        TestObserver<AuthorizationEngineProvider> observer = manager.getProvider("engine-id").test();

        // then
        observer.assertComplete();
        observer.assertValue(mockProvider);
    }

    @Test
    void shouldReturnEmptyWhenProviderNotFound() {

        // when
        TestObserver<AuthorizationEngineProvider> observer = manager.getProvider("non-existent-id").test();

        // then
        observer.assertComplete();
        observer.assertNoValues();
    }

    @Test
    void shouldCacheProvider() {
        // given
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(authorizationEngineService.findAll())
                .thenReturn(Flowable.just(testEngine));

        startManager();

        // when - call getProvider twice
        manager.getProvider("engine-id").test();
        manager.getProvider("engine-id").test();

        // then
        verify(authorizationEnginePluginManager, times(1)).create(any(ProviderConfiguration.class));
    }

    @Test
    void shouldDeployEngineOnDeployEvent() {
        // given
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(authorizationEngineService.findById("engine-id"))
                .thenReturn(Maybe.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);

        // when
        manager.onEvent(event);

        // then - give async operation time to complete
        verify(authorizationEngineService, timeout(1000).times(1)).findById("engine-id");
        verify(authorizationEnginePluginManager, timeout(1000).times(1)).create(any(ProviderConfiguration.class));
    }

    @Test
    void shouldUpdateEngineOnUpdateEvent() {
        // given
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.UPDATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.UPDATE);
        when(event.content()).thenReturn(payload);

        when(authorizationEngineService.findById("engine-id"))
                .thenReturn(Maybe.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);

        // when
        manager.onEvent(event);

        // then
        verify(authorizationEngineService, timeout(1000).times(1)).findById("engine-id");
        verify(authorizationEnginePluginManager, timeout(1000).times(1)).create(any(ProviderConfiguration.class));
    }

    @Test
    void shouldRemoveEngineOnUndeployEvent() throws Exception {
        // given - first deploy an engine
        when(mockProvider.stop()).thenReturn(mockProvider);
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(authorizationEngineService.findAll())
                .thenReturn(Flowable.just(testEngine));

        startManager();

        // Verify provider is cached
        TestObserver<AuthorizationEngineProvider> beforeObserver = manager.getProvider("engine-id").test();
        beforeObserver.assertValue(mockProvider);

        // when - undeploy event
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.DELETE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.UNDEPLOY);
        when(event.content()).thenReturn(payload);
        manager.onEvent(event);

        // then - provider should be removed
        TestObserver<AuthorizationEngineProvider> afterObserver = manager.getProvider("engine-id").test();
        afterObserver.assertNoValues();
        verify(mockProvider, times(1)).stop();
    }

    @Test
    void shouldStopOldProviderBeforeDeployingNew() throws Exception {
        // given - deploy initial provider
        AuthorizationEngineProvider oldProvider = mock(AuthorizationEngineProvider.class);
        AuthorizationEngineProvider newProvider = mock(AuthorizationEngineProvider.class);

        when(oldProvider.stop()).thenReturn(oldProvider);

        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(oldProvider)
                .thenReturn(newProvider);
        when(authorizationEngineService.findAll())
                .thenReturn(Flowable.just(testEngine));

        startManager();

        // when - redeploy with new provider
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(authorizationEngineService.findById("engine-id"))
                .thenReturn(Maybe.just(testEngine));

        manager.onEvent(event);

        // then - old provider should be stopped
        verify(oldProvider, timeout(1000).times(1)).stop();
    }

    @Test
    void shouldHandleErrorsDuringDeploy() {
        // given
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(authorizationEngineService.findById("engine-id"))
                .thenReturn(Maybe.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenThrow(new RuntimeException("Plugin creation failed"));

        // when - should not throw exception
        assertDoesNotThrow(() -> manager.onEvent(event));

        // then - provider should not be in cache
        TestObserver<AuthorizationEngineProvider> observer = manager.getProvider("engine-id").test();
        observer.assertNoValues();
    }

    @Test
    void shouldHandleEngineNotFoundDuringDeploy() {
        // given
        Payload payload = new Payload("non-existent-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(authorizationEngineService.findById("non-existent-id"))
                .thenReturn(Maybe.empty());

        // when
        manager.onEvent(event);

        // then - should not attempt to create provider
        verify(authorizationEnginePluginManager, never()).create(any(ProviderConfiguration.class));
    }

    @Test
    void shouldUseCorrectProviderConfiguration() {
        // given
        ArgumentCaptor<ProviderConfiguration> configCaptor = ArgumentCaptor.forClass(ProviderConfiguration.class);

        when(authorizationEngineService.findAll())
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);

        // when
        startManager();

        // then
        verify(authorizationEnginePluginManager).create(configCaptor.capture());
        ProviderConfiguration capturedConfig = configCaptor.getValue();
        assertEquals("openfga", capturedConfig.getType());
        assertEquals("{\"connectionUri\":\"http://localhost:8080\"}", capturedConfig.getConfiguration());
    }
}