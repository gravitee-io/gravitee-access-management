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
package io.gravitee.am.management.service.impl.upgrades.helpers;

import io.gravitee.am.model.*;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MembershipHelper {

    private final MembershipService membershipService;
    private final RoleService roleService;

    public MembershipHelper(MembershipService membershipService,
                            RoleService roleService) {
        this.membershipService = membershipService;
        this.roleService = roleService;
    }

    /**
     * Helper method to set ORGANIZATION_PRIMARY_OWNER role to the specified user.
     * Note: if the user already has a role, nothing is done.
     *
     * @param user the user to define the role on.
     */
    public void setOrganizationPrimaryOwnerRole(User user) {

        Role adminRole = roleService.findSystemRole(SystemRole.ORGANIZATION_PRIMARY_OWNER, ReferenceType.ORGANIZATION).blockingGet();

        setOrganizationRole(user, adminRole);
    }

    /**
     * Helper method to set PLATFORM_ADMIN role to the primary owner of the default organization.
     * In a standalone installation, someone needs to be defined as platform admin to be able to access some features outside of the organization scope.
     * It seems that the primary owner of the default organization is the best choice we can make.
     * Note: if the user already has a role, nothing is done.
     */
    public void setPlatformAdminRole() {

        Role organizationPrimaryOwnerRole = roleService.findSystemRole(SystemRole.ORGANIZATION_PRIMARY_OWNER, ReferenceType.ORGANIZATION).blockingGet();

        MembershipCriteria criteria = new MembershipCriteria();
        criteria.setRoleId(organizationPrimaryOwnerRole.getId());
        Membership member = membershipService.findByCriteria(ReferenceType.ORGANIZATION, Organization.DEFAULT, criteria).filter(membership -> membership.getMemberType() == MemberType.USER).blockingFirst(null);

        if (member != null) {
            membershipService.setPlatformAdmin(member.getMemberId()).blockingGet();
        }
    }

    /**
     * Helper method to set specified organization role to the specified user.
     * Note: if the user already has a role, nothing is done.
     *
     * @param user the user to define the role on.
     */
    public void setOrganizationRole(User user, Role role) {

        MembershipCriteria criteria = new MembershipCriteria();
        criteria.setUserId(user.getId());
        Boolean alreadyHasMembership = membershipService.findByCriteria(ReferenceType.ORGANIZATION, Organization.DEFAULT, criteria).count().map(count -> count > 0).blockingGet();

        // If admin user already has a role on the default organization no need to do anything (either he is already admin, either someone decided to change his role).
        if (!alreadyHasMembership) {

            Membership membership = new Membership();
            membership.setRoleId(role.getId());
            membership.setMemberType(MemberType.USER);
            membership.setMemberId(user.getId());
            membership.setReferenceType(ReferenceType.ORGANIZATION);
            membership.setReferenceId(Organization.DEFAULT);

            membershipService.addOrUpdate(Organization.DEFAULT, membership).blockingGet();
        }
    }
}
