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
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import io.gravitee.am.repository.management.api.CommonUserRepository.UpdateActions;
import java.util.Date;

import static io.gravitee.am.model.ReferenceType.ORGANIZATION;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class OrganizationUserServiceImpl extends AbstractUserService implements OrganizationUserService {

    @Lazy
    @Autowired
    private OrganizationUserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupService groupService;

    @Autowired
    protected MembershipService membershipService;

    @Override
    protected OrganizationUserRepository getUserRepository() {
        return this.userRepository;
    }

    public Completable setRoles(io.gravitee.am.model.User user) {
        return setRoles(null, user);
    }

    public Completable setRoles(io.gravitee.am.identityprovider.api.User principal, io.gravitee.am.model.User user) {

        final Maybe<Role> defaultRoleObs = roleService.findDefaultRole(user.getReferenceId(), DefaultRole.ORGANIZATION_USER, ORGANIZATION);
        Maybe<Role> roleObs = defaultRoleObs;

        if (principal != null && principal.getRoles() != null && !principal.getRoles().isEmpty()) {
            // We allow only one role in AM portal. Get the first (should not append).
            String roleId =  principal.getRoles().get(0);

            roleObs = roleService.findById(user.getReferenceType(), user.getReferenceId(), roleId)
                    .toMaybe()
                    .onErrorResumeNext(throwable -> {
                        if (throwable instanceof RoleNotFoundException) {
                            return roleService.findById(ReferenceType.PLATFORM, Platform.DEFAULT, roleId).toMaybe()
                                    .switchIfEmpty(defaultRoleObs)
                                    .onErrorResumeNext(exception -> defaultRoleObs);
                        } else {
                            return defaultRoleObs;
                        }
                    });
        }

        Membership membership = new Membership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(user.getId());
        membership.setReferenceType(user.getReferenceType());
        membership.setReferenceId(user.getReferenceId());

        return roleObs.switchIfEmpty(Maybe.error(new TechnicalManagementException(String.format("Cannot add user membership to organization %s. Unable to find ORGANIZATION_USER role", user.getReferenceId()))))
                .flatMapCompletable(role -> {
                    membership.setRoleId(role.getId());
                    return membershipService.addOrUpdate(user.getReferenceId(), membership).ignoreElement();
                });
    }

    @Override
    public Single<User> update(User user) {
        LOGGER.debug("Update a user {}", user);
        // updated date
        user.setUpdatedAt(new Date());
        return userValidator.validate(user).andThen(getUserRepository()
                .findByUsernameAndSource(ORGANIZATION, user.getReferenceId(), user.getUsername(), user.getSource())
                .switchIfEmpty(getUserRepository().findById(ORGANIZATION, user.getReferenceId(), user.getId()))
                        .switchIfEmpty(Single.error(new UserNotFoundException(user.getId())))
                .flatMap(oldUser -> {

                        user.setId(oldUser.getId());
                        user.setReferenceType(oldUser.getReferenceType());
                        user.setReferenceId(oldUser.getReferenceId());
                        if (user.getFirstName() != null) {
                            user.setDisplayName(user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : ""));
                        }
                        user.setSource(oldUser.getSource());
                        user.setInternal(oldUser.isInternal());
                        user.setUpdatedAt(new Date());
                        if (user.getLoginsCount() < oldUser.getLoginsCount()) {
                            user.setLoggedAt(oldUser.getLoggedAt());
                            user.setLoginsCount(oldUser.getLoginsCount());
                        }

                        return getUserRepository().update(user);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                }));
    }
}
