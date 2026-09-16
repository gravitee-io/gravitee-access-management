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

import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.impl.upgrades.DomainTrustedIssuerUpgrader;
import io.gravitee.am.management.service.trustdomain.TrustedIssuerProjection;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.reactivex.rxjava3.core.Completable;
import lombok.CustomLog;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the stored trusted-issuer list of every security domain on each start. System upgraders
 * run before {@link DomainTrustedIssuerUpgrader}, so nothing is rebuilt until its migration has
 * succeeded.
 *
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class DomainTrustedIssuerMirrorUpgrader implements SystemUpgrader {

    private final DomainService domainService;
    private final TrustedIssuerProjection trustedIssuerProjection;
    private final SystemTaskRepository systemTaskRepository;

    public DomainTrustedIssuerMirrorUpgrader(DomainService domainService,
                                             TrustedIssuerProjection trustedIssuerProjection,
                                             @Lazy SystemTaskRepository systemTaskRepository) {
        this.domainService = domainService;
        this.trustedIssuerProjection = trustedIssuerProjection;
        this.systemTaskRepository = systemTaskRepository;
    }

    @Override
    public Completable upgrade() {
        return systemTaskRepository.findById(DomainTrustedIssuerUpgrader.TASK_ID)
                .filter(task -> SystemTaskStatus.SUCCESS.name().equals(task.getStatus()))
                .flatMapCompletable(migrated -> domainService.listAll()
                        .filter(domain -> domain.getTokenExchangeSettings() != null)
                        .map(Domain::getId)
                        .concatMapCompletable(trustedIssuerProjection::syncStored));
    }

    @Override
    public int getOrder() {
        return SystemUpgraderOrder.DOMAIN_TRUSTED_ISSUER_MIRROR_UPGRADER;
    }
}
