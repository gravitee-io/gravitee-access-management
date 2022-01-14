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

import io.gravitee.am.management.service.impl.IdentityProviderManagerImpl;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.impl.ReporterServiceImpl;
import io.reactivex.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DefaultReporterUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DefaultReporterUpgrader.class);

    @Autowired
    private ReporterService reporterService;

    @Override
    public boolean upgrade() {
        logger.info("Applying domain idp upgrade");
        reporterService.findAll()
                .flatMapCompletable(this::updateDefaultReporter)
                .subscribe();
        return true;
    }

    private Completable updateDefaultReporter(Reporter reporter) {
        return reporterService.findById(reporter.getId())
                .flatMapCompletable(reporter1 -> {
                    if (reporter1.isSystem()) {
                        logger.info("Set the default idp found with the default configurations, update idp {}", reporter1.getName());
                        reporter1.setConfiguration(ReporterServiceImpl.getDefaultReporterConfiguration());
                        return Completable.complete();
                    }
                    return Completable.complete();
                });
    }

    @Override
    public int getOrder() {
        return UpgraderOrder.DEFAULT_REPORTER_UPGRADER;
    }
}
