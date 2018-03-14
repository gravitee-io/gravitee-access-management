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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.management.core.event.DomainEvent;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.event.EventManager;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DeployAdminDomainUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DeployAdminDomainUpgrader.class);
    private final static String ADMIN_DOMAIN = "admin";

    @Autowired
    private DomainService domainService;

    @Autowired
    private EventManager eventManager;

    @Override
    public boolean upgrade() {
        logger.info("Deploying registered {} domain", ADMIN_DOMAIN);
        domainService.findById(ADMIN_DOMAIN)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(ADMIN_DOMAIN)))
                .subscribe(
                        adminDomain -> eventManager.publishEvent(DomainEvent.DEPLOY, adminDomain),
                        error -> {
                            logger.error("Failed to find admin domain", error);
                            throw new IllegalStateException("Failed to deploy admin domain", error);
                        }
                );
        return true;
    }

    @Override
    public int getOrder() {
        return 170;
    }
}
