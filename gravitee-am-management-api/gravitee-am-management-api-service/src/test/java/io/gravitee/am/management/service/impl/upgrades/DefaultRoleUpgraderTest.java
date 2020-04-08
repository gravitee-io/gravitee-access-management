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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.management.service.impl.upgrades.helpers.MembershipHelper;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.management.service.impl.upgrades.DefaultRoleUpgrader.PAGE_SIZE;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultRoleUpgraderTest {

    @Mock
    private RoleService roleService;

    @Mock
    private UserService userService;

    @Mock
    private MembershipHelper membershipHelper;

    private DefaultRoleUpgrader cut;

    @Before
    public void before() {

        cut = new DefaultRoleUpgrader(roleService, userService, membershipHelper);
    }

    @Test
    public void shouldCreateSystemRoles() {

        when(roleService.createOrUpdateSystemRoles()).thenReturn(Completable.complete());
        when(roleService.findDefaultRole(Organization.DEFAULT, DefaultRole.ORGANIZATION_OWNER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(new Role()));

        cut.upgrade();
    }

    @Test
    public void shouldCreateSystemRoles_setOwnerRoleToExistingUsers() {

        int totalUsers = 22;

        User user = new User();
        user.setId("user-1");

        Role adminRole = new Role();
        adminRole.setId("role-1");

        List<User> users = Stream.iterate(0, i -> i++).limit(PAGE_SIZE).map(i -> user)
                .collect(Collectors.toList());

        when(roleService.createOrUpdateSystemRoles()).thenReturn(Completable.complete());

        when(roleService.findDefaultRole(Organization.DEFAULT, DefaultRole.ORGANIZATION_OWNER, ReferenceType.ORGANIZATION))
                .thenReturn(Maybe.empty()) // Role does not exist at start.
                .thenReturn(Maybe.just(adminRole)); // Role has been created.

        when(userService.findAll(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(0), anyInt()))
                .thenReturn(Single.just(new Page<>(users, 0, totalUsers)));

        when(userService.findAll(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(1), anyInt()))
                .thenReturn(Single.just(new Page<>(users, 1, totalUsers)));

        when(userService.findAll(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(2), anyInt()))
                .thenReturn(Single.just(new Page<>(Arrays.asList(user, user), 2, totalUsers)));

        doNothing().when(membershipHelper).setRole(eq(user), eq(adminRole));

        cut.upgrade();

        verify(membershipHelper, times(totalUsers)).setRole(eq(user), eq(adminRole));
    }

    @Test
    public void shouldCreateSystemRoles_technicalError() {

        when(roleService.createOrUpdateSystemRoles()).thenReturn(Completable.error(TechnicalException::new));
        when(roleService.findDefaultRole(Organization.DEFAULT, DefaultRole.ORGANIZATION_OWNER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.empty());

        assertFalse(cut.upgrade());
    }
}