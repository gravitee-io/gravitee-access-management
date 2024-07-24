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
package io.gravitee.am.gateway.handler.common.role.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.RoleEvent;
import io.gravitee.am.gateway.handler.common.role.RoleFacade;
import io.gravitee.am.gateway.handler.common.role.RoleManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
public class InMemoryRoleManagerImpl extends AbstractService implements RoleFacade, InitializingBean, RoleManager, EventListener<RoleEvent, Payload> {

    private final ConcurrentMap<String, Role> roles = new ConcurrentHashMap<>();

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public void afterPropertiesSet() throws Exception {
        roleRepository.findAll(ReferenceType.DOMAIN, domain.getId()).subscribe(
                this::updateRole,
                error -> log.error("Unable to initialize factors for domain {}", domain.getName(), error));
    }

    @Override
    public void onEvent(Event<RoleEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN &&
                domain.getId().equals(event.content().getReferenceId())) {
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
    protected void doStart() throws Exception {
        super.doStart();

        log.info("Register event listener for role events.");
        eventManager.subscribeForEvents(this, RoleEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        log.info("Dispose event listener for role events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, RoleEvent.class, domain.getId());
    }

    @Override
    public Single<Set<Role>> findByIdIn(List<String> rolesIds) {
        return Single.just(rolesIds.stream().map(roles::get).filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    private void updateRole(String roleId, RoleEvent roleEvent) {
        final String eventType = roleEvent.toString().toLowerCase();
        log.info("Domain {} has received {} role event for {}", domain.getName(), eventType, roleId);
        roleRepository.findById(roleId)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a role using its ID: {}", roleId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a role using its ID: %s", roleId), ex));
                }).subscribe(
                        this::updateRole,
                        error -> log.error("Unable to load role for domain {}", domain.getName(), error),
                        () -> log.error("No role found with id {}", roleId));
    }

    private void updateRole(Role role) {
        if (needDeployment(role)) {
            this.roles.put(role.getId(), role);
            log.info("Role {} has been loaded for domain {}", role.getId(), domain.getName());
        } else {
            log.info("Role {} has been already loaded for domain {}", role.getId(), domain.getName());
        }
    }

    private boolean needDeployment(Role role) {
        final Role deployedRole = this.roles.get(role.getId());
        return (deployedRole == null || deployedRole.getUpdatedAt().before(role.getUpdatedAt()));
    }


    private void removeRole(String roleId) {
        log.info("Domain {} has received form event, remove role {}", domain.getName(), roleId);
        roles.remove(roleId);
    }


}
