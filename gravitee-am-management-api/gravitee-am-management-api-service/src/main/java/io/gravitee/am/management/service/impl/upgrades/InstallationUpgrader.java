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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Installation;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.service.*;
import io.gravitee.am.service.model.NewScope;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class InstallationUpgrader implements Upgrader, Ordered {

    private final Logger logger = LoggerFactory.getLogger(InstallationUpgrader.class);

    private final InstallationService installationService;

    public InstallationUpgrader(InstallationService installationService) {
        this.installationService = installationService;
    }

    @Override
    public boolean upgrade() {
        final Installation installation = installationService.getOrInitialize().blockingGet();
        logger.info("Current installation id is [{}]", installation.getId());
        return true;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
