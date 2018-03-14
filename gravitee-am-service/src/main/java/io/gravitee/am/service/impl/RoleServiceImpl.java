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

import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.Role;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.RoleAlreadyExistsException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.am.service.model.UpdateRole;
import io.gravitee.common.utils.UUID;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class RoleServiceImpl implements RoleService {

    private final Logger LOGGER = LoggerFactory.getLogger(RoleServiceImpl.class);

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public Single<Set<Role>> findByDomain(String domain) {
        LOGGER.debug("Find roles by domain: {}", domain);
        return roleRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find roles by domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find roles by domain", ex));
                });
    }

    @Override
    public Maybe<Role> findById(String id) {
        LOGGER.debug("Find role by ID: {}", id);
        return roleRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a role using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a role using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<Set<Role>> findByIdIn(List<String> ids) {
        LOGGER.debug("Find roles by ids: {}", ids);
        return roleRepository.findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find roles by ids", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find roles by ids", ex));
                });
    }


    @Override
    public Single<Role> create(String domain, NewRole newRole) {
        LOGGER.debug("Create a new role {} for domain {}", newRole, domain);

        String roleId = UUID.toString(UUID.random());

        // check if role name is unique
        return checkRoleUniqueness(newRole.getName(), roleId, domain)
                .flatMap(irrelevant -> {
                    Role role = new Role();
                    role.setId(roleId);
                    role.setDomain(domain);
                    role.setName(newRole.getName());
                    role.setDescription(newRole.getDescription());
                    role.setCreatedAt(new Date());
                    role.setUpdatedAt(role.getCreatedAt());
                    return roleRepository.create(role);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a role", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a role", ex));
                });
    }

    @Override
    public Single<Role> update(String domain, String id, UpdateRole updateRole) {
        LOGGER.debug("Update a role {} for domain {}", id, domain);

        return roleRepository.findById(id)
                .map(role -> Optional.of(role))
                .defaultIfEmpty(Optional.empty())
                .toSingle()
                .flatMap(roleOpt -> {
                    if (!roleOpt.isPresent()) {
                        throw new RoleNotFoundException(id);
                    }
                    return Single.just(roleOpt.get());
                })
                .flatMap(oldRole -> {
                    // check if role name is unique
                    return checkRoleUniqueness(updateRole.getName(), oldRole.getId(), domain)
                            .flatMap(irrelevant -> {
                                oldRole.setName(updateRole.getName());
                                oldRole.setDescription(updateRole.getDescription());
                                oldRole.setPermissions(updateRole.getPermissions());
                                oldRole.setUpdatedAt(new Date());
                                return roleRepository.update(oldRole);
                            });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a role", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a role", ex));
                });

    }

    @Override
    public Single<Irrelevant> delete(String roleId) {
        LOGGER.debug("Delete role {}", roleId);
        return roleRepository.findById(roleId)
                .isEmpty()
                    .flatMap(empty -> {
                        if (empty) {
                            throw new RoleNotFoundException(roleId);
                        }
                        return roleRepository.delete(roleId);
                    })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete role: {}", roleId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete role: %s", roleId), ex));
                });
    }

    private Single<Irrelevant> checkRoleUniqueness(String roleName, String roleId, String domain) {

        return roleRepository.findByDomain(domain)
                .flatMap(roles -> {
                    if (roles.stream()
                            .filter(role -> !role.getId().equals(roleId))
                            .anyMatch(role -> role.getName().equals(roleName))) {
                        throw new RoleAlreadyExistsException(roleName);
                    }
                    return Single.just(Irrelevant.ROLE);
                });
    }

}
