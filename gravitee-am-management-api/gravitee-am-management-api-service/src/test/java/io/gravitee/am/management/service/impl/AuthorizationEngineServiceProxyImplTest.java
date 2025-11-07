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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AuthorizationEnginePluginService;
import io.gravitee.am.management.service.exception.AuthorizationEnginePluginSchemaNotFoundException;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthorizationEngineService;
import io.gravitee.am.service.exception.AuthorizationEngineNotFoundException;
import io.gravitee.am.service.model.NewAuthorizationEngine;
import io.gravitee.am.service.model.UpdateAuthorizationEngine;
import io.gravitee.am.service.reporter.builder.management.AuthorizationEngineAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationEngineServiceProxyImplTest {

    private static final String SCHEMA_WITH_SECRET = "{\"type\":\"object\", \"properties\": {\"safe\": {\"type\": \"string\"}, \"secret\": {\"type\": \"string\", \"sensitive\": true}}}";

    @Mock
    private AuthorizationEngineService service;

    @Mock
    private AuthorizationEnginePluginService pluginService;

    @Mock
    private AuditService auditService;

    private AuthorizationEngineServiceProxyImpl proxy;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final User principal;

    {
        var user = new DefaultUser("tester");
        user.setId("tester");
        this.principal = user;
    }

    @BeforeEach
    void init() {
        proxy = new AuthorizationEngineServiceProxyImpl(service, pluginService, auditService, objectMapper);
    }

    @Test
    void findById_shouldFilterSensitiveData_whenSchemaPresent() {
        var engine = new AuthorizationEngine();
        engine.setId("e1");
        engine.setType("openfga");
        engine.setConfiguration("{\"safe\": \"value\", \"secret\": \"value\"}");

        when(service.findById("e1")).thenReturn(Maybe.just(engine));
        when(pluginService.getSchema("openfga")).thenReturn(Maybe.just(SCHEMA_WITH_SECRET));

        TestObserver<AuthorizationEngine> obs = proxy.findById("e1").test();

        obs.assertComplete();
        obs.assertValue(e -> e.getConfiguration().equals("{\"safe\":\"value\",\"secret\":\"********\"}"));
    }

    @Test
    void findById_shouldClearConfiguration_whenNoSchema() {
        var engine = new AuthorizationEngine();
        engine.setId("e2");
        engine.setType("missing");
        engine.setConfiguration("{\"safe\": \"value\", \"secret\":\"abc\"}");

        when(service.findById("e2")).thenReturn(Maybe.just(engine));
        when(pluginService.getSchema("missing")).thenReturn(Maybe.empty());

        TestObserver<AuthorizationEngine> obs = proxy.findById("e2").test();

        obs.assertComplete();
        obs.assertValue(e -> "{}".equals(e.getConfiguration()));
    }

    @Test
    void findAll_shouldFilterSensitiveData() {
        var e1 = new AuthorizationEngine();
        e1.setId("e1");
        e1.setType("t1");
        e1.setConfiguration("{\"safe\": \"value\", \"secret\": \"s1\"}");
        var e2 = new AuthorizationEngine();
        e2.setId("e2");
        e2.setType("t2");
        e2.setConfiguration("{\"safe\": \"value\", \"secret\": \"s2\"}");

        when(service.findAll()).thenReturn(Flowable.just(e1, e2));
        when(pluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA_WITH_SECRET));

        TestSubscriber<AuthorizationEngine> sub = proxy.findAll().test();
        sub.assertComplete();
        sub.assertValueCount(2);
        var values = sub.values();
        org.junit.jupiter.api.Assertions.assertEquals("{\"safe\":\"value\",\"secret\":\"********\"}", values.get(0).getConfiguration());
        org.junit.jupiter.api.Assertions.assertEquals("{\"safe\":\"value\",\"secret\":\"********\"}", values.get(1).getConfiguration());
    }

    @Test
    void findAll_shouldClearConfiguration_whenNoSchema() {
        var e1 = new AuthorizationEngine();
        e1.setId("e1");
        e1.setType("t1");
        e1.setConfiguration("{\"safe\": \"value\", \"secret\":\"abc\"}");
        var e2 = new AuthorizationEngine();
        e2.setId("e2");
        e2.setType("t2");
        e2.setConfiguration("{\"safe\": \"value\", \"secret\":\"def\"}");

        when(service.findAll()).thenReturn(Flowable.just(e1, e2));
        when(pluginService.getSchema(anyString())).thenReturn(Maybe.empty());

        TestSubscriber<AuthorizationEngine> sub = proxy.findAll().test();
        sub.assertComplete();
        sub.assertValueCount(2);
        var values = sub.values();
        org.junit.jupiter.api.Assertions.assertEquals("{}", values.get(0).getConfiguration());
        org.junit.jupiter.api.Assertions.assertEquals("{}", values.get(1).getConfiguration());
    }

    @Test
    void findByDomain_shouldFilterSensitiveData() {
        var e = new AuthorizationEngine();
        e.setId("e3");
        e.setType("t");
        e.setConfiguration("{\"safe\": \"value\", \"secret\": \"value\"}");

        when(service.findByDomain("d1")).thenReturn(Flowable.just(e));
        when(pluginService.getSchema("t")).thenReturn(Maybe.just(SCHEMA_WITH_SECRET));

        TestSubscriber<AuthorizationEngine> sub = proxy.findByDomain("d1").test();
        sub.assertComplete();
        sub.assertValueCount(1);
        sub.assertValue(v -> v.getConfiguration().equals("{\"safe\":\"value\",\"secret\":\"********\"}"));
    }

    @Test
    void findByDomain_shouldClearConfiguration_whenNoSchema() {
        var e = new AuthorizationEngine();
        e.setId("e3");
        e.setType("missing");
        e.setConfiguration("{\"safe\": \"value\", \"secret\":\"abc\"}");

        when(service.findByDomain("d1")).thenReturn(Flowable.just(e));
        when(pluginService.getSchema("missing")).thenReturn(Maybe.empty());

        TestSubscriber<AuthorizationEngine> sub = proxy.findByDomain("d1").test();
        sub.assertComplete();
        sub.assertValueCount(1);
        sub.assertValue(v -> "{}".equals(v.getConfiguration()));
    }

    @Test
    void create_shouldFilterSensitiveData() {
        var newEngine = new NewAuthorizationEngine();
        newEngine.setName("n");
        newEngine.setType("t");
        newEngine.setConfiguration("{\"safe\": \"value\", \"secret\": \"value\"}");

        var created = new AuthorizationEngine();
        created.setId(UUID.randomUUID().toString());
        created.setType("t");
        created.setConfiguration("{\"safe\": \"value\", \"secret\": \"value\"}");
        created.setReferenceType(ReferenceType.DOMAIN);
        created.setReferenceId("domain");

        when(service.create("domain", newEngine, principal)).thenReturn(Single.just(created));
        when(pluginService.getSchema("t")).thenReturn(Maybe.just(SCHEMA_WITH_SECRET));

        TestObserver<AuthorizationEngine> obs = proxy.create("domain", newEngine, principal).test();

        obs.assertComplete();
        obs.assertValue(e -> e.getId().equals(created.getId()));
        obs.assertValue(e -> e.getConfiguration().equals("{\"safe\":\"value\",\"secret\":\"********\"}"));
    }

    @Test
    void create_shouldThrow_whenNoSchema() {
        var newEngine = new NewAuthorizationEngine();
        newEngine.setName("n");
        newEngine.setType("missing");
        newEngine.setConfiguration("{\"safe\": \"value\", \"secret\": \"value\"}");

        var created = new AuthorizationEngine();
        created.setId(UUID.randomUUID().toString());
        created.setType("missing");
        created.setConfiguration("{\"safe\": \"value\", \"secret\": \"value\"}");
        created.setReferenceType(ReferenceType.DOMAIN);
        created.setReferenceId("domain");

        when(service.create("domain", newEngine, principal)).thenReturn(Single.just(created));
        when(pluginService.getSchema("missing")).thenReturn(Maybe.empty());

        TestObserver<AuthorizationEngine> obs = proxy.create("domain", newEngine, principal).test();

        obs.assertError(AuthorizationEnginePluginSchemaNotFoundException.class);
        
        ArgumentCaptor<AuthorizationEngineAuditBuilder> captor = ArgumentCaptor.forClass(AuthorizationEngineAuditBuilder.class);
        verify(auditService, times(1)).report(captor.capture());
        
        var audit = captor.getValue().build(objectMapper);
        assertEquals(EventType.AUTHORIZATION_ENGINE_CREATED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals("domain", audit.getReferenceId());
        assertNotNull(audit.getActor());
        assertEquals("tester", audit.getActor().getId());
    }

    @Test
    void create_shouldAuditOnSuccess() {
        var newEngine = new NewAuthorizationEngine();
        newEngine.setName("n");
        newEngine.setType("t");
        newEngine.setConfiguration("{}");

        var created = new AuthorizationEngine();
        var createdId = UUID.randomUUID().toString();
        created.setId(createdId);
        created.setType("t");
        created.setConfiguration("{}");
        created.setReferenceType(ReferenceType.DOMAIN);
        created.setReferenceId("domain");
        created.setName("n");

        when(service.create("domain", newEngine, principal)).thenReturn(Single.just(created));
        when(pluginService.getSchema("t")).thenReturn(Maybe.just("{\"type\":\"object\"}"));

        proxy.create("domain", newEngine, principal).test();

        ArgumentCaptor<AuthorizationEngineAuditBuilder> captor = ArgumentCaptor.forClass(AuthorizationEngineAuditBuilder.class);
        verify(auditService, times(1)).report(captor.capture());
        
        var audit = captor.getValue().build(objectMapper);
        assertEquals(EventType.AUTHORIZATION_ENGINE_CREATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals("domain", audit.getReferenceId());
        assertNotNull(audit.getActor());
        assertEquals("tester", audit.getActor().getId());
        assertNotNull(audit.getTarget());
        assertEquals(createdId, audit.getTarget().getId());
        assertNotNull(audit.getOutcome().getMessage());
    }

    @Test
    void create_shouldAuditOnError() {
        var newEngine = new NewAuthorizationEngine();
        newEngine.setName("n");
        newEngine.setType("t");

        var runtimeException = new RuntimeException("boom");
        when(service.create("domain", newEngine, principal))
                .thenReturn(Single.error(runtimeException));

        TestObserver<AuthorizationEngine> obs = proxy.create("domain", newEngine, principal).test();

        obs.assertError(RuntimeException.class);

        ArgumentCaptor<AuthorizationEngineAuditBuilder> captor = ArgumentCaptor.forClass(AuthorizationEngineAuditBuilder.class);
        verify(auditService, times(1)).report(captor.capture());
        
        var audit = captor.getValue().build(objectMapper);
        assertEquals(EventType.AUTHORIZATION_ENGINE_CREATED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals("domain", audit.getReferenceId());
        assertNotNull(audit.getActor());
        assertEquals("tester", audit.getActor().getId());
        assertNotNull(audit.getOutcome().getMessage());
    }

    @Test
    void update_shouldFilterSensitiveData() {
        var id = "eng1";
        var update = new UpdateAuthorizationEngine();
        update.setName("new");
        update.setConfiguration("{\"safe\": \"value\", \"secret\": \"value\"}");

        var oldEngine = new AuthorizationEngine();
        oldEngine.setId(id);
        oldEngine.setType("t");
        oldEngine.setReferenceType(ReferenceType.DOMAIN);
        oldEngine.setReferenceId("domain");
        oldEngine.setConfiguration("{\"safe\": \"value\", \"secret\": \"value\"}");

        var updated = new AuthorizationEngine();
        updated.setId(id);
        updated.setType("t");
        updated.setConfiguration("{\"safe\": \"value\", \"secret\": \"value\"}");
        updated.setReferenceType(ReferenceType.DOMAIN);
        updated.setReferenceId("domain");

        when(service.findById(id)).thenReturn(Maybe.just(oldEngine));
        when(pluginService.getSchema("t")).thenReturn(Maybe.just(SCHEMA_WITH_SECRET));
        when(service.update("domain", id, update, principal)).thenReturn(Single.just(updated));

        TestObserver<AuthorizationEngine> obs = proxy.update("domain", id, update, principal).test();

        obs.assertComplete();
        obs.assertValue(e -> e.getId().equals(id));
        obs.assertValue(e -> e.getConfiguration().equals("{\"safe\":\"value\",\"secret\":\"********\"}"));
    }

    @Test
    void update_shouldAuditOnSuccess() {
        var id = "eng1";
        var update = new UpdateAuthorizationEngine();
        update.setName("new");
        update.setConfiguration("{}");

        var oldEngine = new AuthorizationEngine();
        oldEngine.setId(id);
        oldEngine.setType("t");
        oldEngine.setReferenceType(ReferenceType.DOMAIN);
        oldEngine.setReferenceId("domain");
        oldEngine.setConfiguration("{}");
        oldEngine.setName("old");

        var updated = new AuthorizationEngine();
        updated.setId(id);
        updated.setType("t");
        updated.setConfiguration("{}");
        updated.setReferenceType(ReferenceType.DOMAIN);
        updated.setReferenceId("domain");
        updated.setName("new");

        when(service.findById(id)).thenReturn(Maybe.just(oldEngine));
        when(pluginService.getSchema("t")).thenReturn(Maybe.just("{\"type\":\"object\"}"));
        when(service.update("domain", id, update, principal)).thenReturn(Single.just(updated));

        proxy.update("domain", id, update, principal).test();

        ArgumentCaptor<AuthorizationEngineAuditBuilder> captor = ArgumentCaptor.forClass(AuthorizationEngineAuditBuilder.class);
        verify(auditService, times(1)).report(captor.capture());
        
        var audit = captor.getValue().build(objectMapper);
        assertEquals(EventType.AUTHORIZATION_ENGINE_UPDATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals("domain", audit.getReferenceId());
        assertNotNull(audit.getActor());
        assertEquals("tester", audit.getActor().getId());
        assertNotNull(audit.getTarget());
        assertEquals(id, audit.getTarget().getId());
        assertNotNull(audit.getOutcome().getMessage());
    }

    @Test
    void update_shouldFail_whenNotFound() {
        var id = "missing";
        var update = new UpdateAuthorizationEngine();
        update.setConfiguration("{}");

        when(service.findById(id)).thenReturn(Maybe.empty());

        TestObserver<AuthorizationEngine> obs = proxy.update("domain", id, update, principal).test();
        obs.assertError(AuthorizationEngineNotFoundException.class);

        verify(auditService, never()).report(any());
    }

    @Test
    void update_shouldAuditOnError_whenSchemaMissing() {
        var id = "eng2";
        var update = new UpdateAuthorizationEngine();
        update.setConfiguration("{}");

        var oldEngine = new AuthorizationEngine();
        oldEngine.setId(id);
        oldEngine.setType("t");
        oldEngine.setReferenceType(ReferenceType.DOMAIN);
        oldEngine.setReferenceId("domain");
        oldEngine.setConfiguration("{}");

        when(service.findById(id)).thenReturn(Maybe.just(oldEngine));
        // filterSensitiveData path: no schema for old engine -> cleared config
        when(pluginService.getSchema("t")).thenReturn(Maybe.empty());

        TestObserver<AuthorizationEngine> obs = proxy.update("domain", id, update, principal).test();
        obs.assertError(AuthorizationEnginePluginSchemaNotFoundException.class);

        ArgumentCaptor<AuthorizationEngineAuditBuilder> captor = ArgumentCaptor.forClass(AuthorizationEngineAuditBuilder.class);
        verify(auditService, times(1)).report(captor.capture());
        
        var audit = captor.getValue().build(objectMapper);
        assertEquals(EventType.AUTHORIZATION_ENGINE_UPDATED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals("domain", audit.getReferenceId());
        assertNotNull(audit.getActor());
        assertEquals("tester", audit.getActor().getId());
        assertNotNull(audit.getTarget());
        assertEquals(id, audit.getTarget().getId());
        assertNotNull(audit.getOutcome().getMessage());
    }

    @Test
    void delete_shouldAuditOnComplete() {
        var id = "del1";
        var engine = new AuthorizationEngine();
        engine.setId(id);
        engine.setReferenceType(ReferenceType.DOMAIN);
        engine.setReferenceId("domain");
        engine.setName("test-engine");

        when(service.findById(id)).thenReturn(Maybe.just(engine));
        when(service.delete("domain", id, principal)).thenReturn(Completable.complete());

        TestObserver<Void> obs = proxy.delete("domain", id, principal).test();

        obs.assertComplete();
        
        ArgumentCaptor<AuthorizationEngineAuditBuilder> captor = ArgumentCaptor.forClass(AuthorizationEngineAuditBuilder.class);
        verify(auditService, times(1)).report(captor.capture());
        
        var audit = captor.getValue().build(objectMapper);
        assertEquals(EventType.AUTHORIZATION_ENGINE_DELETED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals("domain", audit.getReferenceId());
        assertNotNull(audit.getActor());
        assertEquals("tester", audit.getActor().getId());
        assertNotNull(audit.getTarget());
        assertEquals(id, audit.getTarget().getId());
    }

    @Test
    void delete_shouldThrow_whenNotFound_andNotAudit() {
        var id = "missing";
        when(service.findById(id)).thenReturn(Maybe.empty());

        TestObserver<Void> obs = proxy.delete("domain", id, principal).test();

        obs.assertError(AuthorizationEngineNotFoundException.class);
        verify(auditService, never()).report(any());
    }
}
