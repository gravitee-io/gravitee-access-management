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

import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.RoleServiceImpl;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.am.service.model.UpdateRole;
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
import java.util.Collections;
import java.util.Set;

import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RoleServiceTest {

    public static final String ORGANIZATION_ID = "orga#1";
    @InjectMocks
    private RoleService roleService = new RoleServiceImpl();

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private EventService eventService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(roleRepository.findById("my-role")).thenReturn(Maybe.just(new Role()));
        TestObserver testObserver = roleService.findById("my-role").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingRole() {
        when(roleRepository.findById("my-role")).thenReturn(Maybe.empty());
        TestObserver testObserver = roleService.findById("my-role").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(roleRepository.findById("my-role")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        roleService.findById("my-role").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(new Role()));
        TestObserver<Set<Role>> testObserver = roleService.findByDomain(DOMAIN).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(roles -> roles.size() == 1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.error(TechnicalManagementException::new));

        TestObserver testObserver = roleService.findByDomain(DOMAIN).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByIdsIn() {
        when(roleRepository.findByIdIn(Arrays.asList("my-role"))).thenReturn(Flowable.just(new Role()));
        TestObserver<Set<Role>> testObserver = roleService.findByIdIn(Arrays.asList("my-role")).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(roles -> roles.size() == 1);
    }

    @Test
    public void shouldFindByIdsIn_technicalException() {
        when(roleRepository.findByIdIn(anyList())).thenReturn(Flowable.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        roleService.findByIdIn(Arrays.asList("my-role")).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewRole newRole = Mockito.mock(NewRole.class);
        when(roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.empty());
        Role role = new Role();
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setReferenceId("domain#1");
        when(roleRepository.create(any(Role.class))).thenReturn(Single.just(role));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = roleService.create(DOMAIN, newRole).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(roleRepository, times(1)).findAll(ReferenceType.DOMAIN, DOMAIN);
        verify(roleRepository, times(1)).create(any(Role.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewRole newRole = Mockito.mock(NewRole.class);
        when(roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        roleService.create(DOMAIN, newRole).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(roleRepository, never()).create(any(Role.class));
    }

    @Test
    public void shouldCreate_uniquenessException() {
        NewRole newRole = Mockito.mock(NewRole.class);
        when(newRole.getName()).thenReturn("existing-role-name");

        Role role = new Role();
        role.setId("existing-role-id");
        role.setName("existing-role-name");
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setReferenceId("domain#1");

        when(roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(role));

        TestObserver testObserver = new TestObserver();
        roleService.create(DOMAIN, newRole).subscribe(testObserver);

        testObserver.assertError(RoleAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(roleRepository, never()).create(any(Role.class));
    }

    @Test
    public void shouldUpdate() {
        UpdateRole updateRole = Mockito.mock(UpdateRole.class);
        Role role = new Role();
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setReferenceId("domain#1");
        when(roleRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-role")).thenReturn(Maybe.just(role));
        when(roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.empty());
        when(roleRepository.update(any(Role.class))).thenReturn(Single.just(role));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = roleService.update(DOMAIN, "my-role", updateRole).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(roleRepository, times(1)).findById(ReferenceType.DOMAIN, DOMAIN, "my-role");
        verify(roleRepository, times(1)).findAll(ReferenceType.DOMAIN, DOMAIN);
        verify(roleRepository, times(1)).update(any(Role.class));
    }

    @Test
    public void shouldUpdate_defaultRolePermissions() {
        UpdateRole updateRole = new UpdateRole();
        updateRole.setName(DefaultRole.DOMAIN_USER.name());
        updateRole.setPermissions(Permission.flatten(Collections.singletonMap(Permission.DOMAIN, Collections.singleton(Acl.READ))));

        Role role = new Role();
        role.setName(DefaultRole.DOMAIN_USER.name());
        role.setDefaultRole(true); // should be able to update a default role.
        role.setReferenceType(ReferenceType.ORGANIZATION);
        role.setReferenceId(ORGANIZATION_ID);

        when(roleRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "my-role")).thenReturn(Maybe.just(role));
        when(roleRepository.findAll(ReferenceType.ORGANIZATION, ORGANIZATION_ID)).thenReturn(Flowable.empty());
        when(roleRepository.update(argThat(r -> r.getPermissionAcls().equals(Permission.unflatten(updateRole.getPermissions()))))).thenReturn(Single.just(role));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = roleService.update(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "my-role", updateRole, null).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(roleRepository, times(1)).findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "my-role");
        verify(roleRepository, times(1)).findAll(ReferenceType.ORGANIZATION, ORGANIZATION_ID);
        verify(roleRepository, times(1)).update(any(Role.class));
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateRole updateRole = Mockito.mock(UpdateRole.class);
        when(roleRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-role")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        roleService.update(DOMAIN, "my-role", updateRole).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(roleRepository, never()).findAll(ReferenceType.DOMAIN, DOMAIN);
        verify(roleRepository, never()).update(any(Role.class));
    }

    @Test
    public void shouldUpdate_uniquenessException() {
        UpdateRole updateRole = Mockito.mock(UpdateRole.class);
        when(updateRole.getName()).thenReturn("existing-role-name");

        Role role = new Role();
        role.setId("existing-role-id");
        role.setName("existing-role-name");
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setReferenceId("domain#1");

        when(roleRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-role")).thenReturn(Maybe.just(new Role()));
        when(roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(role));

        TestObserver testObserver = new TestObserver();
        roleService.update(DOMAIN, "my-role", updateRole).subscribe(testObserver);

        testObserver.assertError(RoleAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(roleRepository, never()).create(any(Role.class));
    }

    @Test
    public void shouldUpdate_roleNotFound() {
        UpdateRole updateRole = Mockito.mock(UpdateRole.class);
        when(roleRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-role")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        roleService.update(DOMAIN, "my-role", updateRole).subscribe(testObserver);

        testObserver.assertError(RoleNotFoundException.class);
        testObserver.assertNotComplete();

        verify(roleRepository, never()).findAll(ReferenceType.DOMAIN, DOMAIN);
        verify(roleRepository, never()).create(any(Role.class));
    }

    @Test
    public void shouldNotUpdate_systemRole() {
        UpdateRole updateRole = new UpdateRole();

        Role role = new Role();
        role.setSystem(true);
        role.setReferenceType(ReferenceType.ORGANIZATION);
        role.setReferenceId(ORGANIZATION_ID);

        when(roleRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "my-role")).thenReturn(Maybe.just(role));

        TestObserver testObserver = roleService.update(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "my-role", updateRole, null).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(SystemRoleUpdateException.class);

        verify(roleRepository, times(1)).findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "my-role");
        verify(roleRepository, never()).findAll(ReferenceType.ORGANIZATION, ORGANIZATION_ID);
        verify(roleRepository, never()).update(any(Role.class));
    }

    @Test
    public void shouldNotUpdate_defaultRoleName() {

        UpdateRole updateRole = new UpdateRole();
        updateRole.setName("new name");

        Role role = new Role();
        role.setId("my-role");
        role.setName(DefaultRole.DOMAIN_USER.name());
        role.setDefaultRole(true);
        role.setReferenceType(ReferenceType.ORGANIZATION);
        role.setReferenceId(ORGANIZATION_ID);

        when(roleRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "my-role")).thenReturn(Maybe.just(role));

        TestObserver testObserver = roleService.update(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "my-role", updateRole, null).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(DefaultRoleUpdateException.class);

        verify(roleRepository, times(1)).findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "my-role");
        verify(roleRepository, never()).findAll(ReferenceType.ORGANIZATION, ORGANIZATION_ID);
        verify(roleRepository, never()).update(any(Role.class));
    }

    @Test
    public void shouldDelete_notExistingRole() {
        when(roleRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-role")).thenReturn(Maybe.empty());

        TestObserver testObserver = roleService.delete(ReferenceType.DOMAIN, DOMAIN, "my-role").test();

        testObserver.assertError(RoleNotFoundException.class);
        testObserver.assertNotComplete();

        verify(roleRepository, never()).delete(anyString());
    }

    @Test
    public void shouldDelete_technicalException() {

        when(eventService.create(any(Event.class))).thenReturn(Single.just(new Event()));
        when(roleRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-role"))).thenReturn(Maybe.just(new Role()));
        when(roleRepository.delete(anyString())).thenReturn(Completable.error(TechnicalException::new));

        TestObserver testObserver = roleService.delete(ReferenceType.DOMAIN, DOMAIN, "my-role").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldNotDelete_systemRole() {
        Role role = Mockito.mock(Role.class);
        when(role.isSystem()).thenReturn(true);
        when(roleRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-role"))).thenReturn(Maybe.just(role));

        TestObserver testObserver = roleService.delete(ReferenceType.DOMAIN, DOMAIN, "my-role").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(SystemRoleDeleteException.class);

        verify(roleRepository, never()).delete("my-role");
    }

    @Test
    public void shouldDelete() {
        Role role = new Role();
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setReferenceId(DOMAIN);
        when(roleRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-role"))).thenReturn(Maybe.just(role));
        when(roleRepository.delete("my-role")).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = roleService.delete(ReferenceType.DOMAIN, DOMAIN, "my-role").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(roleRepository, times(1)).delete("my-role");
    }
}
