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
package io.gravitee.am.gateway.services.sync;

import io.gravitee.am.gateway.core.event.DomainEvent;
import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.model.Domain;
import io.gravitee.common.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncManager {

    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);

    @Autowired
    private DomainService domainService;

    @Autowired
    private EventManager eventManager;

    private Map<String, Domain> deployedDomains = new HashMap<>();

    public void refresh() {
        logger.debug("Refreshing sync state...");

        // Registered domains
        Set<Domain> domains = domainService.findAll();

        // Look for disabled domains
        domains.stream()
                .filter(domain -> !domain.isEnabled())
                .forEach(domain -> {
                    Domain deployedDomain = deployedDomains.get(domain.getId());
                    if (deployedDomain != null) {
                        deployedDomains.remove(domain.getId());
                        eventManager.publishEvent(DomainEvent.UNDEPLOY, deployedDomain);
                    }
                });

        // Deploy domains
        domains.stream()
                .filter(Domain::isEnabled)
                .forEach(domain -> {
                    Domain deployedDomain = deployedDomains.get(domain.getId());
                    if (deployedDomain == null) {
                        eventManager.publishEvent(DomainEvent.DEPLOY, domain);
                        deployedDomains.put(domain.getId(), domain);
                    } else {
                        // Check last update date
                        if (domain.getUpdatedAt().after(deployedDomain.getUpdatedAt())) {
                            eventManager.publishEvent(DomainEvent.UPDATE, domain);
                            deployedDomains.put(domain.getId(), domain);
                        }
                    }
                });
    }
}
