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
import io.gravitee.am.model.Reference;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.ReporterService;
import io.reactivex.rxjava3.core.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Create default mongo Reporter for each domain for audit logs
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainReporterUpgrader extends AsyncUpgrader {

    private static final Logger logger = LoggerFactory.getLogger(DomainReporterUpgrader.class);

    @Autowired
    private DomainService domainService;

    @Autowired
    private ReporterService reporterService;

    @Override
    public Completable doUpgrade() {
        logger.info("Applying domain reporter upgrade");
        return domainService.listAll()
                .flatMapCompletable(this::updateDefaultReporter);
    }

    private Completable updateDefaultReporter(Domain domain) {
        return reporterService.findByReference(Reference.domain(domain.getId()))
                .toList()
                .flatMapCompletable(reporters -> {
                    if (reporters == null || reporters.isEmpty()) {
                        logger.info("No default reporter found for domain {}, update domain", domain.getName());
                        return reporterService.createDefault(Reference.domain(domain.getId()))
                                .ignoreElement();
                    }
                    return Completable.complete();
                });
    }

    @Override
    public int getOrder() {
        return UpgraderOrder.DOMAIN_REPORTER_UPGRADER;
    }

}
