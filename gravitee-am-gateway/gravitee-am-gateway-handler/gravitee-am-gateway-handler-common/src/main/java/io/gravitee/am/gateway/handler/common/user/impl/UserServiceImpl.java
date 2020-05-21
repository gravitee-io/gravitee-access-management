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
package io.gravitee.am.gateway.handler.common.user.impl;

import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.User;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.RoleService;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImpl implements UserService {

    @Autowired
    private io.gravitee.am.service.UserService userService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RoleService roleService;

    @Override
    public Maybe<User> findById(String id) {
        return userService.findById(id);
    }

    @Override
    public Maybe<User> findByDomainAndExternalIdAndSource(String domain, String externalId, String source) {
        return userService.findByDomainAndExternalIdAndSource(domain, externalId, source);
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source) {
        return userService.findByDomainAndUsernameAndSource(domain, username, source);
    }

    @Override
    public Single<List<User>> findByDomainAndEmail(String domain, String email, boolean strict) {
        return userService.findByDomainAndEmail(domain, email, strict);
    }

    @Override
    public Single<User> create(User user) {
        return userService.create(user);
    }

    @Override
    public Single<User> update(User user) {
        return userService.update(user);
    }

    @Override
    public Single<User> enhance(User user) {
        // fetch user groups
        return groupService.findByMember(user.getId())
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
                        roles.addAll(user.getRoles());
                    }
                    // fetch roles information and enhance user data
                    if (!roles.isEmpty()) {
                        return roleService.findByIdIn(new ArrayList<>(roles))
                                .map(roles1 -> {
                                    user.setRolesPermissions(roles1);
                                    return user;
                                });

                    }
                    return Single.just(user);
                });
    }
}
