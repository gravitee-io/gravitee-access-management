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
package io.gravitee.am.gateway.handler.common.role.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.RoleEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class InMemoryRoleManagerImplTest {
    public static final String ADMIN_ID = "admin-id";
    public static final String USER_ID = "user-id";
    private Map<String, Role> roles;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private Domain domain;

    @InjectMocks
    private InMemoryRoleManager roleManager;

    @BeforeEach
    public void setUp() {
        roles = (Map<String, Role>) ReflectionTestUtils.getField(roleManager, "roles");
        roles.clear();
    }

    @Test
    public void shouldGetAllOfRolesByIdIn() {
        final Role adminRole = new Role();
        adminRole.setId(ADMIN_ID);
        adminRole.setName("admin");
        adminRole.setDescription("admin");
        adminRole.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        final Role userRole = new Role();
        userRole.setId(USER_ID);
        userRole.setName("user");
        userRole.setDescription("user");
        userRole.setCreatedAt(new Date(Instant.now().minus(4, ChronoUnit.MINUTES).toEpochMilli()));
        roles.putAll(Map.of(ADMIN_ID, adminRole, USER_ID, userRole));

        TestSubscriber<Role> observer = roleManager.findByIdIn(List.of(ADMIN_ID, USER_ID)).test();
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValueAt(0, role -> role.getId().equals(ADMIN_ID));
        observer.assertValueAt(1, role -> role.getId().equals(USER_ID));
    }

    @Test
    public void shouldGetOneOfRolesByIdIn() {
        final Role adminRole = new Role();
        adminRole.setId(ADMIN_ID);
        adminRole.setName("admin");
        adminRole.setDescription("admin");
        adminRole.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli()));
        roles.put(ADMIN_ID, adminRole);

        TestSubscriber<Role> observer = roleManager.findByIdIn(List.of(ADMIN_ID, USER_ID)).test();
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertValue(role -> role.getId().equals(ADMIN_ID));
    }

    @Test
    public void shouldDeployRoleToManager() {
        final String domainId = "domain-id";
        final Role adminRole = new Role();
        adminRole.setId(ADMIN_ID);
        adminRole.setName("admin");
        adminRole.setDescription("admin");
        adminRole.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli()));
        Payload payload = new Payload(ADMIN_ID, new Reference(ReferenceType.DOMAIN, domainId), Action.CREATE);
        Event<RoleEvent, Payload> event = new SimpleEvent<>(RoleEvent.DEPLOY, payload);
        when(roleRepository.findById(any())).thenReturn(Maybe.just(adminRole));
        when(domain.getId()).thenReturn(domainId);
        roleManager.onEvent(event);
        TestSubscriber<Role> observer = roleManager.findByIdIn(List.of(ADMIN_ID, USER_ID)).test();
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertValue(role -> role.getId().equals(ADMIN_ID));
    }

    @Test
    public void shouldDeleteRoleFromManager() {
        final String domainId = "domain-id";
        Payload payload = new Payload(ADMIN_ID, new Reference(ReferenceType.DOMAIN, domainId), Action.DELETE);
        Event<RoleEvent, Payload> event = new SimpleEvent<>(RoleEvent.UNDEPLOY, payload);
        when(domain.getId()).thenReturn(domainId);
        roleManager.onEvent(event);
        TestSubscriber<Role> observer = roleManager.findByIdIn(List.of(ADMIN_ID, USER_ID)).test();
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertNoValues();
    }

}