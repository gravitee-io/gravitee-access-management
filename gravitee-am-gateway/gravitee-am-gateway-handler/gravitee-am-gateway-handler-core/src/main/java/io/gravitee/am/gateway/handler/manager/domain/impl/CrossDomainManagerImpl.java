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
package io.gravitee.am.gateway.handler.manager.domain.impl;

import io.gravitee.am.common.event.DomainEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.manager.domain.CrossDomainManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CrossDomainManagerImpl extends AbstractService implements CrossDomainManager, InitializingBean, EventListener<DomainEvent, Domain> {

    private static Logger logger = LoggerFactory.getLogger(CrossDomainManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private ClientManager clientManager;

    @Autowired
    private EventManager eventManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (domain.isMaster()) {
            // notify for cross domain events
            domainRepository.findAllByReferenceId(domain.getReferenceId())
                    .filter(d -> !domain.getId().equals(d.getId()))
                    .toList()
                    .subscribe(domains -> domains.forEach(d -> clientManager.deployCrossDomain(d)));
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (domain.isMaster()) {
            logger.info("Register event listener for cross domain events for domain {}", domain.getName());
            eventManager.subscribeForEvents(this, DomainEvent.class);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (domain.isMaster()) {
            eventManager.unsubscribeForCrossEvents(this, DomainEvent.class, null);
        }
    }

    @Override
    public void onEvent(Event<DomainEvent, Domain> event) {
        if (!this.domain.getId().equals(event.content().getId())) {
            switch (event.type()) {
                case DEPLOY:
                    clientManager.deployCrossDomain(event.content());
                    break;
                case UNDEPLOY:
                    clientManager.undeployCrossDomain(event.content());
                    break;
            }
        }
    }
}
