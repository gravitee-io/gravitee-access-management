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
package io.gravitee.am.management.handlers.management.api.manager.group.impl;

import io.gravitee.am.common.event.GroupEvent;
import io.gravitee.am.management.handlers.management.api.manager.group.GroupManager;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.GroupService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupManagerImpl implements GroupManager, InitializingBean, EventListener<GroupEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(GroupManagerImpl.class);
    private static final String ADMIN_DOMAIN = "admin";
    private ConcurrentMap<String, Group> groups = new ConcurrentHashMap<>();

    @Autowired
    private GroupService groupService;

    @Autowired
    private EventManager eventManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Register event listener for group events for the management API");
        eventManager.subscribeForEvents(this, GroupEvent.class);

        logger.info("Initializing management groups");
        try {
            List<Group> managementGroups = groupService.findByDomain(ADMIN_DOMAIN).blockingGet();
            if (managementGroups != null) {
                managementGroups.forEach(g -> groups.put(g.getId(), g));
            }
        } catch (Exception ex) {
            logger.error("An error occurs while loading management groups", ex);
        }
    }

    @Override
    public void onEvent(Event<GroupEvent, Payload> event) {
        if (ADMIN_DOMAIN.equals(event.content().getDomain())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateGroup(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeGroup(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public List<Group> findByMember(String memberId) {
        return groups.values().stream().filter(g -> g.getMembers() != null && g.getMembers().contains(memberId)).collect(Collectors.toList());
    }

    private void updateGroup(String groupId, GroupEvent groupEvent) {
        final String eventType = groupEvent.toString().toLowerCase();
        logger.info("Management API has received {} group event for {}", eventType, groupId);
        groupService.findById(groupId)
                .subscribe(
                        group -> {
                            groups.put(group.getId(), group);
                            logger.info("Group {} {}d", groupId, eventType);
                        },
                        error -> logger.error("Unable to {} group", eventType, error),
                        () -> logger.error("No group found with id {}", groupId));
    }

    private void removeGroup(String groupId) {
        logger.info("Management API has received group event, delete group {}", groupId);
        groups.remove(groupId);
    }
}
