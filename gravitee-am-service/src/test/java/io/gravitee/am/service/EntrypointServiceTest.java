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
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.EntrypointRepository;
import io.gravitee.am.service.exception.EntrypointNotFoundException;
import io.gravitee.am.service.exception.InvalidEntrypointException;
import io.gravitee.am.service.impl.EntrypointServiceImpl;
import io.gravitee.am.service.model.NewEntrypoint;
import io.gravitee.am.service.model.UpdateEntrypoint;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidator;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    private EntrypointService cut;

    @Before
    public void before() {

        cut = new EntrypointServiceImpl(entrypointRepository, organizationService, auditService, virtualHostValidator, "https://gravitee.io");
    }

    @Test
    public void shouldFindById() {

        Entrypoint entrypoint = new Entrypoint();
        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.just(entrypoint));

        TestObserver<Entrypoint> obs = cut.findById(ENTRYPOINT_ID, ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(entrypoint);
    }

    @Test
    public void shouldFindById_notExistingEntrypoint() {

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());

        TestObserver<Entrypoint> obs = cut.findById(ENTRYPOINT_ID, ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(EntrypointNotFoundException.class);
    }

    @Test
    public void shouldFindById_technicalException() {

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<Entrypoint> obs = cut.findById(ENTRYPOINT_ID, ORGANIZATION_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalException.class);
    }

    @Test
    public void shouldCreateDefaults() {

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));
        when(entrypointRepository.create(any(Entrypoint.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        doReturn(true).when(virtualHostValidator).isValidDomainOrSubDomain("gravitee.io", null);

        TestSubscriber<Entrypoint> obs = cut.createDefaults(organization).test();

        obs.awaitTerminalEvent();
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

        obs.awaitTerminalEvent();
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

        obs.awaitTerminalEvent();
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

        obs.awaitTerminalEvent();
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

        obs.awaitTerminalEvent();
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

        obs.awaitTerminalEvent();
        obs.assertError(InvalidEntrypointException.class);

        verify(auditService, times(0)).report(any());
    }

    @Test
    public void shouldNotUpdate_notExistingEntrypoint() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());

        TestObserver<Entrypoint> obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, new UpdateEntrypoint(), user).test();

        obs.awaitTerminalEvent();
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

        obs.awaitTerminalEvent();
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
        obs.awaitTerminalEvent();
        obs.assertError(InvalidEntrypointException.class);

        updateEntrypoint.setName(existingEntrypoint.getName());
        updateEntrypoint.setDescription("updated");
        obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, updateEntrypoint, user).test();
        obs.awaitTerminalEvent();
        obs.assertError(InvalidEntrypointException.class);

        updateEntrypoint.setDescription(existingEntrypoint.getDescription());
        updateEntrypoint.setTags(Arrays.asList("updated"));
        obs = cut.update(ENTRYPOINT_ID, ORGANIZATION_ID, updateEntrypoint, user).test();
        obs.awaitTerminalEvent();
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

        obs.awaitTerminalEvent();
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
    public void shouldNotDelete_notExistingEntrypoint() {

        DefaultUser user = new DefaultUser("test");
        user.setId(USER_ID);

        when(entrypointRepository.findById(ENTRYPOINT_ID, ORGANIZATION_ID)).thenReturn(Maybe.empty());

        TestObserver<Void> obs = cut.delete(ENTRYPOINT_ID, ORGANIZATION_ID, user).test();

        obs.awaitTerminalEvent();
        obs.assertError(EntrypointNotFoundException.class);

        verify(auditService, times(0)).report(any());
    }
}
