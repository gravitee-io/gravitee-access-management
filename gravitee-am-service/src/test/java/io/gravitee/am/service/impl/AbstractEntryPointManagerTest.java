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

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.EntrypointEvent;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.EntrypointRepository;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.SimpleEvent;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Exercises the shared {@link AbstractEntryPointManager} logic through a repository-backed subclass.
 *
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractEntryPointManagerTest {

    @Mock
    private EntrypointRepository entrypointRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private EventManager eventManager;

    @Mock
    private Node node;

    private AbstractEntryPointManager cut;

    @Before
    public void before() {
        cut = new AbstractEntryPointManager(eventManager, node) {
            @Override
            protected Flowable<Entrypoint> loadOrganizationEntrypoints(String organizationId) {
                return entrypointRepository.findAll(organizationId);
            }

            @Override
            protected Flowable<Entrypoint> loadEnvironmentEntrypoints(String environmentId) {
                return environmentRepository.findById(environmentId)
                        .flatMapPublisher(environment -> entrypointRepository.findByEnvironment(environment.getOrganizationId(), environmentId));
            }

            @Override
            protected Flowable<String> allOrganizationIds() {
                return organizationRepository.findAll().map(Organization::getId);
            }

            @Override
            protected Maybe<Entrypoint> findEntrypointById(String entrypointId, ReferenceType referenceType, String referenceId) {
                return entrypointRepository.findById(entrypointId);
            }
        };
    }

    private static Entrypoint entrypoint(String id, String organizationId, String environmentId) {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(id);
        entrypoint.setOrganizationId(organizationId);
        entrypoint.setEnvironmentId(environmentId);
        return entrypoint;
    }

    private static SimpleEvent<EntrypointEvent, Payload> event(EntrypointEvent type, String id) {
        return new SimpleEvent<>(type, new Payload(id, ReferenceType.ENVIRONMENT, "env#1", Action.CREATE));
    }

    @Test
    public void shouldLoadAllOrganizationsWhenNoScopeConfigured() throws Exception {
        when(node.metadata()).thenReturn(Map.of());
        Organization organization = new Organization();
        organization.setId("org#1");
        when(organizationRepository.findAll()).thenReturn(Flowable.just(organization));
        Entrypoint entrypoint = entrypoint("e1", "org#1", null);
        when(entrypointRepository.findAll("org#1")).thenReturn(Flowable.just(entrypoint));

        cut.doStart();

        assertEquals(List.of(entrypoint), cut.findByOrganizationId("org#1"));
    }

    @Test
    public void shouldLoadConfiguredEnvironmentsAndOrganizations() throws Exception {
        when(node.metadata()).thenReturn(Map.of(
                Node.META_ORGANIZATIONS, Set.of("org#1"),
                Node.META_ENVIRONMENTS, Set.of("env#1")));

        Entrypoint orgEntrypoint = entrypoint("e-org", "org#1", null);
        when(entrypointRepository.findAll("org#1")).thenReturn(Flowable.just(orgEntrypoint));

        Environment environment = new Environment();
        environment.setId("env#1");
        environment.setOrganizationId("org#2");
        when(environmentRepository.findById("env#1")).thenReturn(Maybe.just(environment));
        Entrypoint envEntrypoint = entrypoint("e-env", "org#2", "env#1");
        when(entrypointRepository.findByEnvironment("org#2", "env#1")).thenReturn(Flowable.just(envEntrypoint));

        cut.doStart();

        assertEquals(List.of(orgEntrypoint), cut.findByOrganizationId("org#1"));
        assertEquals(List.of(envEntrypoint), cut.findByEnvironmentId("env#1"));
        assertTrue(cut.findByEnvironmentId("env#other").isEmpty());
    }

    @Test
    public void shouldAddEntrypointOnDeployEvent() {
        when(node.metadata()).thenReturn(Map.of());
        Entrypoint entrypoint = entrypoint("e1", "org#1", "env#1");
        when(entrypointRepository.findById("e1")).thenReturn(Maybe.just(entrypoint));

        cut.onEvent(event(EntrypointEvent.DEPLOY, "e1"));

        assertEquals(List.of(entrypoint), cut.findByEnvironmentId("env#1"));
    }

    @Test
    public void shouldReplaceEntrypointOnUpdateEvent() {
        when(node.metadata()).thenReturn(Map.of());
        Entrypoint original = entrypoint("e1", "org#1", "env#1");
        original.setUrl("https://before.example.com");
        Entrypoint updated = entrypoint("e1", "org#1", "env#1");
        updated.setUrl("https://after.example.com");
        when(entrypointRepository.findById("e1")).thenReturn(Maybe.just(original), Maybe.just(updated));

        cut.onEvent(event(EntrypointEvent.DEPLOY, "e1"));
        cut.onEvent(event(EntrypointEvent.UPDATE, "e1"));

        List<Entrypoint> result = cut.findByEnvironmentId("env#1");
        assertEquals(1, result.size());
        assertEquals("https://after.example.com", result.get(0).getUrl());
    }

    @Test
    public void shouldRemoveEntrypointOnUndeployEvent() {
        when(node.metadata()).thenReturn(Map.of());
        Entrypoint entrypoint = entrypoint("e1", "org#1", "env#1");
        when(entrypointRepository.findById("e1")).thenReturn(Maybe.just(entrypoint));

        cut.onEvent(event(EntrypointEvent.DEPLOY, "e1"));
        assertEquals(1, cut.findByOrganizationId("org#1").size());

        cut.onEvent(event(EntrypointEvent.UNDEPLOY, "e1"));
        assertTrue(cut.findByOrganizationId("org#1").isEmpty());
    }

    @Test
    public void shouldRemoveEntrypointOnUpdateWhenNoLongerResolvable() {
        when(node.metadata()).thenReturn(Map.of());
        Entrypoint entrypoint = entrypoint("e1", "org#1", "env#1");
        when(entrypointRepository.findById("e1")).thenReturn(Maybe.just(entrypoint), Maybe.empty());

        cut.onEvent(event(EntrypointEvent.DEPLOY, "e1"));
        assertEquals(1, cut.findByEnvironmentId("env#1").size());

        cut.onEvent(event(EntrypointEvent.UPDATE, "e1"));
        assertTrue(cut.findByEnvironmentId("env#1").isEmpty());
    }

    @Test
    public void shouldKeepCachedEntrypointWhenReloadFails() {
        when(node.metadata()).thenReturn(Map.of());
        Entrypoint entrypoint = entrypoint("e1", "org#1", "env#1");
        when(entrypointRepository.findById("e1")).thenReturn(Maybe.just(entrypoint), Maybe.error(new RuntimeException("database unavailable")));

        cut.onEvent(event(EntrypointEvent.DEPLOY, "e1"));
        cut.onEvent(event(EntrypointEvent.UPDATE, "e1"));

        assertEquals(List.of(entrypoint), cut.findByEnvironmentId("env#1"));
    }

    @Test
    public void shouldIgnoreOutOfScopeEntrypointOnEvent() {
        when(node.metadata()).thenReturn(Map.of(Node.META_ENVIRONMENTS, Set.of("env#1")));
        Entrypoint outOfScope = entrypoint("e2", "org#9", "env#9");
        when(entrypointRepository.findById("e2")).thenReturn(Maybe.just(outOfScope));

        cut.onEvent(event(EntrypointEvent.DEPLOY, "e2"));

        assertTrue(cut.findByEnvironmentId("env#9").isEmpty());
    }
}
