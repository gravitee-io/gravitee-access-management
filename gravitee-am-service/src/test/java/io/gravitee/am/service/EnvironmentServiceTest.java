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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.EnvironmentServiceImpl;
import io.gravitee.am.service.model.NewEnvironment;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EnvironmentServiceTest {

    public static final String ENVIRONMENT_ID = "env#1";
    public static final String ORGANIZATION_ID = "org#1";
    public static final String USER_ID = "user#1";

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private AuditService auditService;

    private EnvironmentService cut;

    @Before
    public void before() {

        cut = new EnvironmentServiceImpl(environmentRepository, organizationService, auditService);
    }

    @Test
    public void shouldFindByIdAndOrgId() {

        Environment environment = new Environment();
        when(environmentRepository.findById(ENVIRONMENT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(environment));

        TestObserver<Environment> obs = cut.findById(ENVIRONMENT_ID, ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(environment);
    }

    @Test
    public void shouldFindByIdAndOrgId_notExistingEnvironment() {

        when(environmentRepository.findById(ENVIRONMENT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());

        TestObserver<Environment> obs = cut.findById(ENVIRONMENT_ID, ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(EnvironmentNotFoundException.class);
    }

    @Test
    public void shouldFindByIdAndOrgId_technicalException() {

        when(environmentRepository.findById(ENVIRONMENT_ID, ORGANIZATION_ID)).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<Environment> obs = cut.findById(ENVIRONMENT_ID, ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalException.class);
    }

    @Test
    public void shouldFindById() {

        Environment environment = new Environment();
        when(environmentRepository.findById(ENVIRONMENT_ID)).thenReturn(Maybe.just(environment));

        TestObserver<Environment> obs = cut.findById(ENVIRONMENT_ID).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(environment);
    }

    @Test
    public void shouldFindById_notExistingEnvironment() {

        when(environmentRepository.findById(ENVIRONMENT_ID)).thenReturn(Maybe.empty());

        TestObserver<Environment> obs = cut.findById(ENVIRONMENT_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(EnvironmentNotFoundException.class);
    }

    @Test
    public void shouldFindById_technicalException() {

        when(environmentRepository.findById(ENVIRONMENT_ID)).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<Environment> obs = cut.findById(ENVIRONMENT_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalException.class);
    }

    @Test
    public void shouldFindAll() {

        Environment environment = new Environment();
        when(environmentRepository.findAll(ORGANIZATION_ID)).thenReturn(Flowable.just(environment));

        TestSubscriber<Environment> obs = cut.findAll(ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(environment);
    }

    @Test
    public void shouldFindAll_noEnvironment() {

        when(environmentRepository.findAll(ORGANIZATION_ID)).thenReturn(Flowable.empty());

        TestSubscriber<Environment> obs = cut.findAll(ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertNoErrors();
        obs.assertComplete();
        obs.assertNoValues();
    }

    @Test
    public void shouldFindAll_TechnicalException() {

        when(environmentRepository.findAll(ORGANIZATION_ID)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber<Environment> obs = cut.findAll(ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalException.class);
    }

    @Test
    public void shouldCreateDefault() {

        Environment defaultEnvironment = new Environment();
        defaultEnvironment.setId(Environment.DEFAULT);
        defaultEnvironment.setOrganizationId(ORGANIZATION_ID);

        when(environmentRepository.count()).thenReturn(Single.just(0L));
        when(environmentRepository.create(argThat(environment -> environment.getId().equals(Environment.DEFAULT)))).thenReturn(Single.just(defaultEnvironment));

        TestObserver<Environment> obs = cut.createDefault().test();

        obs.awaitTerminalEvent();
        obs.assertValue(defaultEnvironment);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(defaultEnvironment.getOrganizationId(), audit.getReferenceId());
            assertEquals("system", audit.getActor().getId());

            return true;
        }));
    }

    @Test
    public void shouldCreateDefault_EnvironmentsAlreadyExists() {

        Environment defaultEnvironment = new Environment();
        defaultEnvironment.setId("DEFAULT");

        when(environmentRepository.count()).thenReturn(Single.just(1L));

        TestObserver<Environment> obs = cut.createDefault().test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertNoValues();

        verify(environmentRepository, times(1)).count();
        verifyNoMoreInteractions(environmentRepository);
        verifyZeroInteractions(auditService);
    }

    @Test
    public void shouldCreate() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        when(environmentRepository.findById(ENVIRONMENT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());
        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(environmentRepository.create(argThat(environment -> environment.getId().equals(ENVIRONMENT_ID)))).thenAnswer(i -> Single.just(i.getArgument(0)));

        NewEnvironment newEnvironment = new NewEnvironment();
        newEnvironment.setName("TestName");
        newEnvironment.setDescription("TestDescription");
        newEnvironment.setDomainRestrictions(Collections.singletonList("TestDomainRestriction"));
        newEnvironment.setHrids(Collections.singletonList("testEnvHRID"));

        DefaultUser createdBy = new DefaultUser("test");
        createdBy.setId(USER_ID);

        TestObserver<Environment> obs = cut.createOrUpdate(ORGANIZATION_ID, ENVIRONMENT_ID, newEnvironment, createdBy).test();

        obs.awaitTerminalEvent();
        obs.assertValue(environment -> {
            assertEquals(ORGANIZATION_ID, environment.getOrganizationId());
            assertEquals(newEnvironment.getName(), environment.getName());
            assertEquals(newEnvironment.getDescription(), environment.getDescription());
            assertEquals(newEnvironment.getDomainRestrictions(), environment.getDomainRestrictions());
            assertEquals(newEnvironment.getHrids(), environment.getHrids());

            return true;
        });

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(createdBy.getId(), audit.getActor().getId());
            assertEquals(EventType.ENVIRONMENT_CREATED, audit.getType());
            assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());

            return true;
        }));
    }

    @Test
    public void shouldCreate_error() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        when(environmentRepository.findById(ENVIRONMENT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());
        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(environmentRepository.create(argThat(environment -> environment.getId().equals(ENVIRONMENT_ID)))).thenReturn(Single.error(new TechnicalManagementException()));

        NewEnvironment newEnvironment = new NewEnvironment();
        newEnvironment.setName("TestName");
        newEnvironment.setDescription("TestDescription");
        newEnvironment.setDomainRestrictions(Collections.singletonList("TestDomainRestriction"));

        DefaultUser createdBy = new DefaultUser("test");
        createdBy.setId(USER_ID);

        TestObserver<Environment> obs = cut.createOrUpdate(ORGANIZATION_ID, ENVIRONMENT_ID, newEnvironment, createdBy).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalManagementException.class);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(createdBy.getId(), audit.getActor().getId());
            assertEquals(EventType.ENVIRONMENT_CREATED, audit.getType());
            assertEquals(Status.FAILURE, audit.getOutcome().getStatus());

            return true;
        }));
    }

    @Test
    public void shouldCreate_organizationNotFound() {

        when(environmentRepository.findById(ENVIRONMENT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());
        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.error(new OrganizationNotFoundException(ORGANIZATION_ID)));

        NewEnvironment newEnvironment = new NewEnvironment();
        newEnvironment.setName("TestName");
        newEnvironment.setDescription("TestDescription");
        newEnvironment.setDomainRestrictions(Collections.singletonList("TestDomainRestriction"));

        DefaultUser createdBy = new DefaultUser("test");
        createdBy.setId(USER_ID);

        TestObserver<Environment> obs = cut.createOrUpdate(ORGANIZATION_ID, ENVIRONMENT_ID, newEnvironment, createdBy).test();

        obs.awaitTerminalEvent();
        obs.assertError(OrganizationNotFoundException.class);

        verifyZeroInteractions(auditService);
    }

    @Test
    public void shouldCreate_update() {

        Environment existingEnvironment = new Environment();
        existingEnvironment.setId(ENVIRONMENT_ID);
        existingEnvironment.setOrganizationId(ORGANIZATION_ID);

        when(environmentRepository.findById(ENVIRONMENT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEnvironment));
        when(environmentRepository.update(argThat(environment -> environment.getId().equals(ENVIRONMENT_ID)))).thenAnswer(i -> Single.just(i.getArgument(0)));

        NewEnvironment newEnvironment = new NewEnvironment();
        newEnvironment.setName("TestName");
        newEnvironment.setDescription("TestDescription");
        newEnvironment.setDomainRestrictions(Collections.singletonList("TestDomainRestriction"));
        newEnvironment.setHrids(Collections.singletonList("testHRIDUpdated"));

        DefaultUser createdBy = new DefaultUser("test");
        createdBy.setId(USER_ID);

        TestObserver<Environment> obs = cut.createOrUpdate(ORGANIZATION_ID, ENVIRONMENT_ID, newEnvironment, createdBy).test();

        obs.awaitTerminalEvent();
        obs.assertValue(environment -> {
            assertEquals(ENVIRONMENT_ID, environment.getId());
            assertEquals(newEnvironment.getName(), environment.getName());
            assertEquals(newEnvironment.getDescription(), environment.getDescription());
            assertEquals(newEnvironment.getDomainRestrictions(), environment.getDomainRestrictions());
            assertEquals(newEnvironment.getHrids(), environment.getHrids());

            return true;
        });

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(createdBy.getId(), audit.getActor().getId());
            assertEquals(EventType.ENVIRONMENT_UPDATED, audit.getType());
            assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());

            return true;
        }));
    }

    @Test
    public void shouldCreate_updateError() {

        Environment existingEnvironment = new Environment();
        existingEnvironment.setId(ENVIRONMENT_ID);
        existingEnvironment.setOrganizationId(ORGANIZATION_ID);

        when(environmentRepository.findById(ENVIRONMENT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEnvironment));
        when(environmentRepository.update(argThat(environment -> environment.getId().equals(ENVIRONMENT_ID)))).thenReturn(Single.error(new TechnicalManagementException()));

        NewEnvironment newEnvironment = new NewEnvironment();
        newEnvironment.setName("TestName");
        newEnvironment.setDescription("TestDescription");
        newEnvironment.setDomainRestrictions(Collections.singletonList("TestDomainRestriction"));

        DefaultUser createdBy = new DefaultUser("test");
        createdBy.setId(USER_ID);

        TestObserver<Environment> obs = cut.createOrUpdate(ORGANIZATION_ID, ENVIRONMENT_ID, newEnvironment, createdBy).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalManagementException.class);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(createdBy.getId(), audit.getActor().getId());
            assertEquals(EventType.ENVIRONMENT_UPDATED, audit.getType());
            assertEquals(Status.FAILURE, audit.getOutcome().getStatus());

            return true;
        }));
    }
}
