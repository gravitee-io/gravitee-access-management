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
package io.gravitee.am.gateway.handler.common.authorizationengine.impl;

import io.gravitee.am.authorizationengine.api.AuthorizationEngineProvider;
import io.gravitee.am.common.event.AuthorizationBundleEvent;
import io.gravitee.am.common.event.AuthorizationEngineEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.model.AuthorizationBundle;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.authorizationengine.core.AuthorizationEnginePluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.repository.management.api.AuthorizationBundleRepository;
import io.gravitee.am.repository.management.api.AuthorizationEngineRepository;
import io.gravitee.common.event.Event;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationEngineManagerImplTest {

    @Mock
    private Domain domain;

    @Mock
    private AuthorizationEnginePluginManager authorizationEnginePluginManager;

    @Mock
    private AuthorizationEngineRepository authorizationEngineRepository;

    @Mock
    private AuthorizationBundleRepository authorizationBundleRepository;

    @Mock
    private EventManager eventManager;

    @Mock
    private AuthorizationEngineProvider mockProvider;

    @Mock
    private DomainReadinessService domainReadinessService;

    @InjectMocks
    private AuthorizationEngineManagerImpl manager;

    private AuthorizationEngine testEngine;

    @BeforeAll
    static void setupSchedulers() {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setComputationSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setNewThreadSchedulerHandler(s -> Schedulers.trampoline());
    }

    @AfterAll
    static void resetSchedulers() {
        RxJavaPlugins.reset();
    }

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

    @Test
    void shouldLoadEnginesOnInitialization() {
        // given
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");
        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);

        // when
        manager.afterPropertiesSet();

        // then - give async operation time to complete
        verify(authorizationEngineRepository, timeout(1000).times(1)).findByDomain("domain-id");
        verify(authorizationEnginePluginManager, timeout(1000).times(1)).create(any(ProviderConfiguration.class));
        verify(domainReadinessService, timeout(1000).times(1)).initPluginSync("domain-id", "engine-id", "AUTHORIZATION_ENGINE");
        verify(domainReadinessService, timeout(1000).times(1)).pluginLoaded("domain-id", "engine-id");
    }

    @Test
    void shouldHandleErrorsDuringInitialization() {
        // given
        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.error(new RuntimeException("Database error")));
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        // when - should not throw exception
        manager.afterPropertiesSet();

        // then
        verify(authorizationEngineRepository, times(1)).findByDomain("domain-id");
        verify(domainReadinessService, never()).pluginLoaded(any(), any());
        verify(domainReadinessService).pluginInitFailed("domain-id", "AUTHORIZATION_ENGINE", "Database error");
    }

    @Test
    void shouldSubscribeToEventsOnStart() throws Exception {
        // given
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        // when
        manager.doStart();

        // then
        verify(eventManager, times(1)).subscribeForEvents(eq(manager), eq(AuthorizationEngineEvent.class), eq("domain-id"));
    }

    @Test
    void shouldUnsubscribeFromEventsOnStop() throws Exception {
        // given
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        // when
        manager.doStop();

        // then
        verify(eventManager, times(1)).unsubscribeForEvents(eq(manager), eq(AuthorizationEngineEvent.class), eq("domain-id"));
    }

    @Test
    void shouldGetProviderById() {
        // given
        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        manager.afterPropertiesSet();

        // when
        TestObserver<AuthorizationEngineProvider> observer = manager.get("engine-id").test();

        // then
        observer.assertComplete();
        observer.assertValue(mockProvider);
    }

    @Test
    void shouldReturnEmptyWhenProviderNotFound() {
        // when
        TestObserver<AuthorizationEngineProvider> observer = manager.get("non-existent-id").test();

        // then
        observer.assertComplete();
        observer.assertNoValues();
    }

    @Test
    void shouldHandleDeployEvent() {
        // given
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(authorizationEngineRepository.findById("engine-id"))
                .thenReturn(Maybe.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        // when
        manager.onEvent(event);

        // then - give async operation time to complete
        verify(authorizationEngineRepository, timeout(1000).times(1)).findById("engine-id");
        verify(authorizationEnginePluginManager, timeout(1000).times(1)).create(any(ProviderConfiguration.class));
        verify(domainReadinessService, timeout(1000).times(1)).initPluginSync("domain-id", "engine-id", "AUTHORIZATION_ENGINE");
        verify(domainReadinessService, timeout(1000).times(1)).pluginLoaded("domain-id", "engine-id");
    }

    @Test
    void shouldHandleUpdateEvent() {
        // given
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.UPDATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.UPDATE);
        when(event.content()).thenReturn(payload);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        when(authorizationEngineRepository.findById("engine-id"))
                .thenReturn(Maybe.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);

        // when
        manager.onEvent(event);

        // then
        verify(authorizationEngineRepository, timeout(1000).times(1)).findById("engine-id");
        verify(authorizationEnginePluginManager, timeout(1000).times(1)).create(any(ProviderConfiguration.class));
        verify(domainReadinessService, timeout(1000).times(1)).initPluginSync("domain-id", "engine-id", "AUTHORIZATION_ENGINE");
        verify(domainReadinessService, timeout(1000).times(1)).pluginLoaded("domain-id", "engine-id");
    }

    @Test
    void shouldHandleUndeployEvent() throws Exception {
        // given - first deploy an engine
        when(mockProvider.stop()).thenReturn(mockProvider);
        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        manager.afterPropertiesSet();

        // when - undeploy event
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.DELETE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.UNDEPLOY);
        when(event.content()).thenReturn(payload);
        manager.onEvent(event);

        // then - provider should be removed
        TestObserver<AuthorizationEngineProvider> observer = manager.get("engine-id").test();
        observer.assertNoValues();
        verify(mockProvider, times(1)).stop();
        verify(domainReadinessService, times(1)).pluginUnloaded("domain-id", "engine-id");
    }

    @Test
    void shouldIgnoreEventForDifferentDomain() {
        // given
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "other-domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.content()).thenReturn(payload);
        when(domain.getId()).thenReturn("domain-id");

        // when
        manager.onEvent(event);

        // then - should not process the event
        verify(authorizationEngineRepository, never()).findById(any());
        verify(authorizationEnginePluginManager, never()).create(any());
    }

    @Test
    void shouldIgnoreEventForDifferentReferenceType() {
        // given
        Payload payload = new Payload("engine-id", ReferenceType.ORGANIZATION, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.content()).thenReturn(payload);

        // when
        manager.onEvent(event);

        // then - should not process the event
        verify(authorizationEngineRepository, never()).findById(any());
        verify(authorizationEnginePluginManager, never()).create(any());
    }

    @Test
    void shouldHandleErrorWhenEngineNotFoundDuringDeploy() {
        // given
        Payload payload = new Payload("non-existent-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.DEPLOY);
        when(event.content()).thenReturn(payload);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        when(authorizationEngineRepository.findById("non-existent-id"))
                .thenReturn(Maybe.empty());

        // when
        manager.onEvent(event);

        // then - should not attempt to create provider
        verify(authorizationEnginePluginManager, never()).create(any(ProviderConfiguration.class));
    }

    @Test
    void shouldHandleErrorWhenProviderCreationFails() {
        // given
        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(authorizationEngineRepository.findById("engine-id"))
                .thenReturn(Maybe.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenThrow(new RuntimeException("Plugin creation failed"));
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        // when
        manager.onEvent(event);

        // then - should handle the error gracefully
        verify(authorizationEngineRepository, timeout(1000).times(1)).findById("engine-id");
        verify(domainReadinessService, timeout(1000).times(1)).initPluginSync("domain-id", "engine-id", "AUTHORIZATION_ENGINE");
        verify(domainReadinessService, timeout(1000).times(1)).pluginFailed(eq("domain-id"), eq("engine-id"), any());

        // Provider should not be in cache
        TestObserver<AuthorizationEngineProvider> observer = manager.get("engine-id").test();
        observer.assertNoValues();
    }

    @Test
    void shouldStopOldProviderBeforeDeployingNew() throws Exception {
        // given - deploy initial provider
        AuthorizationEngineProvider oldProvider = mock(AuthorizationEngineProvider.class);
        AuthorizationEngineProvider newProvider = mock(AuthorizationEngineProvider.class);

        when(oldProvider.stop()).thenReturn(oldProvider);

        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(oldProvider);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        manager.afterPropertiesSet();

        // when - redeploy with new provider
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(newProvider);

        Payload payload = new Payload("engine-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationEngineEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationEngineEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(authorizationEngineRepository.findById("engine-id"))
                .thenReturn(Maybe.just(testEngine));

        manager.onEvent(event);

        // then - old provider should be stopped
        verify(oldProvider, timeout(1000).times(1)).stop();
    }

    @Test
    void shouldUseCorrectProviderConfiguration() {
        // given
        ArgumentCaptor<ProviderConfiguration> configCaptor = ArgumentCaptor.forClass(ProviderConfiguration.class);

        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");
        // when
        manager.afterPropertiesSet();

        // then - give async operation time to complete
        verify(authorizationEnginePluginManager, timeout(1000).times(1)).create(configCaptor.capture());
        ProviderConfiguration capturedConfig = configCaptor.getValue();
        assertNotNull(capturedConfig);
        assertEquals("openfga", capturedConfig.getType());
        assertEquals("{\"connectionUri\":\"http://localhost:8080\"}", capturedConfig.getConfiguration());
    }

    @Test
    void shouldClearAllProvidersOnStop() throws Exception {
        // given - deploy a provider
        when(mockProvider.stop()).thenReturn(mockProvider);
        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        manager.afterPropertiesSet();

        // when
        manager.doStop();

        // then
        verify(mockProvider, times(1)).stop();
        verify(domainReadinessService, times(1)).pluginUnloaded("domain-id", "engine-id");

        // Provider should be cleared
        TestObserver<AuthorizationEngineProvider> observer = manager.get("engine-id").test();
        observer.assertNoValues();
    }

    @Test
    void shouldGetDefaultProviderWhenSingleProviderAvailable() {
        // given
        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        manager.afterPropertiesSet();

        // when
        TestObserver<AuthorizationEngineProvider> observer = manager.getDefault().test();

        // then
        observer.assertComplete();
        observer.assertValue(mockProvider);
    }

    @Test
    void shouldReturnEmptyWhenNoProvidersAvailable() {
        // when
        TestObserver<AuthorizationEngineProvider> observer = manager.getDefault().test();

        // then
        observer.assertComplete();
        observer.assertNoValues();
    }

    @Test
    void shouldThrowExceptionWhenMultipleProvidersAvailable() {
        // given - create multiple engines
        AuthorizationEngine testEngine2 = new AuthorizationEngine();
        testEngine2.setId("engine-id-2");
        testEngine2.setName("Test Engine 2");
        testEngine2.setType("openfga");
        testEngine2.setReferenceType(ReferenceType.DOMAIN);
        testEngine2.setReferenceId("domain-id");
        testEngine2.setConfiguration("{\"connectionUri\":\"http://localhost:8080\"}");

        AuthorizationEngineProvider mockProvider2 = mock(AuthorizationEngineProvider.class);

        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine, testEngine2));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider)
                .thenReturn(mockProvider2);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        manager.afterPropertiesSet();

        // when
        TestObserver<AuthorizationEngineProvider> observer = manager.getDefault().test();

        // then - should throw IllegalStateException
        observer.assertError(IllegalStateException.class);
        observer.assertError(throwable ->
            throwable instanceof IllegalStateException &&
            throwable.getMessage().equals("Multiple authorization engine providers found. Only one provider is allowed per domain."));
    }

    // --- Authorization Bundle event tests ---

    @Test
    void shouldHotReloadFromBundleOnDeployEvent() throws Exception {
        // given - deploy an engine provider with bundleId in its config
        testEngine.setConfiguration("{\"connectionUri\":\"http://localhost:8080\",\"bundleId\":\"bundle-1\"}");

        AuthorizationBundle bundle = new AuthorizationBundle();
        bundle.setId("bundle-1");
        bundle.setDomainId("domain-id");
        bundle.setPolicies("permit(principal, action, resource);");
        bundle.setEntities("[{\"uid\":{\"type\":\"User\",\"id\":\"alice\"}}]");
        bundle.setSchema("{\"entityTypes\":{}}");
        bundle.setVersion(1);

        when(authorizationBundleRepository.findById("bundle-1"))
                .thenReturn(Maybe.just(bundle));
        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");
        manager.afterPropertiesSet();

        // when - simulate bundle DEPLOY event via the bundleEventListener
        Payload payload = new Payload("bundle-1", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationBundleEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationBundleEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        manager.doStart();

        // Capture the bundle event listener that was registered
        ArgumentCaptor<io.gravitee.common.event.EventListener> listenerCaptor = ArgumentCaptor.forClass(io.gravitee.common.event.EventListener.class);
        verify(eventManager).subscribeForEvents(listenerCaptor.capture(), eq(AuthorizationBundleEvent.class), eq("domain-id"));

        // Fire the event through captured listener
        io.gravitee.common.event.EventListener<AuthorizationBundleEvent, Payload> capturedListener = listenerCaptor.getValue();
        capturedListener.onEvent(event);

        // then - called during initial deploy push AND hot-reload
        verify(authorizationBundleRepository, timeout(1000).atLeast(2)).findById("bundle-1");
        verify(mockProvider, timeout(1000).atLeast(2)).updateConfig(
                "permit(principal, action, resource);",
                "[{\"uid\":{\"type\":\"User\",\"id\":\"alice\"}}]",
                "{\"entityTypes\":{}}"
        );
    }

    @Test
    void shouldClearConfigOnBundleUndeployEvent() throws Exception {
        // given - deploy an engine provider with bundleId in its config
        testEngine.setConfiguration("{\"connectionUri\":\"http://localhost:8080\",\"bundleId\":\"bundle-1\"}");

        // Mock bundle for the initial push during deploy
        AuthorizationBundle bundle = new AuthorizationBundle();
        bundle.setId("bundle-1");
        bundle.setDomainId("domain-id");
        bundle.setPolicies("permit(principal, action, resource);");
        bundle.setEntities("[]");
        bundle.setSchema("{}");
        bundle.setVersion(1);

        when(authorizationBundleRepository.findById("bundle-1"))
                .thenReturn(Maybe.just(bundle));
        when(authorizationEngineRepository.findByDomain("domain-id"))
                .thenReturn(Flowable.just(testEngine));
        when(authorizationEnginePluginManager.create(any(ProviderConfiguration.class)))
                .thenReturn(mockProvider);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");
        manager.afterPropertiesSet();

        // when - simulate bundle UNDEPLOY event
        Payload payload = new Payload("bundle-1", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.DELETE);
        Event<AuthorizationBundleEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(AuthorizationBundleEvent.UNDEPLOY);
        when(event.content()).thenReturn(payload);

        manager.doStart();

        ArgumentCaptor<io.gravitee.common.event.EventListener> listenerCaptor = ArgumentCaptor.forClass(io.gravitee.common.event.EventListener.class);
        verify(eventManager).subscribeForEvents(listenerCaptor.capture(), eq(AuthorizationBundleEvent.class), eq("domain-id"));

        io.gravitee.common.event.EventListener<AuthorizationBundleEvent, Payload> capturedListener = listenerCaptor.getValue();
        capturedListener.onEvent(event);

        // then - should push null config to clear engine state
        verify(mockProvider, timeout(1000)).updateConfig(null, null, null);
    }

    @Test
    void shouldIgnoreBundleEventForDifferentDomain() throws Exception {
        // given
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("Test Domain");

        Payload payload = new Payload("bundle-1", ReferenceType.DOMAIN, "other-domain-id", io.gravitee.am.common.event.Action.CREATE);
        Event<AuthorizationBundleEvent, Payload> event = mock(Event.class);
        when(event.content()).thenReturn(payload);

        manager.doStart();

        ArgumentCaptor<io.gravitee.common.event.EventListener> listenerCaptor = ArgumentCaptor.forClass(io.gravitee.common.event.EventListener.class);
        verify(eventManager).subscribeForEvents(listenerCaptor.capture(), eq(AuthorizationBundleEvent.class), eq("domain-id"));

        io.gravitee.common.event.EventListener<AuthorizationBundleEvent, Payload> capturedListener = listenerCaptor.getValue();
        capturedListener.onEvent(event);

        // then - should not fetch any bundle
        verify(authorizationBundleRepository, never()).findById(any());
        verify(mockProvider, never()).updateConfig(any(), any(), any());
    }
}
