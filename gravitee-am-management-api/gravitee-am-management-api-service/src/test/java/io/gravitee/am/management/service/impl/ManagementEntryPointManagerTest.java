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

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.EntrypointEvent;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.EntrypointNotFoundException;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.SimpleEvent;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ManagementEntryPointManagerTest {

    @Mock
    private EntrypointService entrypointService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private EventManager eventManager;

    @Mock
    private Node node;

    @InjectMocks
    private ManagementEntryPointManager cut;

    private Entrypoint entrypoint;

    @Before
    public void before() {
        when(node.metadata()).thenReturn(Map.of());

        entrypoint = new Entrypoint();
        entrypoint.setId("e1");
        entrypoint.setOrganizationId("org#1");
        entrypoint.setEnvironmentId("env#1");
    }

    private static Environment environment() {
        Environment environment = new Environment();
        environment.setId("env#1");
        environment.setOrganizationId("org#1");
        return environment;
    }

    private static SimpleEvent<EntrypointEvent, Payload> event(EntrypointEvent type) {
        return new SimpleEvent<>(type, new Payload("e1", ReferenceType.ENVIRONMENT, "env#1", Action.UPDATE));
    }

    @Test
    public void shouldEvictEntrypointWhenNoLongerFound() {
        when(environmentService.findById("env#1")).thenReturn(Single.just(environment()));
        when(entrypointService.findById("e1", "org#1"))
                .thenReturn(Single.just(entrypoint), Single.error(new EntrypointNotFoundException("e1")));

        cut.onEvent(event(EntrypointEvent.DEPLOY));
        assertEquals(List.of(entrypoint), cut.findByEnvironmentId("env#1"));

        cut.onEvent(event(EntrypointEvent.UPDATE));
        assertTrue(cut.findByEnvironmentId("env#1").isEmpty());
    }

    @Test
    public void shouldEvictEntrypointWhenEnvironmentIsGone() {
        when(environmentService.findById("env#1"))
                .thenReturn(Single.just(environment()), Single.error(new EnvironmentNotFoundException("env#1")));
        when(entrypointService.findById("e1", "org#1")).thenReturn(Single.just(entrypoint));

        cut.onEvent(event(EntrypointEvent.DEPLOY));
        assertEquals(List.of(entrypoint), cut.findByEnvironmentId("env#1"));

        cut.onEvent(event(EntrypointEvent.UPDATE));
        assertTrue(cut.findByEnvironmentId("env#1").isEmpty());
    }
}
