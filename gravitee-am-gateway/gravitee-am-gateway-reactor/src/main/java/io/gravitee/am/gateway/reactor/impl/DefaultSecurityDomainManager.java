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
package io.gravitee.am.gateway.reactor.impl;

import io.gravitee.am.common.event.DomainEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.gateway.reactor.SecurityDomainManager;
import io.gravitee.am.model.Domain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultSecurityDomainManager implements SecurityDomainManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSecurityDomainManager.class);

    @Autowired
    private EventManager eventManager;

    private final Map<String, Domain> domains = new HashMap<>();


    @Override
    public void deploy(Domain domain) {
        logger.info("Deployment of {}", domain.getId());

        if (domain.isEnabled()) {
            eventManager.publishEvent(DomainEvent.DEPLOY, domain);
            domains.put(domain.getId(), domain);
        } else {
            logger.info("{} is not enabled. Skip deployment.", domain.getId());
        }
    }

    @Override
    public void update(Domain domain) {
        logger.info("Updating {}", domain.getId());
        if (domain.isEnabled()) {
            domains.put(domain.getId(), domain);
            eventManager.publishEvent(DomainEvent.UPDATE, domain);
        } else {
            // domain has been disabled, undeploy
            if (domains.containsKey(domain.getId())) {
                logger.info("{} has been disabled.", domain.getId());
                undeploy(domain.getId());
            }
        }
    }

    @Override
    public void undeploy(String domainId) {
        Domain currentDomain = domains.remove(domainId);
        if (currentDomain != null) {
            logger.info("Undeployment of {}", currentDomain.getId());

            eventManager.publishEvent(DomainEvent.UNDEPLOY, currentDomain);
            logger.info("{} has been undeployed", domainId);
        }
    }

    @Override
    public Collection<Domain> domains() {
        return domains.values();
    }

    @Override
    public Domain get(String domainId) {
        return domains.get(domainId);
    }
}
