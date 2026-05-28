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

import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationOwnerServiceTest {

    private static final String ORGANIZATION_ID = "DEFAULT";
    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String USER_ID = "user-1";
    private static final String ROLE_ID = "role-app-primary-owner";

    @InjectMocks
    private final ApplicationOwnerService service = new ApplicationOwnerService();

    @Mock
    private OrganizationUserRepository organizationUserRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private MembershipService membershipService;

    private User owner(String id) {
        final User user = new User();
        user.setId(id);
        user.setEmail(OWNER_EMAIL);
        return user;
    }

    private Role primaryOwnerRole() {
        final Role role = new Role();
        role.setId(ROLE_ID);
        role.setName(SystemRole.APPLICATION_PRIMARY_OWNER.name());
        return role;
    }

    private Membership membership(String applicationId) {
        final Membership m = new Membership();
        m.setReferenceType(ReferenceType.APPLICATION);
        m.setReferenceId(applicationId);
        m.setRoleId(ROLE_ID);
        m.setMemberId(USER_ID);
        return m;
    }

    @Test
    public void retrieveOwnerApplicationIds_returnsApplicationIdsForPrimaryOwnerMemberships() {
        when(organizationUserRepository.findByEmail(ORGANIZATION_ID, OWNER_EMAIL)).thenReturn(Flowable.just(owner(USER_ID)));
        when(roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)).thenReturn(Maybe.just(primaryOwnerRole()));
        when(membershipService.findByCriteria(eq(ReferenceType.APPLICATION), any(MembershipCriteria.class)))
                .thenReturn(Flowable.just(membership("app-1"), membership("app-2"), membership("app-3")));

        final TestObserver<List<String>> observer = service.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION_ID).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValue(List.of("app-1", "app-2", "app-3"));
    }

    @Test
    public void retrieveOwnerApplicationIds_buildsMembershipCriteriaWithUserAndRoleId() {
        when(organizationUserRepository.findByEmail(ORGANIZATION_ID, OWNER_EMAIL)).thenReturn(Flowable.just(owner(USER_ID)));
        when(roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)).thenReturn(Maybe.just(primaryOwnerRole()));
        when(membershipService.findByCriteria(eq(ReferenceType.APPLICATION), any(MembershipCriteria.class)))
                .thenReturn(Flowable.empty());

        service.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION_ID).test().awaitDone(5, TimeUnit.SECONDS);

        final ArgumentCaptor<MembershipCriteria> captor = ArgumentCaptor.forClass(MembershipCriteria.class);
        verify(membershipService).findByCriteria(eq(ReferenceType.APPLICATION), captor.capture());
        final MembershipCriteria criteria = captor.getValue();
        assertNotNull(criteria);
        assertEquals(USER_ID, criteria.getUserId().orElseThrow());
        assertEquals(ROLE_ID, criteria.getRoleId().orElseThrow());
    }

    @Test
    public void retrieveOwnerApplicationIds_picksFirstUserWhenMultipleMatchEmail() {
        when(organizationUserRepository.findByEmail(ORGANIZATION_ID, OWNER_EMAIL))
                .thenReturn(Flowable.just(owner("user-1"), owner("user-2")));
        when(roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)).thenReturn(Maybe.just(primaryOwnerRole()));
        when(membershipService.findByCriteria(eq(ReferenceType.APPLICATION), any(MembershipCriteria.class)))
                .thenReturn(Flowable.just(membership("app-1")));

        service.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION_ID).test().awaitDone(5, TimeUnit.SECONDS);

        final ArgumentCaptor<MembershipCriteria> captor = ArgumentCaptor.forClass(MembershipCriteria.class);
        verify(membershipService).findByCriteria(eq(ReferenceType.APPLICATION), captor.capture());
        assertEquals("user-1", captor.getValue().getUserId().orElseThrow());
    }

    @Test
    public void retrieveOwnerApplicationIds_noUserFound_returnsEmptyMaybe() {
        when(organizationUserRepository.findByEmail(ORGANIZATION_ID, OWNER_EMAIL)).thenReturn(Flowable.empty());

        final TestObserver<List<String>> observer = service.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION_ID).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertNoValues();
        verify(roleService, never()).findSystemRole(any(SystemRole.class), any(ReferenceType.class));
        verify(membershipService, never()).findByCriteria(any(ReferenceType.class), any(MembershipCriteria.class));
    }

    @Test
    public void retrieveOwnerApplicationIds_noSystemRole_returnsEmptyMaybe() {
        when(organizationUserRepository.findByEmail(ORGANIZATION_ID, OWNER_EMAIL)).thenReturn(Flowable.just(owner(USER_ID)));
        when(roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)).thenReturn(Maybe.empty());

        final TestObserver<List<String>> observer = service.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION_ID).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertNoValues();
        verify(membershipService, never()).findByCriteria(any(ReferenceType.class), any(MembershipCriteria.class));
    }

    @Test
    public void retrieveOwnerApplicationIds_noMemberships_returnsEmptyList() {
        when(organizationUserRepository.findByEmail(ORGANIZATION_ID, OWNER_EMAIL)).thenReturn(Flowable.just(owner(USER_ID)));
        when(roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)).thenReturn(Maybe.just(primaryOwnerRole()));
        when(membershipService.findByCriteria(eq(ReferenceType.APPLICATION), any(MembershipCriteria.class))).thenReturn(Flowable.empty());

        final TestObserver<List<String>> observer = service.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION_ID).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValue(List.of());
    }

    @Test
    public void retrieveOwnerApplicationIds_userLookupError_propagates() {
        final RuntimeException boom = new RuntimeException("boom");
        when(organizationUserRepository.findByEmail(ORGANIZATION_ID, OWNER_EMAIL)).thenReturn(Flowable.error(boom));

        final TestObserver<List<String>> observer = service.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION_ID).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertError(boom);
        verify(roleService, never()).findSystemRole(any(SystemRole.class), any(ReferenceType.class));
    }

    @Test
    public void retrieveOwnerApplicationIds_membershipLookupError_propagates() {
        final RuntimeException boom = new RuntimeException("boom");
        when(organizationUserRepository.findByEmail(ORGANIZATION_ID, OWNER_EMAIL)).thenReturn(Flowable.just(owner(USER_ID)));
        when(roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)).thenReturn(Maybe.just(primaryOwnerRole()));
        when(membershipService.findByCriteria(eq(ReferenceType.APPLICATION), any(MembershipCriteria.class))).thenReturn(Flowable.error(boom));

        final TestObserver<List<String>> observer = service.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION_ID).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertError(boom);
    }

    @Test
    public void retrieveOwnerApplicationIds_findByEmailIsCalledWithOrgAndEmail() {
        when(organizationUserRepository.findByEmail(anyString(), anyString())).thenReturn(Flowable.empty());

        service.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION_ID).test().awaitDone(5, TimeUnit.SECONDS);

        verify(organizationUserRepository).findByEmail(ORGANIZATION_ID, OWNER_EMAIL);
    }
}
