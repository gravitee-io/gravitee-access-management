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

import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.DomainService;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.SYSTEM_CERTIFICATE_UPGRADER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class SystemCertificateUpgrader extends SystemTaskUpgrader {
    private static final String TASK_ID = "system_certificates_migration";
    private static final String UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE =
            "System Certificates can't be upgraded, other instance may process them or an upgrader has failed previously";
    public static final int ONE_MINUTE = 60_000;
    private final Logger logger = LoggerFactory.getLogger(SystemCertificateUpgrader.class);

    private final DomainService domainService;

    @Lazy
    private final CertificateRepository certificateRepository;

    public SystemCertificateUpgrader(@Lazy SystemTaskRepository systemTaskRepository,
                                     DomainService domainService,
                                     @Lazy CertificateRepository certificateRepository) {
        super(systemTaskRepository);
        this.domainService = domainService;
        this.certificateRepository = certificateRepository;
    }

    @Override
    public boolean upgrade() {
        boolean upgraded = super.upgrade();
        if (!upgraded) {
            throw new IllegalStateException(UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE);
        }
        return true;
    }

    @Override
    protected Single<Boolean> processUpgrade(String instanceOperationId, SystemTask task, String conditionalOperationId) {
        return updateSystemTask(task, (SystemTaskStatus.ONGOING), conditionalOperationId)
                .flatMap(updatedTask -> {
                    if (updatedTask.getOperationId().equals(instanceOperationId)) {
                        return flagSystemCertificates(updatedTask);
                    } else {
                        return Single.error(new IllegalStateException("Task " + getTaskId() + " already processed by another instance : trigger a retry"));
                    }
                })
                .map(__ -> true);
    }

    @Override
    protected IllegalStateException getIllegalStateException() {
        return new IllegalStateException(UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE);
    }

    private Single<Boolean> flagSystemCertificates(SystemTask task) {
        return domainService.listAll()
                .flatMap(domain -> certificateRepository.findByDomain(domain.getId())
                        .filter(cert ->
                                // look for the certificates named "Default" and with a creation & update date close to the domain creation
                                cert.getName().equalsIgnoreCase("Default") &&
                                        Math.abs(cert.getCreatedAt().getTime() - domain.getCreatedAt().getTime()) < ONE_MINUTE &&
                                        Math.abs(cert.getUpdatedAt().getTime() - domain.getCreatedAt().getTime()) < ONE_MINUTE
                        )
                ).map(cert -> {
                    cert.setSystem(true);
                    return cert;
                }).flatMapSingle(cert -> certificateRepository.update(cert))
                .count()
                .ignoreElement()
                .doOnError(err -> updateSystemTask(task, (SystemTaskStatus.FAILURE), task.getOperationId()).subscribe())
                .andThen(updateSystemTask(task, SystemTaskStatus.SUCCESS, task.getOperationId())
                        .map(__ -> true)
                        .onErrorResumeNext(err -> {
                            logger.error("Unable to update status for system certificates task: {}", err.getMessage());
                            return Single.just(false);
                        }))
                .onErrorResumeNext(err -> {
                    logger.error("Unable to migrate system certificates: {}", err.getMessage());
                    return Single.just(false);
                });
    }

    @Override
    public int getOrder() {
        return SYSTEM_CERTIFICATE_UPGRADER;
    }

    @Override
    protected String getTaskId() {
        return TASK_ID;
    }
}
