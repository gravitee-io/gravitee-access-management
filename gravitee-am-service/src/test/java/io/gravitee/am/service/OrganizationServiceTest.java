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
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.OrganizationServiceImpl;
import io.gravitee.am.service.model.NewOrganization;
import io.gravitee.am.service.model.PatchOrganization;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OrganizationServiceTest {

    public static final String ORGANIZATION_ID = "orga#1";
    public static final String USER_ID = "user#1";

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private EntrypointService entrypointService;

    @Mock
    private AuditService auditService;

    private OrganizationService cut;

    @Before
    public void before() {

        cut = new OrganizationServiceImpl(organizationRepository, roleService, entrypointService, auditService);
    }

    @Test
    public void shouldFindById() {

        Organization organization = new Organization();
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Maybe.just(organization));

        TestObserver<Organization> obs = cut.findById(ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(organization);
    }

    @Test
    public void shouldFindById_notExistingOrganization() {

        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Maybe.empty());

        TestObserver<Organization> obs = cut.findById(ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(OrganizationNotFoundException.class);
    }

    @Test
    public void shouldFindById_technicalException() {

        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<Organization> obs = cut.findById(ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalException.class);
    }

    @Test
    public void shouldCreateDefault() {

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId("DEFAULT");

        when(organizationRepository.count()).thenReturn(Single.just(0L));
        when(organizationRepository.create(argThat(organization -> organization.getId().equals(Organization.DEFAULT)))).thenReturn(Single.just(defaultOrganization));
        when(roleService.createDefaultRoles("DEFAULT")).thenReturn(Completable.complete());
        when(entrypointService.createDefaults(defaultOrganization)).thenReturn(Flowable.just(new Entrypoint()));

        TestObserver<Organization> obs = cut.createDefault().test();

        obs.awaitTerminalEvent();
        obs.assertValue(defaultOrganization);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(defaultOrganization.getId(), audit.getReferenceId());
            assertEquals("system", audit.getActor().getId());

            return true;
        }));
    }

    @Test
    public void shouldCreateDefault_OrganizationsAlreadyExists() {

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);

        when(organizationRepository.count()).thenReturn(Single.just(1L));

        TestObserver<Organization> obs = cut.createDefault().test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertNoValues();

        verify(organizationRepository, times(1)).count();
        verifyNoMoreInteractions(organizationRepository);
        verifyZeroInteractions(auditService);
    }

    @Test
    public void shouldNotCreateDefault_error() {

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId("DEFAULT");

        when(organizationRepository.count()).thenReturn(Single.just(0L));
        when(organizationRepository.create(argThat(organization -> organization.getId().equals(Organization.DEFAULT)))).thenReturn(Single.error(new TechnicalManagementException()));

        TestObserver<Organization> obs = cut.createDefault().test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalManagementException.class);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(defaultOrganization.getId(), audit.getReferenceId());
            assertEquals("system", audit.getActor().getId());
            assertEquals(Status.FAILURE, audit.getOutcome().getStatus());

            return true;
        }));
    }

    @Test
    public void shouldCreate() {

        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Maybe.empty());
        when(organizationRepository.create(argThat(organization -> organization.getId().equals(ORGANIZATION_ID)))).thenAnswer(i -> Single.just(i.getArgument(0)));
        when(roleService.createDefaultRoles(ORGANIZATION_ID)).thenReturn(Completable.complete());
        when(entrypointService.createDefaults(any(Organization.class))).thenReturn(Flowable.just(new Entrypoint()));

        NewOrganization newOrganization = new NewOrganization();
        newOrganization.setName("TestName");
        newOrganization.setDescription("TestDescription");
        newOrganization.setDomainRestrictions(Collections.singletonList("TestDomainRestriction"));
        newOrganization.setHrids(Collections.singletonList("testOrgHRID"));

        DefaultUser createdBy = new DefaultUser("test");
        createdBy.setId(USER_ID);

        TestObserver<Organization> obs = cut.createOrUpdate(ORGANIZATION_ID, newOrganization, createdBy).test();

        obs.awaitTerminalEvent();
        obs.assertValue(organization -> {
            assertEquals(ORGANIZATION_ID, organization.getId());
            assertEquals(newOrganization.getName(), organization.getName());
            assertEquals(newOrganization.getDescription(), organization.getDescription());
            assertEquals(newOrganization.getDomainRestrictions(), organization.getDomainRestrictions());
            assertEquals(newOrganization.getHrids(), organization.getHrids());

            return true;
        });

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(createdBy.getId(), audit.getActor().getId());
            assertEquals(EventType.ORGANIZATION_CREATED, audit.getType());
            assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());

            return true;
        }));
    }

    @Test
    public void shouldCreate_error() {

        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Maybe.empty());
        when(organizationRepository.create(argThat(organization -> organization.getId().equals(ORGANIZATION_ID)))).thenReturn(Single.error(new TechnicalManagementException()));

        NewOrganization newOrganization = new NewOrganization();
        newOrganization.setName("TestName");
        newOrganization.setDescription("TestDescription");
        newOrganization.setDomainRestrictions(Collections.singletonList("TestDomainRestriction"));

        DefaultUser createdBy = new DefaultUser("test");
        createdBy.setId(USER_ID);

        TestObserver<Organization> obs = cut.createOrUpdate(ORGANIZATION_ID, newOrganization, createdBy).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalManagementException.class);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(createdBy.getId(), audit.getActor().getId());
            assertEquals(EventType.ORGANIZATION_CREATED, audit.getType());
            assertEquals(Status.FAILURE, audit.getOutcome().getStatus());

            return true;
        }));
    }

    @Test
    public void shouldCreate_update() {

        Organization existingOrganization = new Organization();
        existingOrganization.setId(ORGANIZATION_ID);

        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Maybe.just(existingOrganization));
        when(organizationRepository.update(argThat(organization -> organization.getId().equals(ORGANIZATION_ID)))).thenAnswer(i -> Single.just(i.getArgument(0)));

        NewOrganization newOrganization = new NewOrganization();
        newOrganization.setName("TestName");
        newOrganization.setDescription("TestDescription");
        newOrganization.setDomainRestrictions(Collections.singletonList("TestDomainRestriction"));
        newOrganization.setHrids(Collections.singletonList("TestHridUpdate"));

        DefaultUser createdBy = new DefaultUser("test");
        createdBy.setId(USER_ID);

        TestObserver<Organization> obs = cut.createOrUpdate(ORGANIZATION_ID, newOrganization, createdBy).test();

        obs.awaitTerminalEvent();
        obs.assertValue(organization -> {
            assertEquals(ORGANIZATION_ID, organization.getId());
            assertEquals(newOrganization.getName(), organization.getName());
            assertEquals(newOrganization.getDescription(), organization.getDescription());
            assertEquals(newOrganization.getDomainRestrictions(), organization.getDomainRestrictions());
            assertEquals(newOrganization.getHrids(), organization.getHrids());

            return true;
        });

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(createdBy.getId(), audit.getActor().getId());
            assertEquals(EventType.ORGANIZATION_UPDATED, audit.getType());
            assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());

            return true;
        }));
    }

    @Test
    public void shouldCreate_updateError() {

        Organization existingOrganization = new Organization();
        existingOrganization.setId(ORGANIZATION_ID);

        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Maybe.just(existingOrganization));
        when(organizationRepository.update(argThat(organization -> organization.getId().equals(ORGANIZATION_ID)))).thenReturn(Single.error(new TechnicalManagementException()));

        NewOrganization newOrganization = new NewOrganization();
        newOrganization.setName("TestName");
        newOrganization.setDescription("TestDescription");
        newOrganization.setDomainRestrictions(Collections.singletonList("TestDomainRestriction"));

        DefaultUser createdBy = new DefaultUser("test");
        createdBy.setId(USER_ID);

        TestObserver<Organization> obs = cut.createOrUpdate(ORGANIZATION_ID, newOrganization, createdBy).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalManagementException.class);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(createdBy.getId(), audit.getActor().getId());
            assertEquals(EventType.ORGANIZATION_UPDATED, audit.getType());
            assertEquals(Status.FAILURE, audit.getOutcome().getStatus());

            return true;
        }));
    }

    @Test
    public void shouldUpdate() {

        Organization existingOrganization = new Organization();
        existingOrganization.setId(ORGANIZATION_ID);

        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Maybe.just(existingOrganization));
        when(organizationRepository.update(argThat(toUpdate -> toUpdate.getIdentities() != null))).thenAnswer(i -> Single.just(i.getArgument(0)));

        PatchOrganization patchOrganization = new PatchOrganization();
        List<String> identities = Collections.singletonList("test");
        patchOrganization.setIdentities(identities);

        TestObserver<Organization> obs = cut.update(ORGANIZATION_ID, patchOrganization, new DefaultUser("username")).test();

        obs.awaitTerminalEvent();
        obs.assertValue(updated -> updated.getIdentities().equals(identities));
    }

    @Test
    public void shouldUpdate_notExistingOrganization() {

        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Maybe.empty());

        PatchOrganization patchOrganization = new PatchOrganization();
        patchOrganization.setIdentities(Collections.singletonList("test"));

        TestObserver<Organization> obs = cut.update(ORGANIZATION_ID, patchOrganization, new DefaultUser("username")).test();

        obs.awaitTerminalEvent();
        obs.assertError(OrganizationNotFoundException.class);
    }
}
