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
package io.gravitee.am.gateway.handler.common.group.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.GroupEvent;
import io.gravitee.am.gateway.handler.common.group.GroupManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryGroupManager extends AbstractService implements Service, InitializingBean, GroupManager, EventListener<GroupEvent, Payload> {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryGroupManager.class);

    @Autowired
    Domain domain;

    @Autowired
    EventManager eventManager;

    @Autowired
    GroupRepository groupRepository;

    final ConcurrentMap<String, Group> groups = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        groupRepository.findAll(ReferenceType.DOMAIN, domain.getId()).subscribe(
                group -> groups.put(group.getId(), group),
                error -> LOGGER.error("Unable to initialize groups for domain {}", domain.getName(), error));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        eventManager.subscribeForEvents(this, GroupEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        eventManager.unsubscribeForEvents(this, GroupEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<GroupEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN &&
                domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()){
                case DEPLOY, UPDATE -> deploy(event.content().getId());
                case UNDEPLOY -> undeploy(event.content().getId());
            }
        }
    }

    private void deploy(String groupId) {
        groupRepository.findById(groupId)
                .doOnComplete(() -> LOGGER.info("Deployed group id={}", groupId))
                .subscribe(
                        group -> groups.put(groupId, group),
                        ex -> LOGGER.error("Unable to deploy group id={}", groupId));
    }

    private void undeploy(String groupId){
        this.groups.remove(groupId);
    }

    @Override
    public Flowable<Group> findByMember(String userId) {
        return Flowable.fromIterable(groups.values())
                .filter(g -> g.getMembers().contains(userId));
    }

    @Override
    public Flowable<Group> findByIds(List<String> ids) {
        return Flowable.fromIterable(ids)
                .map(groups::get);
    }
}
