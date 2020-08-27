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
package io.gravitee.am.management.service.impl.commands;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.membership.MembershipCommand;
import io.gravitee.cockpit.api.command.membership.MembershipPayload;
import io.gravitee.cockpit.api.command.membership.MembershipReply;
import io.gravitee.common.utils.UUID;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MembershipCommandHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private RoleService roleService;

    @Mock
    private MembershipService membershipService;

    public MembershipCommandHandler cut;

    @Before
    public void before() {
        cut = new MembershipCommandHandler(userService, roleService, membershipService);
    }

    @Test
    public void handleType() {
        assertEquals(Command.Type.MEMBERSHIP_COMMAND, cut.handleType());
    }

    @Test
    public void handleWithSystemRole() {

        MembershipPayload membershipPayload = new MembershipPayload();
        membershipPayload.setUserId("user#1");
        membershipPayload.setOrganizationId("orga#1");
        membershipPayload.setReferenceType(ReferenceType.ENVIRONMENT.name());
        membershipPayload.setReferenceId("env#1");
        membershipPayload.setRole(SystemRole.ENVIRONMENT_PRIMARY_OWNER.name());

        MembershipCommand command = new MembershipCommand(membershipPayload);

        User user = new User();
        user.setId(UUID.random().toString());

        Role role = new Role();
        role.setId(UUID.random().toString());

        when(userService.findByExternalIdAndSource(ReferenceType.ORGANIZATION, membershipPayload.getOrganizationId(), membershipPayload.getUserId(), "cockpit")).thenReturn(Maybe.just(user));
        when(roleService.findSystemRole(SystemRole.ENVIRONMENT_PRIMARY_OWNER, ReferenceType.ENVIRONMENT)).thenReturn(Maybe.just(role));
        when(membershipService.addOrUpdate(eq(membershipPayload.getOrganizationId()),
                argThat(membership -> membership.getReferenceType() == ReferenceType.ENVIRONMENT
                        && membership.getReferenceId().equals(membershipPayload.getReferenceId())
                        && membership.getMemberType() == MemberType.USER
                        && membership.getMemberId().equals(user.getId())
                        && membership.getRoleId().equals(role.getId()))))
                .thenAnswer(i -> Single.just(i.getArgument(1)));

        TestObserver<MembershipReply> obs = cut.handle(command).test();

        obs.awaitTerminalEvent();
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
    }

    @Test
    public void handleWithDefaultRole() {

        MembershipPayload membershipPayload = new MembershipPayload();
        membershipPayload.setUserId("user#1");
        membershipPayload.setOrganizationId("orga#1");
        membershipPayload.setReferenceType(ReferenceType.ENVIRONMENT.name());
        membershipPayload.setReferenceId("env#1");
        membershipPayload.setRole(DefaultRole.ENVIRONMENT_OWNER.name());

        MembershipCommand command = new MembershipCommand(membershipPayload);

        User user = new User();
        user.setId(UUID.random().toString());

        Role role = new Role();
        role.setId(UUID.random().toString());

        when(userService.findByExternalIdAndSource(ReferenceType.ORGANIZATION, membershipPayload.getOrganizationId(), membershipPayload.getUserId(), "cockpit")).thenReturn(Maybe.just(user));
        when(roleService.findDefaultRole(membershipPayload.getOrganizationId(), DefaultRole.ENVIRONMENT_OWNER, ReferenceType.ENVIRONMENT)).thenReturn(Maybe.just(role));
        when(membershipService.addOrUpdate(eq(membershipPayload.getOrganizationId()),
                argThat(membership -> membership.getReferenceType() == ReferenceType.ENVIRONMENT
                        && membership.getReferenceId().equals(membershipPayload.getReferenceId())
                        && membership.getMemberType() == MemberType.USER
                        && membership.getMemberId().equals(user.getId())
                        && membership.getRoleId().equals(role.getId()))))
                .thenAnswer(i -> Single.just(i.getArgument(1)));

        TestObserver<MembershipReply> obs = cut.handle(command).test();

        obs.awaitTerminalEvent();
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
    }

    @Test
    public void handleWithUnknownRole() {

        MembershipPayload membershipPayload = new MembershipPayload();
        membershipPayload.setUserId("user#1");
        membershipPayload.setOrganizationId("orga#1");
        membershipPayload.setReferenceType(ReferenceType.ENVIRONMENT.name());
        membershipPayload.setReferenceId("env#1");
        membershipPayload.setRole("UNKNOWN");

        MembershipCommand command = new MembershipCommand(membershipPayload);

        User user = new User();
        user.setId(UUID.random().toString());

        Role role = new Role();
        role.setId(UUID.random().toString());

        // Need to switch to lenient because we can be sure of what method will be executed (cause it's reactive and executed in //).
        lenient().when(userService.findByExternalIdAndSource(ReferenceType.ORGANIZATION, membershipPayload.getOrganizationId(), membershipPayload.getUserId(), "cockpit")).thenReturn(Maybe.just(user));
        lenient().when(roleService.findDefaultRole(membershipPayload.getOrganizationId(), DefaultRole.ENVIRONMENT_OWNER, ReferenceType.ENVIRONMENT)).thenReturn(Maybe.just(role));

        TestObserver<MembershipReply> obs = cut.handle(command).test();

        obs.awaitTerminalEvent();
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));

        verifyZeroInteractions(membershipService);
    }

}