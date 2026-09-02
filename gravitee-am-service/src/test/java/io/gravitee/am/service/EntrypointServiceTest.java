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
import io.gravitee.am.common.env.CloudProperties;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.EntrypointRepository;
import io.gravitee.am.service.exception.EntrypointNotFoundException;
import io.gravitee.am.service.exception.InvalidEntrypointException;
import io.gravitee.am.service.exception.LastDefaultEntrypointException;
import io.gravitee.am.service.impl.EntrypointServiceImpl;
import io.gravitee.am.service.model.DesiredEntrypoint;
import io.gravitee.am.service.model.NewEntrypoint;
import io.gravitee.am.service.model.UpdateEntrypoint;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EntrypointServiceTest {

    public static final String ENTRYPOINT_ID = "entrypoint#1";
    public static final String ORGANIZATION_ID = "orga#1";
    public static final String USER_ID = "user#1";

    @Mock
    private EntrypointRepository entrypointRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private AuditService auditService;

    @Mock
    private VirtualHostValidator virtualHostValidator;

    @Mock
    private EventService eventService;

    private EntrypointService cut;

    @Before
    public void before() {

        cut = newEntrypointService(new MockEnvironment());
        lenient().when(eventService.create(any())).thenAnswer(i -> Single.just(i.getArgument(0)));
    }

    private EntrypointService newEntrypointService(Environment environment) {
        return new EntrypointServiceImpl(entrypointRepository, organizationService, auditService, virtualHostValidator, eventService, "https://gravitee.io", environment);
    }

    private EntrypointService cloudModeEntrypointService() {
        MockEnvironment cloudEnvironment = new MockEnvironment();
        cloudEnvironment.setProperty("cloud.enabled", "true");
        cloudEnvironment.setProperty("installation.type", CloudProperties.INSTALLATION_TYPE_MANAGED);
        return newEntrypointService(cloudEnvironment);
    }

    @Test
    public void shouldFindById() {

        Entrypoint entrypoint = new Entrypoint();
        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(entrypoint));

        TestObserver<Entrypoint> obs = cut.findById(ENTRYPOINT_ID, ORGANIZATION_ID).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(entrypoint);
    }

    @Test
    public void shouldFindById_notExistingEntrypoint() {

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());

        TestObserver<Entrypoint> obs = cut.findById(ENTRYPOINT_ID, ORGANIZATION_ID).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(EntrypointNotFoundException.class);
    }

    @Test
    public void shouldFindById_technicalException() {

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<Entrypoint> obs = cut.findById(ENTRYPOINT_ID, ORGANIZATION_ID).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(TechnicalException.class);
    }

    @Test
    public void shouldFindByEnvironment() {

        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setOrganizationId(ORGANIZATION_ID);
        entrypoint.setEnvironmentId("env#1");
        when(entrypointRepository.findByEnvironment(ORGANIZATION_ID, "env#1")).thenReturn(Flowable.just(entrypoint));

        TestSubscriber<Entrypoint> obs = cut.findByEnvironment(ORGANIZATION_ID, "env#1").test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(entrypoint);
    }

    @Test
    public void shouldCreateWithEnvironmentId() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        NewEntrypoint newEntrypoint = new NewEntrypoint();
        newEntrypoint.setName("name");
        newEntrypoint.setDescription("description");
        newEntrypoint.setTags(Arrays.asList("tag#1", "tags#2"));
        newEntrypoint.setUrl("https://auth.gravitee.io");
        newEntrypoint.setEnvironmentId("env#1");

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(entrypointRepository.create(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("auth.gravitee.io", null);

        TestObserver<Entrypoint> obs = cut.create(ORGANIZATION_ID, newEntrypoint, null).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(entrypoint -> entrypoint.getId() != null
                && entrypoint.getOrganizationId().equals(ORGANIZATION_ID)
                && "env#1".equals(entrypoint.getEnvironmentId()));

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(EventType.ENTRYPOINT_CREATED, audit.getType());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals("system", audit.getActor().getId());

            return true;
        }));
    }

    private NewEntrypoint newEntrypointFor(String host) {
        NewEntrypoint newEntrypoint = new NewEntrypoint();
        newEntrypoint.setName(host);
        newEntrypoint.setTags(Collections.emptyList());
        newEntrypoint.setUrl("https://" + host);
        newEntrypoint.setEnvironmentId("env#1");

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);
        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(entrypointRepository.create(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain(host, null);

        return newEntrypoint;
    }

    @Test
    public void shouldCreateDefaultEntrypointWhenFlagged() {

        NewEntrypoint newEntrypoint = newEntrypointFor("env-acme.gravitee.io");

        TestObserver<Entrypoint> obs = cut.create(ORGANIZATION_ID, newEntrypoint, true, null).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(Entrypoint::isDefaultEntrypoint);
    }

    @Test
    public void shouldNotCreateDefaultEntrypointWhenCallerOmitsTheFlag() {
        // The three-arg form is what the public REST resource calls; it must never raise the flag.
        NewEntrypoint newEntrypoint = newEntrypointFor("auth.acme.com");

        TestObserver<Entrypoint> obs = cut.create(ORGANIZATION_ID, newEntrypoint, null).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(entrypoint -> !entrypoint.isDefaultEntrypoint());
    }

    @Test
    public void shouldDeleteDefaultFlaggedEnvironmentEntrypoint() {
        Entrypoint environmentEntrypoint = new Entrypoint();
        environmentEntrypoint.setId(ENTRYPOINT_ID);
        environmentEntrypoint.setOrganizationId(ORGANIZATION_ID);
        environmentEntrypoint.setEnvironmentId("env#1");
        environmentEntrypoint.setDefaultEntrypoint(true);

        Entrypoint organizationDefault = new Entrypoint();
        organizationDefault.setId("org-default");
        organizationDefault.setOrganizationId(ORGANIZATION_ID);
        organizationDefault.setDefaultEntrypoint(true);

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(environmentEntrypoint));
        when(entrypointRepository.findAll(ORGANIZATION_ID)).thenReturn(Flowable.just(environmentEntrypoint, organizationDefault));
        when(entrypointRepository.delete(ENTRYPOINT_ID)).thenReturn(Completable.complete());

        TestObserver<Void> obs = cut.delete(ENTRYPOINT_ID, ORGANIZATION_ID, null).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();

        verify(entrypointRepository, times(1)).delete(ENTRYPOINT_ID);
    }

    @Test
    public void shouldDeleteWithNullPrincipal() {

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);
        existingEntrypoint.setEnvironmentId("env#1");

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.delete(ENTRYPOINT_ID)).thenReturn(Completable.complete());

        TestObserver<Void> obs = cut.delete(ENTRYPOINT_ID, ORGANIZATION_ID, null).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(EventType.ENTRYPOINT_DELETED, audit.getType());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals("system", audit.getActor().getId());

            return true;
        }));
    }

    @Test
    public void shouldCreateDefaults() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(entrypointRepository.create(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("gravitee.io", null);

        TestSubscriber<Entrypoint> obs = cut.createDefaults(organization).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(entrypoint -> entrypoint.getId() != null
                && entrypoint.isDefaultEntrypoint() && entrypoint.getOrganizationId().equals(ORGANIZATION_ID));

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(EventType.ENTRYPOINT_CREATED, audit.getType());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals("system", audit.getActor().getId());

            return true;
        }));
    }

    @Test
    public void shouldCreateDefaultsWithDomainRestrictions() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);
        organization.setDomainRestrictions(Arrays.asList("domain1.gravitee.io", "domain2.gravitee.io"));

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(entrypointRepository.create(argThat(e -> e != null && e.getUrl().equals("https://domain1.gravitee.io") && e.isDefaultEntrypoint()))).thenAnswer(i -> Single.just(i.getArgument(0)));
        when(entrypointRepository.create(argThat(e -> e != null && e.getUrl().equals("https://domain2.gravitee.io") && !e.isDefaultEntrypoint()))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("domain1.gravitee.io", List.of("domain1.gravitee.io", "domain2.gravitee.io"));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("domain2.gravitee.io", List.of("domain1.gravitee.io", "domain2.gravitee.io"));

        TestSubscriber<Entrypoint> obs = cut.createDefaults(organization).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueAt(0, entrypoint -> entrypoint.getId() != null
                && entrypoint.isDefaultEntrypoint() && entrypoint.getOrganizationId().equals(ORGANIZATION_ID));
        obs.assertValueAt(1, entrypoint -> entrypoint.getId() != null
                && !entrypoint.isDefaultEntrypoint() && entrypoint.getOrganizationId().equals(ORGANIZATION_ID));

        verify(auditService, times(2)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(EventType.ENTRYPOINT_CREATED, audit.getType());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals("system", audit.getActor().getId());

            return true;
        }));
    }

    @Test
    public void shouldCreate() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        NewEntrypoint newEntrypoint = new NewEntrypoint();
        newEntrypoint.setName("name");
        newEntrypoint.setDescription("description");
        newEntrypoint.setTags(Arrays.asList("tag#1", "tags#2"));
        newEntrypoint.setUrl("https://auth.gravitee.io");

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(entrypointRepository.create(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("auth.gravitee.io", null);
        TestObserver<Entrypoint> obs = cut.create(ORGANIZATION_ID, newEntrypoint, user).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(entrypoint -> entrypoint.getId() != null
                && !entrypoint.isDefaultEntrypoint()
                && entrypoint.getOrganizationId().equals(ORGANIZATION_ID)
                && entrypoint.getName().equals(newEntrypoint.getName())
                && entrypoint.getDescription().equals(newEntrypoint.getDescription())
                && entrypoint.getTags().equals(newEntrypoint.getTags())
                && entrypoint.getUrl().equals(newEntrypoint.getUrl()));

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(EventType.ENTRYPOINT_CREATED, audit.getType());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(user.getId(), audit.getActor().getId());

            return true;
        }));
    }

    @Test
    public void shouldNotCreate_badUrl() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        NewEntrypoint newEntrypoint = new NewEntrypoint();
        newEntrypoint.setName("name");
        newEntrypoint.setDescription("description");
        newEntrypoint.setTags(Arrays.asList("tag#1", "tags#2"));
        newEntrypoint.setUrl("invalid");

        when(entrypointRepository.create(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        TestObserver<Entrypoint> obs = cut.create(ORGANIZATION_ID, newEntrypoint, user).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidEntrypointException.class);

        verify(auditService, times(0)).report(any());
    }

    @Test
    public void shouldUpdate() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);

        UpdateEntrypoint updateEntrypoint = new UpdateEntrypoint();
        updateEntrypoint.setName("name");
        updateEntrypoint.setDescription("description");
        updateEntrypoint.setTags(Arrays.asList("tag#1", "tags#2"));
        updateEntrypoint.setUrl("https://auth.gravitee.io");

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(new Organization()));
        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.update(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("auth.gravitee.io", null);

        TestObserver<Entrypoint> obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, updateEntrypoint, user).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(entrypoint -> entrypoint.getId() != null
                && !entrypoint.isDefaultEntrypoint()
                && entrypoint.getOrganizationId().equals(ORGANIZATION_ID)
                && entrypoint.getName().equals(updateEntrypoint.getName())
                && entrypoint.getDescription().equals(updateEntrypoint.getDescription())
                && entrypoint.getTags().equals(updateEntrypoint.getTags())
                && entrypoint.getUrl().equals(updateEntrypoint.getUrl())
                && entrypoint.getUpdatedAt() != null);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(EventType.ENTRYPOINT_UPDATED, audit.getType());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(user.getId(), audit.getActor().getId());

            return true;
        }));
    }

    @Test
    public void shouldNotUpdate_badUrl() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);

        UpdateEntrypoint updateEntrypoint = new UpdateEntrypoint();
        updateEntrypoint.setName("name");
        updateEntrypoint.setDescription("description");
        updateEntrypoint.setTags(Arrays.asList("tag#1", "tags#2"));
        updateEntrypoint.setUrl("invalid");

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.update(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        TestObserver<Entrypoint> obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, updateEntrypoint, user).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidEntrypointException.class);

        verify(auditService, times(0)).report(any());
    }

    @Test
    public void shouldNotUpdate_notExistingEntrypoint() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());

        TestObserver<Entrypoint> obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, new UpdateEntrypoint(), user).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(EntrypointNotFoundException.class);

        verify(auditService, times(0)).report(any());
    }

    @Test
    public void shouldUpdateDefault_onlyUrl() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);
        existingEntrypoint.setName("name");
        existingEntrypoint.setDescription("description");
        existingEntrypoint.setTags(Arrays.asList("tag#1", "tags#2"));
        existingEntrypoint.setUrl("https://current.com");

        UpdateEntrypoint updateEntrypoint = new UpdateEntrypoint();
        updateEntrypoint.setName("name");
        updateEntrypoint.setDescription("description");
        updateEntrypoint.setTags(Arrays.asList("tag#1", "tags#2"));
        updateEntrypoint.setUrl("https://changed.com");

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(new Organization()));
        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.update(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("changed.com", null);

        TestObserver<Entrypoint> obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, updateEntrypoint, user).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(entrypoint -> entrypoint.getId() != null
                && !entrypoint.isDefaultEntrypoint()
                && entrypoint.getOrganizationId().equals(ORGANIZATION_ID)
                && entrypoint.getName().equals(updateEntrypoint.getName())
                && entrypoint.getDescription().equals(updateEntrypoint.getDescription())
                && entrypoint.getTags().equals(updateEntrypoint.getTags())
                && entrypoint.getUrl().equals(updateEntrypoint.getUrl())
                && entrypoint.getUpdatedAt() != null);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(EventType.ENTRYPOINT_UPDATED, audit.getType());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(user.getId(), audit.getActor().getId());

            return true;
        }));
    }

    @Test
    public void shouldNotUpdateDefault_onlyUrl() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);
        existingEntrypoint.setName("name");
        existingEntrypoint.setDescription("description");
        existingEntrypoint.setTags(Collections.emptyList());
        existingEntrypoint.setUrl("https://current.com");
        existingEntrypoint.setDefaultEntrypoint(true);

        UpdateEntrypoint updateEntrypoint = new UpdateEntrypoint();
        updateEntrypoint.setName("name");
        updateEntrypoint.setDescription("description");
        updateEntrypoint.setTags(Collections.emptyList());
        updateEntrypoint.setUrl("https://changed.com");

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.update(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        TestObserver<Entrypoint> obs;

        updateEntrypoint.setName("updated");
        obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, updateEntrypoint, user).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidEntrypointException.class);

        updateEntrypoint.setName(existingEntrypoint.getName());
        updateEntrypoint.setDescription("updated");
        obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, updateEntrypoint, user).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidEntrypointException.class);

        updateEntrypoint.setDescription(existingEntrypoint.getDescription());
        updateEntrypoint.setTags(List.of("updated"));
        obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, updateEntrypoint, user).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidEntrypointException.class);

        verify(auditService, times(0)).report(any());
    }

    @Test
    public void shouldDelete() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.delete(ENTRYPOINT_ID)).thenReturn(Completable.complete());

        TestObserver<Void> obs = cut.delete(ENTRYPOINT_ID, ORGANIZATION_ID, user).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(EventType.ENTRYPOINT_DELETED, audit.getType());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(user.getId(), audit.getActor().getId());

            return true;
        }));
    }

    @Test
    public void shouldNotDelete_onlyDefaultEntrypoint() {
        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);
        existingEntrypoint.setDefaultEntrypoint(true);

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.findAll(ORGANIZATION_ID)).thenReturn(Flowable.just(existingEntrypoint));

        cut.delete(ENTRYPOINT_ID, ORGANIZATION_ID, user).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(LastDefaultEntrypointException.class);

        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(EventType.ENTRYPOINT_DELETED, audit.getType());
            assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
            assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
            assertEquals(ORGANIZATION_ID, audit.getReferenceId());
            assertEquals(user.getId(), audit.getActor().getId());

            return true;
        }));
    }


    @Test
    public void shouldNotDelete_notExistingEntrypoint() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());

        TestObserver<Void> obs = cut.delete(ENTRYPOINT_ID, ORGANIZATION_ID, user).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(EntrypointNotFoundException.class);

        verify(auditService, times(0)).report(any());
    }

    @Test
    public void shouldPublishEntrypointEventOnCreate() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        NewEntrypoint newEntrypoint = new NewEntrypoint();
        newEntrypoint.setName("name");
        newEntrypoint.setDescription("description");
        newEntrypoint.setTags(Arrays.asList("tag#1"));
        newEntrypoint.setUrl("https://auth.gravitee.io");

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(entrypointRepository.create(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("auth.gravitee.io", null);

        cut.create(ORGANIZATION_ID, newEntrypoint, null).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(eventService, times(1)).create(argThat(event ->
                event.getType() == Type.ENTRYPOINT
                        && event.getPayload().getAction() == Action.CREATE
                        && event.getPayload().getReferenceType() == ReferenceType.ORGANIZATION
                        && ORGANIZATION_ID.equals(event.getPayload().getReferenceId())
                        && event.getDataPlaneId() == null));
    }

    @Test
    public void shouldPublishEntrypointEventOnCreate_withEnvironment() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        NewEntrypoint newEntrypoint = new NewEntrypoint();
        newEntrypoint.setName("name");
        newEntrypoint.setDescription("description");
        newEntrypoint.setTags(Arrays.asList("tag#1"));
        newEntrypoint.setUrl("https://auth.gravitee.io");
        newEntrypoint.setEnvironmentId("env#1");

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(entrypointRepository.create(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("auth.gravitee.io", null);

        cut.create(ORGANIZATION_ID, newEntrypoint, null).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(eventService, times(1)).create(argThat(event ->
                event.getType() == Type.ENTRYPOINT
                        && event.getPayload().getAction() == Action.CREATE
                        && event.getPayload().getReferenceType() == ReferenceType.ENVIRONMENT
                        && "env#1".equals(event.getPayload().getReferenceId())
                        && "env#1".equals(event.getEnvironmentId())
                        && event.getDataPlaneId() == null));
    }

    @Test
    public void shouldPublishEntrypointEventOnUpdate() {

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);

        UpdateEntrypoint updateEntrypoint = new UpdateEntrypoint();
        updateEntrypoint.setName("name");
        updateEntrypoint.setDescription("description");
        updateEntrypoint.setTags(Arrays.asList("tag#1"));
        updateEntrypoint.setUrl("https://auth.gravitee.io");

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(new Organization()));
        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.update(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("auth.gravitee.io", null);

        cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, updateEntrypoint, null).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(eventService, times(1)).create(argThat(event ->
                event.getType() == Type.ENTRYPOINT
                        && event.getPayload().getAction() == Action.UPDATE
                        && event.getPayload().getReferenceType() == ReferenceType.ORGANIZATION
                        && ORGANIZATION_ID.equals(event.getPayload().getReferenceId())
                        && event.getDataPlaneId() == null));
    }

    @Test
    public void shouldPublishEntrypointEventOnDelete() {

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);
        existingEntrypoint.setEnvironmentId("env#1");

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.delete(ENTRYPOINT_ID)).thenReturn(Completable.complete());

        cut.delete(ENTRYPOINT_ID, ORGANIZATION_ID, null).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(eventService, times(1)).create(argThat(event ->
                event.getType() == Type.ENTRYPOINT
                        && event.getPayload().getAction() == Action.DELETE
                        && event.getPayload().getReferenceType() == ReferenceType.ENVIRONMENT
                        && "env#1".equals(event.getPayload().getReferenceId())
                        && "env#1".equals(event.getEnvironmentId())
                        && event.getDataPlaneId() == null));
    }

    private static Entrypoint storedEntrypoint(String id, String url, boolean defaultEntrypoint) {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(id);
        entrypoint.setOrganizationId(ORGANIZATION_ID);
        entrypoint.setEnvironmentId("env#1");
        entrypoint.setUrl(url);
        entrypoint.setDefaultEntrypoint(defaultEntrypoint);
        return entrypoint;
    }

    private static DesiredEntrypoint desiredEntrypoint(String host, boolean defaultEntrypoint) {
        return new DesiredEntrypoint("https://" + host, host, defaultEntrypoint);
    }

    private void syncForEnvironment(Entrypoint[] stored, DesiredEntrypoint... desired) {
        when(entrypointRepository.findByEnvironment(ORGANIZATION_ID, "env#1")).thenReturn(Flowable.fromArray(stored));
        lenient().when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(new Organization()));
        lenient().when(entrypointRepository.create(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        lenient().when(entrypointRepository.delete(anyString())).thenReturn(Completable.complete());
        lenient().doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain(anyString(), any());
        for (Entrypoint entrypoint : stored) {
            lenient().when(entrypointRepository.findById(entrypoint.getId(), ORGANIZATION_ID)).thenReturn(Maybe.just(entrypoint));
        }
        // the delete path reads the organization's entrypoints for its last-default guard
        Entrypoint organizationDefault = new Entrypoint();
        organizationDefault.setId("entrypoint#organization-default");
        organizationDefault.setOrganizationId(ORGANIZATION_ID);
        organizationDefault.setDefaultEntrypoint(true);
        lenient().when(entrypointRepository.findAll(ORGANIZATION_ID)).thenReturn(Flowable.just(organizationDefault));

        cut.syncForEnvironment(ORGANIZATION_ID, "env#1", List.of(desired), null).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();
    }

    @Test
    public void syncForEnvironment_unchangedSet_touchesNothing() {
        syncForEnvironment(
                new Entrypoint[]{
                        storedEntrypoint("entrypoint#1", "https://one.gravitee.io", true),
                        storedEntrypoint("entrypoint#2", "https://custom.acme.com", false)},
                desiredEntrypoint("one.gravitee.io", true),
                desiredEntrypoint("custom.acme.com", false));

        verify(entrypointRepository, never()).delete(anyString());
        verify(entrypointRepository, never()).create(any(Entrypoint.class));
        verifyNoInteractions(eventService);
    }

    @Test
    public void syncForEnvironment_addedAndRemoved_onlyTouchesWhatMoved() {
        syncForEnvironment(
                new Entrypoint[]{
                        storedEntrypoint("entrypoint#kept", "https://kept.gravitee.io", true),
                        storedEntrypoint("entrypoint#gone", "https://gone.gravitee.io", true)},
                desiredEntrypoint("kept.gravitee.io", true),
                desiredEntrypoint("added.gravitee.io", true));

        verify(entrypointRepository, times(1)).delete("entrypoint#gone");
        verify(entrypointRepository, times(1)).delete(anyString());
        verify(entrypointRepository, times(1)).create(argThat(entrypoint ->
                "https://added.gravitee.io".equals(entrypoint.getUrl())
                        && "added.gravitee.io".equals(entrypoint.getName())
                        && "env#1".equals(entrypoint.getEnvironmentId())
                        && entrypoint.isDefaultEntrypoint()));
        verify(entrypointRepository, times(1)).create(any(Entrypoint.class));
    }

    @Test
    public void syncForEnvironment_defaultFlagChanged_replacesTheEntrypoint() {
        syncForEnvironment(
                new Entrypoint[]{storedEntrypoint("entrypoint#1", "https://custom.acme.com", true)},
                desiredEntrypoint("custom.acme.com", false));

        verify(entrypointRepository, times(1)).delete("entrypoint#1");
        verify(entrypointRepository, times(1)).create(argThat(entrypoint ->
                "https://custom.acme.com".equals(entrypoint.getUrl()) && !entrypoint.isDefaultEntrypoint()));
        verify(entrypointRepository, never()).update(any(Entrypoint.class));
    }

    @Test
    public void syncForEnvironment_duplicateStoredRows_keepsOnlyOne() {
        syncForEnvironment(
                new Entrypoint[]{
                        storedEntrypoint("entrypoint#1", "https://one.gravitee.io", true),
                        storedEntrypoint("entrypoint#2", "https://one.gravitee.io", true)},
                desiredEntrypoint("one.gravitee.io", true));

        verify(entrypointRepository, times(1)).delete("entrypoint#2");
        verify(entrypointRepository, times(1)).delete(anyString());
        verify(entrypointRepository, never()).create(any(Entrypoint.class));
    }

    @Test
    public void syncForEnvironment_urlStatedTwice_createsOneEntrypoint() {
        syncForEnvironment(
                new Entrypoint[]{},
                desiredEntrypoint("one.gravitee.io", true),
                desiredEntrypoint("one.gravitee.io", true));

        verify(entrypointRepository, times(1)).create(any(Entrypoint.class));
    }

    @Test
    public void cloudMode_shouldPublishOneUntargetedEntrypointEvent() {

        Entrypoint existingEntrypoint = new Entrypoint();
        existingEntrypoint.setId(ENTRYPOINT_ID);
        existingEntrypoint.setOrganizationId(ORGANIZATION_ID);
        existingEntrypoint.setEnvironmentId("env#1");

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(existingEntrypoint));
        when(entrypointRepository.delete(ENTRYPOINT_ID)).thenReturn(Completable.complete());

        cloudModeEntrypointService().delete(ENTRYPOINT_ID, ORGANIZATION_ID, null).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(eventService, times(1)).create(argThat(event ->
                event.getType() == Type.ENTRYPOINT
                        && "env#1".equals(event.getEnvironmentId())
                        && event.getDataPlaneId() == null));
    }

    @Test
    public void cloudMode_createDefaults_createsNoOrganizationEntrypoint() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        TestSubscriber<Entrypoint> obs = cloudModeEntrypointService().createDefaults(organization).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoValues();

        verifyNoInteractions(entrypointRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    public void cloudMode_createDefaultsWithDomainRestrictions_createsNoOrganizationEntrypoint() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);
        organization.setDomainRestrictions(Arrays.asList("domain1.gravitee.io", "domain2.gravitee.io"));

        TestSubscriber<Entrypoint> obs = cloudModeEntrypointService().createDefaults(organization).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoValues();

        verifyNoInteractions(entrypointRepository);
    }

    @Test
    public void cloudMode_deletesTheLastDefaultEntrypoint() {
        // An ENVIRONMENT re-sync for a single-environment organization deletes the only default entrypoint there is.
        Entrypoint environmentEntrypoint = new Entrypoint();
        environmentEntrypoint.setId(ENTRYPOINT_ID);
        environmentEntrypoint.setOrganizationId(ORGANIZATION_ID);
        environmentEntrypoint.setEnvironmentId("env#1");
        environmentEntrypoint.setDefaultEntrypoint(true);

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(environmentEntrypoint));
        when(entrypointRepository.delete(ENTRYPOINT_ID)).thenReturn(Completable.complete());

        TestObserver<Void> obs = cloudModeEntrypointService().delete(ENTRYPOINT_ID, ORGANIZATION_ID, null).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();

        verify(entrypointRepository, times(1)).delete(ENTRYPOINT_ID);
        // findAll is the guard's read, so never calling it is how we know the guard was skipped
        verify(entrypointRepository, never()).findAll(ORGANIZATION_ID);
    }
}
