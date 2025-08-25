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
package io.gravitee.am.service.impl.user;

import io.gravitee.am.model.Group;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class BaseUserEnhancer implements UserEnhancer {
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());
    protected abstract Flowable<Group> getGroupsByMemberId(String memberId);
    protected abstract Flowable<Role> getRolesByIds(List<String> roleIds);
    protected abstract Flowable<Group> getGroupsByIds(List<String> groupIds);

    private Flowable<Group> collectGroups(User user) {
        Flowable<Group> groupsByMemberId = getGroupsByMemberId(user.getId());
        Flowable<Group> groupsByIds = getGroupsByIds(user.getDynamicGroups() == null ? List.of() : user.getDynamicGroups());
        return groupsByMemberId.mergeWith(groupsByIds);
    }

    public Single<User> enhance(User user) {
        LOGGER.debug("Enhancing user {}", user);
        return collectGroups(user)
                .toList()
                .map(HashSet::new)
                .flatMap(groups -> {
                    Set<String> roles = new HashSet<>();
                    if (groups != null && !groups.isEmpty()) {
                        // set groups
                        user.setGroups(groups.stream().map(Group::getName).collect(Collectors.toList()));
                        // set groups roles
                        roles.addAll(groups
                                .stream()
                                .filter(group -> group.getRoles() != null && !group.getRoles().isEmpty())
                                .flatMap(group -> group.getRoles().stream())
                                .collect(Collectors.toSet()));
                    }

                    // get user roles
                    if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                        LOGGER.debug("Adding roles to user: {}", user.getRoles());
                        roles.addAll(user.getRoles());
                    }
                    if (user.getDynamicRoles() != null && !user.getDynamicRoles().isEmpty()) {
                        LOGGER.debug("Adding dynamic roles to user: {}", user.getDynamicRoles());
                        roles.addAll(user.getDynamicRoles());
                    }

                    // fetch roles information and enhance user data
                    if (!roles.isEmpty()) {
                        return getRolesByIds(new ArrayList<>(roles))
                                .toList()
                                .map(foundRoles -> {
                                    user.setRolesPermissions(new HashSet<>(foundRoles));
                                    return user;
                                });
                    }
                    return Single.just(user);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to enhance user {}", user.getId(), ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to enhance user %s", user.getId()), ex));
                });
    }
}
