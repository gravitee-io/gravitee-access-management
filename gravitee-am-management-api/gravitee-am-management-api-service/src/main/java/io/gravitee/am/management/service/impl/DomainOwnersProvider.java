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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.gravitee.am.model.UserId.internal;

@Component
public class DomainOwnersProvider {

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private OrganizationUserService userService;

    @Autowired
    private OrganizationGroupService organizationGroupService;


    public Flowable<User> retrieveDomainOwners(Domain domain) {
        return findEnvironment(domain).flatMapPublisher(env -> Maybe.concat(
                        roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, ReferenceType.DOMAIN),
                        roleService.findDefaultRole(env.getOrganizationId(), DefaultRole.DOMAIN_OWNER, ReferenceType.DOMAIN)
                ).map(Role::getId)
                .flatMap(roleId -> {
                    final MembershipCriteria criteria = new MembershipCriteria();
                    criteria.setRoleId(roleId);
                    return membershipService.findByCriteria(ReferenceType.DOMAIN, domain.getId(), criteria);
                }).flatMap(membership -> {
                    if (membership.getMemberType() == MemberType.USER) {
                        return userService.findById(Reference.organization(env.getOrganizationId()), internal(membership.getMemberId())).toFlowable();
                    } else {
                        return readUsersFromAnOrganizationGroup(env.getOrganizationId(), membership.getMemberId(), 0, 10);
                    }
                }));
    }

    private Flowable<User> readUsersFromAnOrganizationGroup(String organizationId, String memberId, int pageIndex, int size) {
        return organizationGroupService.findMembers(organizationId, memberId, pageIndex, size)
                .flatMapPublisher(page -> {
                    if (page.getTotalCount() == 0) {
                        return Flowable.empty();
                    }

                    if (page.getData().size() < 10) {
                        return Flowable.fromIterable(page.getData());
                    } else {
                        return Flowable.concat(Flowable.fromIterable(page.getData()), readUsersFromAnOrganizationGroup(organizationId, memberId, pageIndex + 1, size));
                    }
                });
    }

    private Single<Environment> findEnvironment(Domain domain) {
        return environmentService.findById(domain.getReferenceId());
    }
}
