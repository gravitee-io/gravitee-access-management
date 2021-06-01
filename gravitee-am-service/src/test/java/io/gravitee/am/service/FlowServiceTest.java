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
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.*;

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
    public void shouldFindAll() {
        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(new Flow()));
        TestObserver testObserver = flowService.findAll(ReferenceType.DOMAIN, DOMAIN).toList().test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
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
        Flow newFlow = mock(Flow.class);
        when(flowRepository.create(any(Flow.class))).thenReturn(Single.just(new Flow()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.create(ReferenceType.DOMAIN, DOMAIN, newFlow).test();
        testObserver.awaitTerminalEvent();

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
        testObserver.awaitTerminalEvent();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldUpdate() {
        Flow updateFlow = Mockito.mock(Flow.class);
        when(updateFlow.getType()).thenReturn(Type.ROOT);
        Flow existingFlow = new Flow();
        existingFlow.setType(Type.ROOT);
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-flow")).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.update(any(Flow.class))).thenReturn(Single.just(new Flow()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.update(ReferenceType.DOMAIN, DOMAIN, "my-flow", updateFlow).test();
        testObserver.awaitTerminalEvent();

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
        testObserver.awaitTerminalEvent();
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

        Flow updateFlow = new Flow();
        updateFlow.setPre(Arrays.asList(new Step()));
        updateFlow.setPost(Arrays.asList(new Step()));
        updateFlow.setType(Type.ROOT);

        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, ID)).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.update(any(Flow.class))).thenReturn(Single.just(new Flow()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.update(ReferenceType.DOMAIN, DOMAIN, ID, updateFlow).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).findById(ReferenceType.DOMAIN, DOMAIN, ID);
        verify(flowRepository, times(1)).update(argThat( flow -> flow.getPost().isEmpty() && !flow.getPre().isEmpty()));
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
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN,"my-flow")).thenReturn(Maybe.empty());

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
        when(flowRepository.findById("my-flow")).thenReturn(Maybe.just(new Flow()));
        when(flowRepository.delete("my-flow")).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.delete("my-flow").test();
        testObserver.awaitTerminalEvent();

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
        testObserver.awaitTerminalEvent();

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
        Flow existingFlow = new Flow();
        existingFlow.setId("flow1");
        existingFlow.setType(Type.ROOT);
        existingFlow.setOrder(0);

        Flow updateFlow2 = new Flow();
        updateFlow2.setType(Type.ROOT);
        updateFlow2.setId("flow2");
        updateFlow2.setOrder(0);
        Flow existingFlow2 = new Flow();
        existingFlow2.setId("flow2");
        existingFlow2.setType(Type.ROOT);
        existingFlow2.setOrder(0);

        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(existingFlow, existingFlow2));
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, updateFlow.getId())).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, updateFlow2.getId())).thenReturn(Maybe.just(existingFlow2));
        when(flowRepository.update(any(Flow.class))).thenReturn(Single.just(updateFlow), Single.just(updateFlow2));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.createOrUpdate(ReferenceType.DOMAIN, DOMAIN, Arrays.asList(updateFlow, updateFlow2)).test();
        testObserver.awaitTerminalEvent();

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
        Flow existingFlow = new Flow();
        existingFlow.setId("flow1");
        existingFlow.setType(Type.ROOT);
        existingFlow.setOrder(0);

        Flow updateFlow2 = new Flow();
        updateFlow2.setType(Type.ROOT);
        updateFlow2.setOrder(0);

        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(existingFlow));
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, updateFlow.getId())).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.update(any(Flow.class))).thenReturn(Single.just(updateFlow2));
        when(flowRepository.create(any(Flow.class))).thenReturn(Single.just(updateFlow));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.createOrUpdate(ReferenceType.DOMAIN, DOMAIN, Arrays.asList(updateFlow, updateFlow2)).test();
        testObserver.awaitTerminalEvent();

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
        testObserver.awaitTerminalEvent();

        testObserver.assertError(InvalidParameterException.class);

        verify(flowRepository, never()).update(any(Flow.class));
        verify(flowRepository, never()).create(any(Flow.class));
    }


    @Test
    public void shouldUpdateAllFlows_WithDelete() {
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
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, existingFlow2.getId())).thenReturn(Maybe.just(existingFlow2));
        when(flowRepository.findById(existingFlow.getId())).thenReturn(Maybe.just(existingFlow));
        when(flowRepository.update(any(Flow.class))).thenReturn(Single.just(new Flow()));
        when(flowRepository.delete(any())).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.createOrUpdate(ReferenceType.DOMAIN, DOMAIN, Arrays.asList(updateFlow2)).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).update(any(Flow.class));
        verify(flowRepository, times(1)).delete(eq(existingFlow.getId()));
        verify(flowRepository, never()).create(any(Flow.class));
        verify(eventService, times(2)).create(any());
    }

}
