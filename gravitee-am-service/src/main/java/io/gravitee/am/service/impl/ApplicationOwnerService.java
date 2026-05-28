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
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApplicationOwnerService {

    @Lazy
    @Autowired
    private OrganizationUserRepository organizationUserRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MembershipService membershipService;


    public Maybe<List<String>> retrieveOwnerApplicationIds(String ownerEmail, String organizationId) {
        return organizationUserRepository.findByEmail(organizationId, ownerEmail)
                .firstElement()
                .flatMap(user ->
                        roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)
                                .flatMap(role -> membershipService
                                        .findByCriteria(ReferenceType.APPLICATION, new MembershipCriteria(user.getId()).setRoleId(role.getId()))
                                        .map(Membership::getReferenceId)
                                        .toList()
                                        .toMaybe())
                );
    }
}
