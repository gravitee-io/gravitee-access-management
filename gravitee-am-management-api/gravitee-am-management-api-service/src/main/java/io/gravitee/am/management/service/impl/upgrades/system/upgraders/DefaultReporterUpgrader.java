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
package io.gravitee.am.management.service.impl.upgrades.system.upgraders;

import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.model.UpdateReporter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Islem TRIKI (islem.triki at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
public class DefaultReporterUpgrader implements SystemUpgrader {

    private static final Logger logger = LoggerFactory.getLogger(DefaultReporterUpgrader.class);

    private final ReporterService reporterService;

    @Override
    public Completable upgrade() {
        logger.info("Applying domain reporter upgrade");
        return Completable.fromPublisher(reporterService.findAll()
                .filter(Reporter::isSystem)
                .flatMapSingle(this::updateDefaultReporter)
                .doOnNext(reporter -> logger.info("updated reporter: id={}", reporter.getId())));
    }

    private Single<Reporter> updateDefaultReporter(Reporter reporter) {
        logger.info("Configuring updated default reporter: {} ({})", reporter.getName(), reporter.getId());
        UpdateReporter updateReporter = new UpdateReporter();
        updateReporter.setEnabled(reporter.isEnabled());
        updateReporter.setName(reporter.getName());
        updateReporter.setConfiguration(reporterService.createReporterConfig(reporter.getReference()));

        logger.info("Executing update for default reporter: {} ({})", reporter.getName(), reporter.getId());
        return reporterService.update(reporter.getReference(), reporter.getId(), updateReporter, true);
    }

    @Override
    public int getOrder() {
        return SystemUpgraderOrder.DEFAULT_REPORTER_UPGRADER;
    }
}
