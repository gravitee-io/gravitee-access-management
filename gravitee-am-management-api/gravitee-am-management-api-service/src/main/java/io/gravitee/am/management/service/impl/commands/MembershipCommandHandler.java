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

import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.InvalidRoleException;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.membership.MembershipCommand;
import io.gravitee.cockpit.api.command.v1.membership.MembershipCommandPayload;
import io.gravitee.cockpit.api.command.v1.membership.MembershipReply;
import io.gravitee.exchange.api.command.CommandHandler;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static io.gravitee.am.management.service.impl.commands.UserCommandHandler.COCKPIT_SOURCE;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipCommandHandler implements CommandHandler<MembershipCommand, MembershipReply> {

    private final OrganizationUserService userService;
    private final RoleService roleService;
    private final MembershipService membershipService;

    @Override
    public String supportType() {
        return CockpitCommandType.MEMBERSHIP.name();
    }

    @Override
    public Single<MembershipReply> handle(MembershipCommand command) {

        MembershipCommandPayload membershipPayload = command.getPayload();
        ReferenceType assignableType;

        try {
            assignableType = ReferenceType.valueOf(membershipPayload.referenceType());
        } catch (Exception e) {
            log.error("Invalid referenceType [{}].", membershipPayload.referenceType());
            return Single.just(new MembershipReply(command.getId(), e.getMessage()));
        }

        Single<String> userObs = userService.findByExternalIdAndSource(ReferenceType.ORGANIZATION, membershipPayload.organizationId(), membershipPayload.userId(), COCKPIT_SOURCE)
                .map(User::getId).toSingle();
        Single<Role> roleObs = findRole(membershipPayload.role(), membershipPayload.organizationId(), assignableType);

        return Single.zip(roleObs, userObs,
                        (role, userId) -> {
                            Membership membership = new Membership();
                            membership.setMemberType(MemberType.USER);
                            membership.setMemberId(userId);
                            membership.setReferenceType(assignableType);
                            membership.setReferenceId(membershipPayload.referenceId());
                            membership.setRoleId(role.getId());

                            return membership;
                        })
                .flatMap(membership -> membershipService.addOrUpdate(membershipPayload.organizationId(), membership))
                .doOnSuccess(membership -> log.info("Role [{}] assigned on {} [{}] for user [{}] and organization [{}].", membershipPayload.role(), membershipPayload.referenceType(), membershipPayload.referenceId(), membership.getMemberId(), membershipPayload.organizationId()))
                .map(user -> new MembershipReply(command.getId()))
                .doOnError(error -> log.error("Error occurred when trying to assign role [{}] on {} [{}] for cockpit user [{}] and organization [{}].", membershipPayload.role(), membershipPayload.referenceType(), membershipPayload.referenceId(), membershipPayload.userId(), membershipPayload.organizationId(), error))
                .onErrorReturn(throwable -> new MembershipReply(command.getId(), throwable.getMessage()));
    }


    private Single<Role> findRole(String roleName, String organizationId, ReferenceType assignableType) {

        SystemRole systemRole = SystemRole.fromName(roleName);

        // First try to map to a system role.
        if (systemRole != null) {
            return roleService.findSystemRole(systemRole, assignableType).toSingle();
        } else {
            // Then try to find a default role.
            DefaultRole defaultRole = DefaultRole.fromName(roleName);

            if (defaultRole != null) {
                return roleService.findDefaultRole(organizationId, defaultRole, assignableType).toSingle();
            }
        }

        return Single.error(new InvalidRoleException(String.format("Unable to find role [%s] for organization [%s].", roleName, organizationId)));
    }
}
