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

import io.gravitee.am.model.Organization;
import io.gravitee.am.service.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DefaultOrganizationUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOrganizationUpgrader.class);

    private final OrganizationService organizationService;

    public DefaultOrganizationUpgrader(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Override
    public boolean upgrade() {

        try {
            Organization organization = organizationService.createDefault().blockingGet();

            if (organization != null) {
                logger.info("Default organization successfully created");
            } else {
                logger.info("One or more organizations already exist. Skip");
            }
        } catch (Exception e) {
            logger.error("An error occurred trying to initialize default organization", e);
            return false;
        }

        return true;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

}
