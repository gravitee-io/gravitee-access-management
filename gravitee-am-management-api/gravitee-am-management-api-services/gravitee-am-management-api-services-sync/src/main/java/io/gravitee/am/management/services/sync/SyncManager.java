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
package io.gravitee.am.management.services.sync;

import io.gravitee.am.management.core.event.DomainEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.DomainService;
import io.gravitee.common.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncManager {

    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);
    private final static String ADMIN_DOMAIN = "admin";

    @Autowired
    private DomainService domainService;

    @Autowired
    private EventManager eventManager;

    private Domain deployedAdminDomain;

    public void refresh() {
        logger.debug("Refreshing sync state...");

        // For AM Management API only admin domain is used
        Domain adminDomain = domainService.findById(ADMIN_DOMAIN).blockingGet();

        if (adminDomain != null) {
            // Deploy admin domain
            if (deployedAdminDomain == null) {
                eventManager.publishEvent(DomainEvent.DEPLOY, adminDomain);
                deployedAdminDomain = adminDomain;
            } else {
                // Check last update date
                if (adminDomain.getUpdatedAt().after(deployedAdminDomain.getUpdatedAt())) {
                    eventManager.publishEvent(DomainEvent.UPDATE, adminDomain);
                    deployedAdminDomain = adminDomain;
                }
            }
        }
    }
}
