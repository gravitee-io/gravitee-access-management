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
package io.gravitee.am.management.handlers.management.api.manager.membership.impl;

import io.gravitee.am.common.event.MembershipEvent;
import io.gravitee.am.management.handlers.management.api.manager.membership.MembershipManager;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.membership.ReferenceType;
import io.gravitee.am.service.MembershipService;
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
public class MembershipManagerImpl implements MembershipManager, InitializingBean, EventListener<MembershipEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(MembershipManagerImpl.class);
    private ConcurrentMap<String, Membership> memberships = new ConcurrentHashMap<>();
    private ConcurrentMap<String, List<Membership>> localMemberships = new ConcurrentHashMap<>();

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private EventManager eventManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Register event listener for membership events for the management API");
        eventManager.subscribeForEvents(this, MembershipEvent.class);

        logger.info("Initializing management memberships");
        membershipService.findAll()
                .subscribe(
                        membership -> memberships.put(membership.getId(), membership),
                        ex ->  logger.error("An error occurs while loading management roles", ex),
                        () -> logger.info("Management memberships loaded"));
    }

    @Override
    public void onEvent(Event<MembershipEvent, Payload> event) {
        switch (event.type()) {
            case DEPLOY:
            case UPDATE:
                updateMembership(event.content().getId(), event.type());
                break;
            case UNDEPLOY:
                removeMembership(event.content().getId());
                break;
        }
    }

    @Override
    public List<Membership> findByReference(String referenceId, ReferenceType referenceType) {
        List<Membership> membershipList = memberships.values().stream()
                .filter(membership -> membership.getReferenceId().equals(referenceId) && membership.getReferenceType().equals(referenceType))
                .collect(Collectors.toList());

        // membership list can be empty if the resource (Domain or Application) has just been created
        // and the propagation process has not been done yet, retry to be sure
        if (membershipList == null || membershipList.isEmpty()) {
            if (localMemberships.containsKey(referenceId + referenceType.name())) {
                return localMemberships.get(referenceId + referenceType.name());
            } else {
                try {
                    List<Membership> repoMemberships = membershipService.findByReference(referenceId, referenceType).blockingGet();
                    localMemberships.put(referenceId + referenceType.name(), repoMemberships);
                    return repoMemberships;
                } catch (Exception ex) {
                    logger.error("An error occurs while finding memberships by reference {} - {}", referenceId, referenceType, ex);
                }
            }
        }
        return membershipList;
    }

    private void updateMembership(String membershipId, MembershipEvent membershipEvent) {
        final String eventType = membershipEvent.toString().toLowerCase();
        logger.info("Management API has received {} membership event for {}", eventType, membershipId);
        membershipService.findById(membershipId)
                .subscribe(
                        membership -> {
                            memberships.put(membership.getId(), membership);
                            logger.info("Membership {} - {} loaded", membership.getMemberType(), membership.getMemberId(), eventType);
                        },
                        error -> logger.error("Unable to {} membership", eventType, error),
                        () -> logger.error("No membership found with id {}", membershipId));
    }

    private void removeMembership(String membershipId) {
        logger.info("Management API has received membership event, delete membership {}", membershipId);
        memberships.remove(membershipId);
    }
}
