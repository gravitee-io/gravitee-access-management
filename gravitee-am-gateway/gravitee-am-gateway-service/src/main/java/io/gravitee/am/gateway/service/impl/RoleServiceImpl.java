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
package io.gravitee.am.gateway.service.impl;

import io.gravitee.am.gateway.service.RoleService;
import io.gravitee.am.gateway.service.exception.CertificateNotFoundException;
import io.gravitee.am.gateway.service.exception.RoleAlreadyExistsException;
import io.gravitee.am.gateway.service.exception.RoleNotFoundException;
import io.gravitee.am.gateway.service.exception.TechnicalManagementException;
import io.gravitee.am.gateway.service.model.NewRole;
import io.gravitee.am.gateway.service.model.UpdateRole;
import io.gravitee.am.model.Role;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.common.utils.UUID;
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
    public Set<Role> findByDomain(String domain) {
        try {
            LOGGER.debug("Find roles by domain: {}", domain);
            return roleRepository.findByDomain(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find roles by domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to find roles by domain", ex);
        }
    }

    @Override
    public Role findById(String id) {
        try {
            LOGGER.debug("Find role by ID: {}", id);
            Optional<Role> roleOpt = roleRepository.findById(id);

            if (!roleOpt.isPresent()) {
                throw new RoleNotFoundException(id);
            }

            return roleOpt.get();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find a role using its ID: {}", id, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find a role using its ID: %s", id), ex);
        }
    }

    @Override
    public Set<Role> findByIdIn(List<String> ids) {
        try {
            LOGGER.debug("Find roles by ids: {}", ids);
            return roleRepository.findByIdIn(ids);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find roles by ids", ex);
            throw new TechnicalManagementException("An error occurs while trying to find roles by ids", ex);
        }
    }


    @Override
    public Role create(String domain, NewRole newRole) {
        try {
            LOGGER.debug("Create a new role {} for domain {}", newRole, domain);

            String roleId = UUID.toString(UUID.random());

            // check if role name is unique
            checkRoleUniqueness(newRole.getName(), roleId, domain);

            Role role = new Role();
            role.setId(roleId);
            role.setDomain(domain);
            role.setName(newRole.getName());
            role.setDescription(newRole.getDescription());
            role.setCreatedAt(new Date());
            role.setUpdatedAt(role.getCreatedAt());
            return roleRepository.create(role);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to create a role", ex);
            throw new TechnicalManagementException("An error occurs while trying to create a role", ex);
        }
    }

    @Override
    public Role update(String domain, String id, UpdateRole updateRole) {
        try {
            LOGGER.debug("Update a role {} for domain {}", id, domain);

            Optional<Role> roleOpt = roleRepository.findById(id);
            if (!roleOpt.isPresent()) {
                throw new RoleNotFoundException(id);
            }

            Role oldRole = roleOpt.get();

            // check if role name is unique
            checkRoleUniqueness(updateRole.getName(), oldRole.getId(), domain);

            oldRole.setName(updateRole.getName());
            oldRole.setDescription(updateRole.getDescription());
            oldRole.setPermissions(updateRole.getPermissions());
            oldRole.setUpdatedAt(new Date());

            Role role = roleRepository.update(oldRole);

            return role;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update a role", ex);
            throw new TechnicalManagementException("An error occurs while trying to update a role", ex);
        }
    }

    @Override
    public void delete(String roleId) {
        try {
            LOGGER.debug("Delete role {}", roleId);

            Optional<Role> optRole = roleRepository.findById(roleId);
            if (! optRole.isPresent()) {
                throw new CertificateNotFoundException(roleId);
            }

            roleRepository.delete(roleId);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to delete role: {}", roleId, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to delete role: %s", roleId), ex);
        }
    }

    private void checkRoleUniqueness(String roleName, String roleId, String domain) throws TechnicalException {

        if (roleRepository.findByDomain(domain).stream()
                .filter(role -> !role.getId().equals(roleId))
                .anyMatch(role -> role.getName().equals(roleName))) {
            throw new RoleAlreadyExistsException(roleName);
        }
    }

}
