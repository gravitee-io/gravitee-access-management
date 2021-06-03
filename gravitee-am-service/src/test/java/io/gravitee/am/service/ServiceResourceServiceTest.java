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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ServiceResourceRepository;
import io.gravitee.am.service.exception.ServiceResourceCurrentlyUsedException;
import io.gravitee.am.service.exception.ServiceResourceNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.ServiceResourceServiceImpl;
import io.gravitee.am.service.model.NewServiceResource;
import io.gravitee.am.service.model.UpdateServiceResource;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ServiceResourceServiceTest {

    @InjectMocks
    private ServiceResourceService resourceService = new ServiceResourceServiceImpl();

    @Mock
    private EventService eventService;

    @Mock
    private FactorService factorService;

    @Mock
    private ServiceResourceRepository resourceRepository;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(resourceRepository.findById("my-resource")).thenReturn(Maybe.just(new ServiceResource()));
        TestObserver testObserver = resourceService.findById("my-resource").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_NotExist() {
        when(resourceRepository.findById("my-resource")).thenReturn(Maybe.empty());
        TestObserver testObserver = resourceService.findById("my-resource").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindByDomain() {
        when(resourceRepository.findByReference(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(new ServiceResource()));
        TestSubscriber<ServiceResource> testObserver = resourceService.findByDomain(DOMAIN).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldCreate() {
        NewServiceResource resource = new NewServiceResource();
        resource.setConfiguration("{}");
        resource.setName("myresource");
        resource.setType("rtype");

        ServiceResource record = new ServiceResource();
        record.setName(resource.getName());
        record.setType(resource.getType());
        record.setConfiguration(resource.getConfiguration());
        record.setReferenceId(DOMAIN);
        record.setReferenceType(ReferenceType.DOMAIN);
        record.setCreatedAt(new Date());
        record.setUpdatedAt(new Date());

        when(resourceRepository.create(argThat(bean -> bean.getName().equals(resource.getName()))))
                .thenReturn(Single.just(record));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<ServiceResource> testObserver = resourceService.create(DOMAIN, resource, null).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);

        verify(auditService).report(argThat(auditBuilder -> auditBuilder.build(new ObjectMapper()).getOutcome().getStatus().equals("SUCCESS")));
    }

    @Test
    public void shouldCreate_AuditFailureOnError() {
        NewServiceResource resource = new NewServiceResource();
        resource.setConfiguration("{}");
        resource.setName("myresource");
        resource.setType("rtype");

        when(resourceRepository.create(argThat(bean -> bean.getName().equals(resource.getName()))))
                .thenReturn(Single.error(new TechnicalException()));

        TestObserver<ServiceResource> testObserver = resourceService.create(DOMAIN, resource, null).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNoValues();

        verify(auditService).report(argThat(auditBuilder -> auditBuilder.build(new ObjectMapper()).getOutcome().getStatus().equals("FAILURE")));
    }

    @Test
    public void shouldUpdate() {
        UpdateServiceResource resource = new UpdateServiceResource();
        resource.setConfiguration("{}");
        resource.setName("myresource");

        ServiceResource record = new ServiceResource();
        record.setId("resid");
        record.setName(resource.getName());
        record.setType("rtype");
        record.setConfiguration(resource.getConfiguration());
        record.setReferenceId(DOMAIN);
        record.setReferenceType(ReferenceType.DOMAIN);
        record.setCreatedAt(new Date());
        record.setUpdatedAt(new Date());

        when(resourceRepository.findById(record.getId())).thenReturn(Maybe.just(record));
        when(resourceRepository.update(argThat(bean -> bean.getId().equals(record.getId())))).thenReturn(Single.just(record));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<ServiceResource> testObserver = resourceService.update(DOMAIN, record.getId(), resource, null).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);

        verify(auditService).report(argThat(auditBuilder -> auditBuilder.build(new ObjectMapper()).getOutcome().getStatus().equals("SUCCESS")));
    }

    @Test
    public void shouldUpdate_returnNotFound() {
        when(resourceRepository.findById(any())).thenReturn(Maybe.empty());

        TestObserver<ServiceResource> testObserver = resourceService.update(DOMAIN, UUID.randomUUID().toString(), new UpdateServiceResource(), null).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(ServiceResourceNotFoundException.class);

        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any());
        verify(resourceRepository, never()).update(any());
    }

    @Test
    public void shouldDelete_returnNotFound() {
        when(resourceRepository.findById(any())).thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = resourceService.delete(DOMAIN, UUID.randomUUID().toString(), null).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(ServiceResourceNotFoundException.class);

        verify(auditService, never()).report(any());
        verify(resourceRepository, never()).delete(any());
    }

    @Test
    public void shouldDelete() {
        ServiceResource record = new ServiceResource();
        record.setId("resid");
        record.setReferenceId(DOMAIN);
        record.setReferenceType(ReferenceType.DOMAIN);
        record.setCreatedAt(new Date());
        record.setUpdatedAt(new Date());
        
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(resourceRepository.findById(record.getId())).thenReturn(Maybe.just(record));
        when(resourceRepository.delete(record.getId())).thenReturn(Completable.complete());
        when(factorService.findByDomain(DOMAIN)).thenReturn(Flowable.empty());

        TestObserver<Void> testObserver = resourceService.delete(DOMAIN, record.getId(), null).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();

        verify(auditService).report(argThat(auditBuilder -> auditBuilder.build(new ObjectMapper()).getOutcome().getStatus().equals("SUCCESS")));
    }

    @Test
    public void shouldNotDelete_CurrentlyUsed() {
        ServiceResource record = new ServiceResource();
        record.setId("resid");
        record.setReferenceId(DOMAIN);
        record.setReferenceType(ReferenceType.DOMAIN);
        record.setCreatedAt(new Date());
        record.setUpdatedAt(new Date());

        when(resourceRepository.findById(record.getId())).thenReturn(Maybe.just(record));
        Factor factor = new Factor();
        factor.setName("Factor");
        factor.setConfiguration("{\"ref\": \"" + record.getId() + "\"}");
        when(factorService.findByDomain(DOMAIN)).thenReturn(Flowable.just(factor));

        TestObserver<Void> testObserver = resourceService.delete(DOMAIN, record.getId(), null).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(ServiceResourceCurrentlyUsedException.class);

        verify(auditService, never()).report(any());
        verify(resourceRepository, never()).delete(any());
    }
}
