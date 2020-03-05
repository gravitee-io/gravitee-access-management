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
package io.gravitee.am.management.handlers.management.api.manager.role.impl;

import io.gravitee.am.common.event.RoleEvent;
import io.gravitee.am.management.handlers.management.api.manager.role.RoleManager;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RoleManagerImpl implements RoleManager, InitializingBean, EventListener<RoleEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(RoleManagerImpl.class);
    private static final String ADMIN_DOMAIN = "admin";
    private Optional<Role> adminRole;
    private ConcurrentMap<String, Role> roles = new ConcurrentHashMap<>();

    @Autowired
    private RoleService roleService;

    @Autowired
    private EventManager eventManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Register event listener for role events for the management API");
        eventManager.subscribeForEvents(this, RoleEvent.class);

        logger.info("Initializing management roles");
        initRoles();
    }

    @Override
    public void onEvent(Event<RoleEvent, Payload> event) {
        if (event.content().getDomain() == null) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateRole(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeRole(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public Set<Role> findByIdIn(List<String> roles) {
        return this.roles.entrySet().stream().filter(e -> roles.contains(e.getKey())).map(Map.Entry::getValue).collect(Collectors.toSet());
    }

    @Override
    public boolean isAdminRoleGranted(List<String> userRoles) {

        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        if (adminRole == null) {
            adminRole = roles.values().stream().filter(r -> RoleScope.MANAGEMENT.getId() == r.getScope() && SystemRole.ADMIN.name().equals(r.getName())).findFirst();
        }

        return adminRole.isPresent() && userRoles.contains(adminRole.get().getId());
    }

    @Override
    public boolean hasPermission(List<String> userRoles, RolePermission rolePermission, RolePermissionAction action) {
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        return roles.values()
                .stream()
                .filter(r -> rolePermission.getScope().getId() == r.getScope()
                        && r.getPermissions() != null && r.getPermissions().contains(rolePermission.getPermission().getMask() + "_" + action.getMask()))
                .findFirst()
                .map(role -> userRoles.contains(role.getId()))
                .orElse(false);
    }

    private void updateRole(String roleId, RoleEvent roleEvent) {
        final String eventType = roleEvent.toString().toLowerCase();
        logger.info("Management API has received {} role event for {}", eventType, roleId);
        roleService.findById(roleId)
                .subscribe(
                        role -> {
                            roles.put(role.getId(), role);
                            logger.info("Role {} loaded", role.getName(), eventType);
                        },
                        error -> logger.error("Unable to {} role", eventType, error),
                        () -> logger.error("No role found with id {}", roleId));
    }

    private void removeRole(String roleId) {
        logger.info("Management API has received delete role event, remove role {}", roleId);
        roles.remove(roleId);
    }

    private void initRoles() {
        // default system roles should at least exist at startup, repeat until we have them
        // FIXME : try a proper way to get system roles.
        roleService.findAllSystem()
                .map(roles -> {
                    if (roles == null || roles.size() < 3) {
                        throw new RoleNotFoundException("Management roles");
                    }
                    return roles;
                })
                .retryWhen(t -> t.take(30).delay(1000, TimeUnit.MILLISECONDS))
                .subscribe(
                        managementRoles -> managementRoles.forEach(r -> roles.put(r.getId(), r)),
                        ex -> logger.error("An error occurs while loading management roles", ex));
    }
}
