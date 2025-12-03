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
package io.gravitee.am.management.handlers.management.api.authentication.service;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.handlers.management.api.authentication.service.impl.AuthenticationServiceImpl;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.impl.user.UserEnhancer;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest {

    public static final String ORGANIZATION_ID = Organization.DEFAULT;
    public static final Role DEFAULT_ROLE = new Role();

    @InjectMocks
    private AuthenticationService authenticationService = new AuthenticationServiceImpl();

    @Mock
    private AuthenticationSuccessEvent eventMock;

    @Mock
    private Authentication authenticationMock;

    @Mock
    private DefaultUser userDetailsMock;

    @Mock
    private OrganizationUserService userServiceMock;

    @Mock
    private RoleService roleServiceMock;

    @Mock
    private MembershipService membershipServiceMock;

    @Mock
    private io.gravitee.am.model.User repositoryUserMock;

    @Mock
    private Membership membershipMock;

    @Mock
    private AuditService auditService;

    @Mock
    private UserEnhancer userEnhancer;

    @BeforeAll
    public static void beforeAll() {
        DEFAULT_ROLE.setId("id-organization-user-default");
        DEFAULT_ROLE.setName("organization-user-default");
    }

    @Test
    public void shouldCreateUser() {
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORGANIZATION_ID);

        when(authenticationMock.getPrincipal()).thenReturn(userDetailsMock);
        when(userServiceMock.findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null)).thenReturn(Maybe.empty());
        when(userServiceMock.findByUsernameAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null)).thenReturn(Maybe.empty());
        when(userServiceMock.create(any(io.gravitee.am.model.User.class))).thenReturn(Single.just(user));
        when(userServiceMock.setRoles(any(), any(io.gravitee.am.model.User.class))).thenReturn(Completable.complete());
        when(userEnhancer.enhance(any())).thenReturn(Single.just(user));

        authenticationService.onAuthenticationSuccess(authenticationMock);

        verify(userServiceMock, times(1)).findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null);
        verify(userServiceMock, times(1)).findByUsernameAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null);
        verify(userServiceMock, times(1)).create(any(io.gravitee.am.model.User.class));
        verify(userServiceMock, times(1)).setRoles(any(), any(io.gravitee.am.model.User.class));
        verify(userServiceMock, never()).update(any(io.gravitee.am.model.User.class));
    }

    @Test
    public void shouldUpdatedUser() {
        when(authenticationMock.getPrincipal()).thenReturn(userDetailsMock);
        when(userServiceMock.findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null)).thenReturn(Maybe.just(repositoryUserMock));
        when(userServiceMock.update(any(io.gravitee.am.model.User.class))).thenReturn(Single.just(new io.gravitee.am.model.User()));
        when(userEnhancer.enhance(any())).thenReturn(Single.just(new io.gravitee.am.model.User()));

        when(roleServiceMock.findDefaultRole(ORGANIZATION_ID, DefaultRole.ORGANIZATION_USER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(new Role()));
        when(repositoryUserMock.getId()).thenReturn("user-id");
        when(membershipMock.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);
        when(membershipServiceMock.findByMember("user-id", MemberType.USER)).thenReturn(Flowable.just(membershipMock));

        authenticationService.onAuthenticationSuccess(authenticationMock);

        verify(userServiceMock, times(1)).findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null);
        verify(userServiceMock, times(1)).update(any(io.gravitee.am.model.User.class));
        verify(membershipServiceMock, never()).addOrUpdate(any(String.class), any(Membership.class));
    }

    @Test
    public void should_update_role_assigned_by_roleMapper() {
        when(membershipMock.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);
        when(membershipMock.getRoleId()).thenReturn("organization-user-role-id");
        when(roleServiceMock.findDefaultRole(ORGANIZATION_ID, DefaultRole.ORGANIZATION_USER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(DEFAULT_ROLE));

        when(repositoryUserMock.getId()).thenReturn("user-id");
        when(repositoryUserMock.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);
        when(repositoryUserMock.getReferenceId()).thenReturn("organization-id");

        when(userDetailsMock.getRoles()).thenReturn(Collections.singletonList("organization-owner-role-id"));

        when(authenticationMock.getPrincipal()).thenReturn(userDetailsMock);

        when(userServiceMock.findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null)).thenReturn(Maybe.just(repositoryUserMock));
        when(userServiceMock.update(any(io.gravitee.am.model.User.class))).thenReturn(Single.just(new io.gravitee.am.model.User()));
        when(userEnhancer.enhance(any())).thenReturn(Single.just(new io.gravitee.am.model.User()));

        when(roleServiceMock.findById(ReferenceType.ORGANIZATION, "organization-id", "organization-owner-role-id")).thenReturn(Single.just(new Role()));

        when(membershipServiceMock.findByMember("user-id", MemberType.USER)).thenReturn(Flowable.just(membershipMock));
        when(membershipServiceMock.addOrUpdate(anyString(), any(Membership.class))).thenReturn(Single.just(new Membership()));

        authenticationService.onAuthenticationSuccess(authenticationMock);

        verify(userServiceMock, times(1)).findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null);
        verify(userServiceMock, times(1)).update(any(io.gravitee.am.model.User.class));
        verify(membershipServiceMock, times(1)).addOrUpdate(any(String.class), argThat(Membership::isFromRoleMapper));
    }

    @Test
    public void should_not_update_role_assigned_by_roleMapper_no_changes() {
        when(membershipMock.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);
        when(membershipMock.getRoleId()).thenReturn("organization-owner-role-id");
        when(roleServiceMock.findDefaultRole(ORGANIZATION_ID, DefaultRole.ORGANIZATION_USER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(DEFAULT_ROLE));

        when(repositoryUserMock.getId()).thenReturn("user-id");

        when(userDetailsMock.getRoles()).thenReturn(Collections.singletonList("organization-owner-role-id"));

        when(authenticationMock.getPrincipal()).thenReturn(userDetailsMock);

        when(userServiceMock.findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null)).thenReturn(Maybe.just(repositoryUserMock));
        when(userServiceMock.update(any(io.gravitee.am.model.User.class))).thenReturn(Single.just(new io.gravitee.am.model.User()));
        when(userEnhancer.enhance(any())).thenReturn(Single.just(new io.gravitee.am.model.User()));

        when(membershipServiceMock.findByMember("user-id", MemberType.USER)).thenReturn(Flowable.just(membershipMock));

        authenticationService.onAuthenticationSuccess(authenticationMock);

        verify(userServiceMock, times(1)).findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null);
        verify(userServiceMock, times(1)).update(any(io.gravitee.am.model.User.class));
        verify(membershipServiceMock, never()).addOrUpdate(any(String.class), any(Membership.class));
    }

    @Test
    public void should_update_role_with_defaultRole() {
        when(membershipMock.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);
        when(roleServiceMock.findDefaultRole(ORGANIZATION_ID, DefaultRole.ORGANIZATION_USER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(DEFAULT_ROLE));

        when(repositoryUserMock.getId()).thenReturn("user-id");
        when(repositoryUserMock.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);
        when(repositoryUserMock.getReferenceId()).thenReturn("organization-id");

        when(userDetailsMock.getRoles()).thenReturn(Collections.emptyList());

        when(authenticationMock.getPrincipal()).thenReturn(userDetailsMock);

        when(userServiceMock.findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null)).thenReturn(Maybe.just(repositoryUserMock));
        when(userServiceMock.update(any(io.gravitee.am.model.User.class))).thenReturn(Single.just(new io.gravitee.am.model.User()));
        when(userEnhancer.enhance(any())).thenReturn(Single.just(new io.gravitee.am.model.User()));

        when(roleServiceMock.findById(ReferenceType.ORGANIZATION, "organization-id", DEFAULT_ROLE.getId())).thenReturn(Single.just(DEFAULT_ROLE));

        when(membershipServiceMock.findByMember("user-id", MemberType.USER)).thenReturn(Flowable.just(membershipMock));
        when(membershipMock.isFromRoleMapper()).thenReturn(true);
        when(membershipServiceMock.addOrUpdate(anyString(), any(Membership.class))).thenReturn(Single.just(new Membership()));

        authenticationService.onAuthenticationSuccess(authenticationMock);

        verify(userServiceMock, times(1)).findByExternalIdAndSource(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userDetailsMock.getUsername(), null);
        verify(userServiceMock, times(1)).update(any(io.gravitee.am.model.User.class));
        verify(membershipServiceMock, times(1)).addOrUpdate(any(String.class), argThat(m -> !m.isFromRoleMapper() && m.getRoleId().equals(DEFAULT_ROLE.getId())));
    }
}
