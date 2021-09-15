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

import io.gravitee.am.model.Environment;
import io.gravitee.am.service.EnvironmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DefaultEnvironmentUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEnvironmentUpgrader.class);

    private final EnvironmentService environmentService;

    public DefaultEnvironmentUpgrader(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @Override
    public boolean upgrade() {

        try {
            Environment environment = environmentService.createDefault().blockingGet();

            if (environment != null) {
                logger.info("Default environment successfully created");
            } else {
                logger.info("One or more environments already exist. Skip");
            }
        } catch (Exception e) {
            logger.error("An error occurred trying to initialize default environment", e);
            return false;
        }

        return true;
    }

    @Override
    public int getOrder() {
        return UpgraderOrder.DEFAULT_ENV_UPGRADER;
    }
}
