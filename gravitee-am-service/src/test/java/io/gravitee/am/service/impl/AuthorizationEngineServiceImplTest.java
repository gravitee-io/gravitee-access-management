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
package io.gravitee.am.service.impl;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.AuthorizationEngineRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AuthorizationEngineNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthorizationEngine;
import io.gravitee.am.service.model.UpdateAuthorizationEngine;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationEngineServiceImplTest {

    @Mock
    private AuthorizationEngineRepository authorizationEngineRepository;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthorizationEngineServiceImpl service;

    private final User principal = new DefaultUser("test-user");

    @Test
    void shouldCreateAuthorizationEngine() {
        // given
        NewAuthorizationEngine newEngine = new NewAuthorizationEngine();
        newEngine.setName("Test Engine");
        newEngine.setType("openfga");
        newEngine.setConfiguration("{\"connectionUri\":\"http://localhost:8080\"}");

        when(authorizationEngineRepository.create(any(AuthorizationEngine.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class)))
                .thenReturn(Single.just(new Event()));
        doNothing().when(auditService).report(any(AuditBuilder.class));

        // when
        TestObserver<AuthorizationEngine> observer = service.create(
                "domain-id",
                newEngine,
                principal
        ).test();

        // then
        observer.assertComplete();
        observer.assertValue(engine -> {
            assertNotNull(engine.getId());
            assertEquals("Test Engine", engine.getName());
            assertEquals("openfga", engine.getType());
            assertEquals(ReferenceType.DOMAIN, engine.getReferenceType());
            assertEquals("domain-id", engine.getReferenceId());
            assertNotNull(engine.getCreatedAt());
            assertNotNull(engine.getUpdatedAt());
            return true;
        });

        verify(authorizationEngineRepository, times(1)).create(any(AuthorizationEngine.class));
        verify(eventService, times(1)).create(any(Event.class));
    }

    @Test
    void shouldGenerateIdIfNotProvided() {
        // given
        NewAuthorizationEngine newEngine = new NewAuthorizationEngine();
        newEngine.setName("Test Engine");
        newEngine.setType("openfga");
        // No ID provided

        when(authorizationEngineRepository.create(any(AuthorizationEngine.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class)))
                .thenReturn(Single.just(new Event()));
        doNothing().when(auditService).report(any(AuditBuilder.class));

        // when
        TestObserver<AuthorizationEngine> observer = service.create(
                "domain-id",
                newEngine,
                principal
        ).test();

        // then
        observer.assertComplete();
        observer.assertValue(engine -> {
            assertNotNull(engine.getId());
            assertFalse(engine.getId().isEmpty());
            return true;
        });
    }

    @Test
    void shouldUseProvidedId() {
        // given
        NewAuthorizationEngine newEngine = new NewAuthorizationEngine();
        newEngine.setId("custom-id");
        newEngine.setName("Test Engine");
        newEngine.setType("openfga");

        when(authorizationEngineRepository.create(any(AuthorizationEngine.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class)))
                .thenReturn(Single.just(new Event()));
        doNothing().when(auditService).report(any(AuditBuilder.class));

        // when
        TestObserver<AuthorizationEngine> observer = service.create(
                "domain-id",
                newEngine,
                principal
        ).test();

        // then
        observer.assertComplete();
        observer.assertValue(engine -> {
            assertEquals("custom-id", engine.getId());
            return true;
        });
    }

    @Test
    void shouldSetTimestampsOnCreate() {
        // given
        NewAuthorizationEngine newEngine = new NewAuthorizationEngine();
        newEngine.setName("Test Engine");
        newEngine.setType("openfga");

        ArgumentCaptor<AuthorizationEngine> captor = ArgumentCaptor.forClass(AuthorizationEngine.class);

        when(authorizationEngineRepository.create(any(AuthorizationEngine.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class)))
                .thenReturn(Single.just(new Event()));
        doNothing().when(auditService).report(any(AuditBuilder.class));

        // when
        service.create("domain-id", newEngine, principal).test();

        // then
        verify(authorizationEngineRepository).create(captor.capture());
        AuthorizationEngine captured = captor.getValue();
        assertNotNull(captured.getCreatedAt());
        assertNotNull(captured.getUpdatedAt());
        assertEquals(captured.getCreatedAt(), captured.getUpdatedAt());
    }

    @Test
    void shouldCreateEventAfterCreation() {
        // given
        NewAuthorizationEngine newEngine = new NewAuthorizationEngine();
        newEngine.setName("Test Engine");
        newEngine.setType("openfga");

        AuthorizationEngine createdEngine = new AuthorizationEngine();
        createdEngine.setId("engine-id");
        createdEngine.setReferenceType(ReferenceType.DOMAIN);
        createdEngine.setReferenceId("domain-id");

        when(authorizationEngineRepository.create(any(AuthorizationEngine.class)))
                .thenReturn(Single.just(createdEngine));
        when(eventService.create(any(Event.class)))
                .thenReturn(Single.just(new Event()));
        doNothing().when(auditService).report(any(AuditBuilder.class));

        // when
        service.create("domain-id", newEngine, principal).test();

        // then
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventService).create(eventCaptor.capture());
        Event capturedEvent = eventCaptor.getValue();
        assertEquals("engine-id", capturedEvent.getPayload().getId());
        assertEquals(ReferenceType.DOMAIN, capturedEvent.getPayload().getReferenceType());
        assertEquals("domain-id", capturedEvent.getPayload().getReferenceId());
    }

    @Test
    void shouldHandleRepositoryErrorOnCreate() {
        // given
        NewAuthorizationEngine newEngine = new NewAuthorizationEngine();
        newEngine.setName("Test Engine");
        newEngine.setType("openfga");

        when(authorizationEngineRepository.create(any(AuthorizationEngine.class)))
                .thenReturn(Single.error(new RuntimeException("Database error")));
        doNothing().when(auditService).report(any(AuditBuilder.class));

        // when
        TestObserver<AuthorizationEngine> observer = service.create(
                "domain-id",
                newEngine,
                principal
        ).test();

        // then
        observer.assertError(TechnicalManagementException.class);
        observer.assertError(ex -> ex.getMessage().contains("An error occurs while trying to create an authorization engine"));
        verify(eventService, never()).create(any(Event.class));
    }

    @Test
    void shouldUpdateAuthorizationEngine() {
        // given
        String engineId = "engine-id";
        UpdateAuthorizationEngine updateEngine = new UpdateAuthorizationEngine();
        updateEngine.setName("Updated Engine");
        updateEngine.setConfiguration("{\"connectionUri\":\"http://localhost:9090\"}");

        AuthorizationEngine existingEngine = new AuthorizationEngine();
        existingEngine.setId(engineId);
        existingEngine.setName("Old Engine");
        existingEngine.setType("openfga");
        existingEngine.setReferenceType(ReferenceType.DOMAIN);
        existingEngine.setReferenceId("domain-id");

        AuthorizationEngine updatedEngine = new AuthorizationEngine();
        updatedEngine.setId(engineId);
        updatedEngine.setName(updateEngine.getName());
        updatedEngine.setConfiguration(updateEngine.getConfiguration());
        updatedEngine.setReferenceType(ReferenceType.DOMAIN);
        updatedEngine.setReferenceId("domain-id");

        when(authorizationEngineRepository.findByDomainAndId("domain-id", engineId))
                .thenReturn(Maybe.just(existingEngine));
        when(authorizationEngineRepository.update(any(AuthorizationEngine.class)))
                .thenReturn(Single.just(updatedEngine));
        when(eventService.create(any(Event.class)))
                .thenReturn(Single.just(new Event()));
        doNothing().when(auditService).report(any(AuditBuilder.class));

        // when
        TestObserver<AuthorizationEngine> observer = service.update(
                "domain-id",
                engineId,
                updateEngine,
                principal
        ).test();

        // then
        observer.assertComplete();
        observer.assertValue(engine -> {
            assertEquals("Updated Engine", engine.getName());
            return true;
        });

        verify(authorizationEngineRepository, times(1)).update(any(AuthorizationEngine.class));
        verify(eventService, times(1)).create(any(Event.class));
    }

    @Test
    void shouldUpdateTimestampOnUpdate() {
        // given
        String engineId = "engine-id";
        UpdateAuthorizationEngine updateEngine = new UpdateAuthorizationEngine();
        updateEngine.setName("Updated Engine");

        AuthorizationEngine existingEngine = new AuthorizationEngine();
        existingEngine.setId(engineId);
        existingEngine.setReferenceType(ReferenceType.DOMAIN);
        existingEngine.setReferenceId("domain-id");

        ArgumentCaptor<AuthorizationEngine> captor = ArgumentCaptor.forClass(AuthorizationEngine.class);

        when(authorizationEngineRepository.findByDomainAndId("domain-id", engineId))
                .thenReturn(Maybe.just(existingEngine));
        when(authorizationEngineRepository.update(any(AuthorizationEngine.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class)))
                .thenReturn(Single.just(new Event()));
        doNothing().when(auditService).report(any(AuditBuilder.class));

        // when
        service.update("domain-id", engineId, updateEngine, principal).test();

        // then
        verify(authorizationEngineRepository).update(captor.capture());
        AuthorizationEngine captured = captor.getValue();
        assertNotNull(captured.getUpdatedAt());
    }

    @Test
    void shouldThrowNotFoundWhenEngineDoesNotExistOnUpdate() {
        // given
        String engineId = "non-existent-id";
        UpdateAuthorizationEngine updateEngine = new UpdateAuthorizationEngine();
        updateEngine.setName("Updated Engine");

        when(authorizationEngineRepository.findByDomainAndId("domain-id", engineId))
                .thenReturn(Maybe.empty());

        // when
        TestObserver<AuthorizationEngine> observer = service.update(
                "domain-id",
                engineId,
                updateEngine,
                principal
        ).test();

        // then
        observer.assertError(AuthorizationEngineNotFoundException.class);
        verify(authorizationEngineRepository, never()).update(any(AuthorizationEngine.class));
        verify(eventService, never()).create(any(Event.class));
    }

    @Test
    void shouldDeleteAuthorizationEngine() {
        // given
        String engineId = "engine-id";
        AuthorizationEngine engine = new AuthorizationEngine();
        engine.setId(engineId);
        engine.setReferenceType(ReferenceType.DOMAIN);
        engine.setReferenceId("domain-id");

        when(authorizationEngineRepository.findByDomainAndId("domain-id", engineId))
                .thenReturn(Maybe.just(engine));
        when(authorizationEngineRepository.delete(engineId))
                .thenReturn(Completable.complete());
        when(eventService.create(any(Event.class)))
                .thenReturn(Single.just(new Event()));
        doNothing().when(auditService).report(any(AuditBuilder.class));

        // when
        TestObserver<Void> observer = service.delete(
                "domain-id",
                engineId,
                principal
        ).test();

        // then
        observer.assertComplete();
        verify(authorizationEngineRepository, times(1)).delete(engineId);
        verify(eventService, times(1)).create(any(Event.class));
    }

    @Test
    void shouldThrowNotFoundOnDelete() {
        // given
        String engineId = "non-existent-id";

        when(authorizationEngineRepository.findByDomainAndId("domain-id", engineId))
                .thenReturn(Maybe.empty());

        // when
        TestObserver<Void> observer = service.delete(
                "domain-id",
                engineId,
                principal
        ).test();

        // then
        observer.assertError(AuthorizationEngineNotFoundException.class);
        verify(authorizationEngineRepository, never()).delete(anyString());
        verify(eventService, never()).create(any(Event.class));
    }

    @Test
    void shouldFindById() {
        // given
        String engineId = "engine-id";
        AuthorizationEngine engine = new AuthorizationEngine();
        engine.setId(engineId);
        engine.setName("Test Engine");

        when(authorizationEngineRepository.findById(engineId))
                .thenReturn(Maybe.just(engine));

        // when
        TestObserver<AuthorizationEngine> observer = service.findById(engineId).test();

        // then
        observer.assertComplete();
        observer.assertValue(result -> result.getId().equals(engineId));
    }

    @Test
    void shouldFindAll() {
        // given
        AuthorizationEngine engine1 = new AuthorizationEngine();
        engine1.setId("engine-1");
        AuthorizationEngine engine2 = new AuthorizationEngine();
        engine2.setId("engine-2");

        when(authorizationEngineRepository.findAll())
                .thenReturn(Flowable.just(engine1, engine2));

        // when
        TestSubscriber<AuthorizationEngine> observer = service.findAll().test();

        // then
        observer.assertComplete();
        observer.assertValueCount(2);
        observer.assertValues(engine1, engine2);
    }

    @Test
    void shouldFindByDomain() {
        // given
        String domainId = "domain-id";
        AuthorizationEngine engine1 = new AuthorizationEngine();
        engine1.setId("engine-1");
        engine1.setReferenceId(domainId);

        when(authorizationEngineRepository.findByDomain(domainId))
                .thenReturn(Flowable.just(engine1));

        // when
        TestSubscriber<AuthorizationEngine> observer = service.findByDomain(domainId).test();

        // then
        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertValue(engine -> engine.getReferenceId().equals(domainId));
    }

    @Test
    void shouldHandleRepositoryErrorOnFind() {
        // given
        when(authorizationEngineRepository.findById(anyString()))
                .thenReturn(Maybe.error(new RuntimeException("Database error")));

        // when
        TestObserver<AuthorizationEngine> observer = service.findById("engine-id").test();

        // then
        observer.assertError(TechnicalManagementException.class);
    }
}
