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
package io.gravitee.am.service;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.FlowRepository;
import io.gravitee.am.service.exception.FlowNotFoundException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.FlowServiceImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class FlowServiceTest {

    @InjectMocks
    private FlowService flowService = new FlowServiceImpl();

    @Mock
    private EventService eventService;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindAll_flowDoesNotHaveType() {
        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(new Flow()));
        TestSubscriber<Flow> testObserver = flowService.findAll(ReferenceType.DOMAIN, DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(12);
    }

    @Test
    public void shouldFindAll_returnAllWhenEmpty() {
        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.empty());
        TestSubscriber<Flow> testObserver = flowService.findAll(ReferenceType.DOMAIN, DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(12);
    }

    @Test
    public void shouldFindAll_returnWhenFlowIsExisting() {
        var flow = new Flow();
        flow.setType(Type.ROOT);
        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(flow));
        TestSubscriber<Flow> testObserver = flowService.findAll(ReferenceType.DOMAIN, DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(12);
    }

    @Test
    public void shouldFindByApp_returnWhenFlowIsExisting_WithMultipleFlow() {
        var flowApp = new Flow();
        flowApp.setType(Type.ROOT);
        flowApp.setOrder(0);
        flowApp.setApplication("appid");

        var flowApp2 = new Flow();
        flowApp2.setType(Type.ROOT);
        flowApp2.setOrder(0);
        flowApp2.setApplication("appid");

        when(flowRepository.findByApplication(ReferenceType.DOMAIN, DOMAIN, "appid")).thenReturn(Flowable.just(flowApp, flowApp2));
        TestSubscriber<Flow> testObserver = flowService.findByApplication(ReferenceType.DOMAIN, DOMAIN, "appid").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(13);
    }

    @Test
    public void shouldFindByApp_returnAllWhenEmpty() {
        when(flowRepository.findByApplication(ReferenceType.DOMAIN, DOMAIN, "appid")).thenReturn(Flowable.empty());
        TestSubscriber<Flow> testObserver = flowService.findByApplication(ReferenceType.DOMAIN, DOMAIN, "appid").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(12);
    }

    @Test
    public void shouldFindAll_returnWhenFlowIsExisting_WithAppFlow() {
        var flow = new Flow();
        flow.setType(Type.ROOT);
        flow.setOrder(0);

        var flowApp = new Flow();
        flowApp.setType(Type.ROOT);
        flowApp.setOrder(0);
        flowApp.setApplication("appid");

        var flowApp2 = new Flow();
        flowApp2.setType(Type.ROOT);
        flowApp2.setOrder(0);
        flowApp2.setApplication("appid");

        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(flow, flowApp, flowApp2));
        TestSubscriber<Flow> testObserver = flowService.findAll(ReferenceType.DOMAIN, DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(14);
    }

    @Test
    public void shouldFindAll_returnWhenFlowIsExisting_WithAppFlowFiltered() {
        var flow = new Flow();
        flow.setType(Type.ROOT);
        flow.setOrder(0);

        var flow2 = new Flow();
        flow2.setType(Type.ROOT);
        flow2.setOrder(0);

        var flowApp = new Flow();
        flowApp.setType(Type.ROOT);
        flowApp.setOrder(0);
        flowApp.setApplication("appid");

        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(flow, flow2, flowApp));
        TestSubscriber<Flow> testObserver = flowService.findAll(ReferenceType.DOMAIN, DOMAIN, true).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(13);
    }

    @Test
    public void shouldNotFindAll_technicalException() {
        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        flowService.findAll(ReferenceType.DOMAIN, DOMAIN).toList().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        Flow newFlow = new Flow();
        newFlow.setReferenceType(ReferenceType.DOMAIN);
        newFlow.setReferenceId(DOMAIN);
        when(flowRepository.create(any(Flow.class))).thenReturn(Single.just(newFlow));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.create(ReferenceType.DOMAIN, DOMAIN, newFlow).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).create(any(Flow.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldCreate_technicalException() {
        Flow newFlow = mock(Flow.class);
        when(flowRepository.create(any(Flow.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = flowService.create(ReferenceType.DOMAIN, DOMAIN, newFlow).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldUpdate() {
        Flow updateFlow = new Flow();
        updateFlow.setType(Type.ROOT);
        updateFlow.setReferenceType(ReferenceType.DOMAIN);
        updateFlow.setReferenceId(DOMAIN);
        Flow existingFlow = new Flow();
        existingFlow.setType(Type.ROOT);
        existingFlow.setReferenceType(ReferenceType.DOMAIN);
        existingFlow.setReferenceId(DOMAIN);
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-flow")).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.update(any(Flow.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.update(ReferenceType.DOMAIN, DOMAIN, "my-flow", updateFlow).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).findById(ReferenceType.DOMAIN, DOMAIN, "my-flow");
        verify(flowRepository, times(1)).update(any(Flow.class));
        verify(eventService, times(1)).create(any());
    }


    @Test
    public void shouldNotUpdate_TypeChange() {
        Flow updateFlow = Mockito.mock(Flow.class);
        when(updateFlow.getType()).thenReturn(Type.ROOT);
        Flow existingFlow = new Flow();
        existingFlow.setType(Type.LOGIN);
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-flow")).thenReturn(Maybe.just(existingFlow));

        TestObserver testObserver = flowService.update(ReferenceType.DOMAIN, DOMAIN, "my-flow", updateFlow).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidParameterException.class);

        verify(flowRepository, times(1)).findById(ReferenceType.DOMAIN, DOMAIN, "my-flow");
        verify(flowRepository, never()).update(any(Flow.class));
    }

    @Test
    public void shouldResetPostStepsWhenUpdateRoot() {
        final String ID = "ROOT";

        Flow existingFlow = new Flow();
        existingFlow.setPre(emptyList());
        existingFlow.setPost(emptyList());
        existingFlow.setType(Type.ROOT);
        existingFlow.setReferenceType(ReferenceType.DOMAIN);
        existingFlow.setReferenceId(DOMAIN);

        Flow updateFlow = new Flow();
        updateFlow.setPre(Arrays.asList(new Step()));
        updateFlow.setPost(Arrays.asList(new Step()));
        updateFlow.setType(Type.ROOT);
        updateFlow.setReferenceType(ReferenceType.DOMAIN);
        updateFlow.setReferenceId(DOMAIN);

        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, ID)).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.update(any(Flow.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any())).thenAnswer(a -> Single.just(a.getArgument(0)));

        TestObserver testObserver = flowService.update(ReferenceType.DOMAIN, DOMAIN, ID, updateFlow).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).findById(ReferenceType.DOMAIN, DOMAIN, ID);
        verify(flowRepository, times(1)).update(argThat(flow -> flow.getPost().isEmpty() && !flow.getPre().isEmpty()));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldUpdate_technicalException() {
        Flow updateFlow = Mockito.mock(Flow.class);
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-flow")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        flowService.update(ReferenceType.DOMAIN, DOMAIN, "my-flow", updateFlow).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(flowRepository, never()).update(any(Flow.class));
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldUpdate_flowNotFound() {
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-flow")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        flowService.update(ReferenceType.DOMAIN, DOMAIN, "my-flow", new Flow()).subscribe(testObserver);

        testObserver.assertError(FlowNotFoundException.class);
        testObserver.assertNotComplete();

        verify(flowRepository, never()).update(any(Flow.class));
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldDelete_flowNotFound() {
        when(flowRepository.findById("my-flow")).thenReturn(Maybe.empty());

        TestObserver testObserver = flowService.delete("my-flow").test();

        testObserver.assertError(FlowNotFoundException.class);
        testObserver.assertNotComplete();

        verify(flowRepository, never()).delete(anyString());
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldDelete_technicalException() {
        when(flowRepository.findById("my-flow")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = flowService.delete("my-flow").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(flowRepository, never()).delete(anyString());
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldDelete() {
        Flow flow = new Flow();
        flow.setReferenceId(DOMAIN);
        flow.setReferenceType(ReferenceType.DOMAIN);
        when(flowRepository.findById("my-flow")).thenReturn(Maybe.just(flow));
        when(flowRepository.delete("my-flow")).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.delete("my-flow").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).delete("my-flow");
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldCreateAllFlows() {
        Flow newFlow = new Flow();
        newFlow.setOrder(0);
        Flow newFlow2 = new Flow();
        newFlow2.setOrder(0);

        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.empty());
        when(flowRepository.create(any(Flow.class))).thenReturn(Single.just(newFlow), Single.just(newFlow2));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.createOrUpdate(ReferenceType.DOMAIN, DOMAIN, Arrays.asList(newFlow, newFlow2)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(2)).create(any(Flow.class));
        verify(flowRepository, never()).update(any(Flow.class));
        verify(eventService, times(2)).create(any());
    }

    @Test
    public void shouldUpdateAllFlows() {
        Flow updateFlow = new Flow();
        updateFlow.setType(Type.ROOT);
        updateFlow.setId("flow1");
        updateFlow.setOrder(0);
        updateFlow.setReferenceId(DOMAIN);
        updateFlow.setReferenceType(ReferenceType.DOMAIN);
        Flow existingFlow = new Flow();
        existingFlow.setId("flow1");
        existingFlow.setType(Type.ROOT);
        existingFlow.setOrder(0);
        existingFlow.setReferenceId(DOMAIN);
        existingFlow.setReferenceType(ReferenceType.DOMAIN);
        Flow updateFlow2 = new Flow();
        updateFlow2.setType(Type.ROOT);
        updateFlow2.setId("flow2");
        updateFlow2.setOrder(0);
        updateFlow2.setReferenceId(DOMAIN);
        updateFlow2.setReferenceType(ReferenceType.DOMAIN);
        Flow existingFlow2 = new Flow();
        existingFlow2.setId("flow2");
        existingFlow2.setType(Type.ROOT);
        existingFlow2.setOrder(0);
        existingFlow2.setReferenceId(DOMAIN);
        existingFlow2.setReferenceType(ReferenceType.DOMAIN);

        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(existingFlow, existingFlow2));
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, updateFlow.getId())).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, updateFlow2.getId())).thenReturn(Maybe.just(existingFlow2));
        when(flowRepository.update(any(Flow.class))).thenReturn(Single.just(updateFlow), Single.just(updateFlow2));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.createOrUpdate(ReferenceType.DOMAIN, DOMAIN, Arrays.asList(updateFlow, updateFlow2)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(2)).update(any(Flow.class));
        verify(flowRepository, never()).create(any(Flow.class));
        verify(eventService, times(2)).create(any());
    }

    @Test
    public void shouldUpdateOneAndCreateOne() {
        Flow updateFlow = new Flow();
        updateFlow.setType(Type.ROOT);
        updateFlow.setId("flow1");
        updateFlow.setOrder(0);
        updateFlow.setReferenceId(DOMAIN);
        updateFlow.setReferenceType(ReferenceType.DOMAIN);

        Flow existingFlow = new Flow();
        existingFlow.setId("flow1");
        existingFlow.setType(Type.ROOT);
        existingFlow.setOrder(0);
        existingFlow.setReferenceId(DOMAIN);
        existingFlow.setReferenceType(ReferenceType.DOMAIN);

        Flow updateFlow2 = new Flow();
        updateFlow2.setType(Type.ROOT);
        updateFlow2.setOrder(0);
        updateFlow2.setReferenceId(DOMAIN);
        updateFlow2.setReferenceType(ReferenceType.DOMAIN);

        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(existingFlow));
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, updateFlow.getId())).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.update(any(Flow.class))).thenReturn(Single.just(updateFlow2));
        when(flowRepository.create(any(Flow.class))).thenReturn(Single.just(updateFlow));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.createOrUpdate(ReferenceType.DOMAIN, DOMAIN, Arrays.asList(updateFlow, updateFlow2)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).update(any(Flow.class));
        verify(flowRepository, times(1)).create(any(Flow.class));
        verify(eventService, times(2)).create(any());
    }

    @Test
    public void shouldNotUpdateAllFlows_TypeMismatch() {
        Flow updateFlow = new Flow();
        updateFlow.setType(Type.LOGIN);
        updateFlow.setId("flow1");
        Flow existingFlow = new Flow();
        existingFlow.setId("flow1");
        existingFlow.setType(Type.ROOT);

        Flow updateFlow2 = new Flow();
        updateFlow2.setType(Type.ROOT);
        updateFlow2.setId("flow2");
        Flow existingFlow2 = new Flow();
        existingFlow2.setId("flow2");
        existingFlow2.setType(Type.ROOT);

        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(existingFlow, existingFlow2));

        TestObserver testObserver = flowService.createOrUpdate(ReferenceType.DOMAIN, DOMAIN, Arrays.asList(updateFlow, updateFlow2)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidParameterException.class);

        verify(flowRepository, never()).update(any(Flow.class));
        verify(flowRepository, never()).create(any(Flow.class));
    }

    @Test
    public void shouldNotUpdateAllFlows_SameId() {
        Flow updateFlow = new Flow();
        updateFlow.setType(Type.LOGIN);
        updateFlow.setId("flow1");

        TestObserver testObserver = flowService.createOrUpdate(ReferenceType.DOMAIN, DOMAIN, Arrays.asList(updateFlow, updateFlow)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidParameterException.class);

        verify(flowRepository, never()).update(any(Flow.class));
        verify(flowRepository, never()).create(any(Flow.class));
    }


    @Test
    public void shouldUpdateAllFlows_WithDelete() {
        Flow existingFlow = new Flow();
        existingFlow.setId("flow1");
        existingFlow.setType(Type.ROOT);
        existingFlow.setReferenceId(DOMAIN);
        existingFlow.setReferenceType(ReferenceType.DOMAIN);
        Flow updateFlow2 = new Flow();
        updateFlow2.setType(Type.ROOT);
        updateFlow2.setId("flow2");
        updateFlow2.setReferenceId(DOMAIN);
        updateFlow2.setReferenceType(ReferenceType.DOMAIN);
        Flow existingFlow2 = new Flow();
        existingFlow2.setId("flow2");
        existingFlow2.setType(Type.ROOT);
        existingFlow2.setReferenceId(DOMAIN);
        existingFlow2.setReferenceType(ReferenceType.DOMAIN);

        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(existingFlow, existingFlow2));
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, existingFlow2.getId())).thenReturn(Maybe.just(existingFlow2));
        when(flowRepository.findById(existingFlow.getId())).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.update(any(Flow.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(flowRepository.delete(any())).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.createOrUpdate(ReferenceType.DOMAIN, DOMAIN, Arrays.asList(updateFlow2)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).update(any(Flow.class));
        verify(flowRepository, times(1)).delete(eq(existingFlow.getId()));
        verify(flowRepository, never()).create(any(Flow.class));
        verify(eventService, times(2)).create(any());
    }

    @Test
    public void shouldCopyFlow() {
        String clientSourceId = UUID.randomUUID().toString();
        String clientTargetId = UUID.randomUUID().toString();

        Flow existingFlow = buildFlow();
        existingFlow.setApplication(clientSourceId);
        Date existingCreatedAt = existingFlow.getCreatedAt();
        Date existingUpdatedAt = existingFlow.getUpdatedAt();

        when(flowRepository.findByApplication(ReferenceType.DOMAIN, DOMAIN, clientSourceId)).thenReturn(Flowable.just(existingFlow));
        when(flowRepository.create(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<List<Flow>> observer = flowService.copyFromClient(DOMAIN, clientSourceId, clientTargetId).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValueCount(1);

        List<Flow> flows = observer.values().getFirst();
        assertNotNull("Flows list should not be null", flows);
        assertFalse("Flows list should not be empty", flows.isEmpty());
        
        Optional<Flow> copiedFlow = flows.stream()
                .filter(f -> f.getType() == Type.REGISTER)
                .findFirst();
        
        assertTrue("Should find a flow with type REGISTER", copiedFlow.isPresent());
        
        Flow flow = copiedFlow.get();
        assertNotNull(flow.getId());
        assertNotEquals(existingFlow.getId(), flow.getId());
        assertEquals(clientTargetId, flow.getApplication());
        assertEquals(DOMAIN, flow.getReferenceId());
        assertEquals(existingFlow.getOrder(), flow.getOrder());
        
        // Dates should be different (or at least createdAt should be >= existing, and updatedAt should be >= createdAt)
        assertNotNull(flow.getCreatedAt());
        assertNotNull(flow.getUpdatedAt());
        assertTrue(flow.getCreatedAt().compareTo(existingCreatedAt) >= 0);
        assertTrue(flow.getUpdatedAt().compareTo(existingUpdatedAt) >= 0);

        // Assert post steps
        assertNotNull(flow.getPost());
        assertEquals(existingFlow.getPost().size(), flow.getPost().size());
        assertFalse(flow.getPost().isEmpty());
        assertEquals(existingFlow.getPost().getFirst().getName(), flow.getPost().getFirst().getName());

        // Assert pre steps
        assertNotNull(flow.getPre());
        assertEquals(existingFlow.getPre().size(), flow.getPre().size());
        assertFalse(flow.getPre().isEmpty());
        assertEquals(existingFlow.getPre().getFirst().getName(), flow.getPre().getFirst().getName());

        // the find method return all instance of Flows
        verify(flowRepository, atLeast(1)).create(argThat(createdFlow -> !createdFlow.getId().equals(existingFlow.getId())
                && createdFlow.getApplication().equals(clientTargetId)
                && createdFlow.getReferenceId().equals(DOMAIN)));
        verify(eventService, atLeast(1)).create(any());
    }

    private Flow buildFlow() {
        String rand = UUID.randomUUID().toString();
        Flow flow = new Flow();
        flow.setName("ROOT" + rand);
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(new Date());
        flow.setCondition("condition" + rand);
        flow.setEnabled(true);
        flow.setOrder(5);
        flow.setReferenceId(DOMAIN);
        flow.setReferenceType(ReferenceType.DOMAIN);
        flow.setType(Type.REGISTER);

        List<Step> steps = new ArrayList<>();
        Step step = new Step();
        step.setName(UUID.randomUUID().toString());
        step.setEnabled(true);
        step.setConfiguration(UUID.randomUUID().toString());
        step.setPolicy(UUID.randomUUID().toString());
        step.setDescription(UUID.randomUUID().toString());
        step.setCondition(UUID.randomUUID().toString());
        steps.add(step);
        flow.setPre(steps);

        steps = new ArrayList<>();
        step = new Step();
        step.setName(UUID.randomUUID().toString());
        step.setEnabled(true);
        step.setConfiguration(UUID.randomUUID().toString());
        step.setPolicy(UUID.randomUUID().toString());
        step.setDescription(UUID.randomUUID().toString());
        step.setCondition(UUID.randomUUID().toString());
        steps.add(step);
        flow.setPost(steps);

        return flow;
    }
}
