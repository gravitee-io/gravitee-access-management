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
package io.gravitee.am.service.tasks;

import io.gravitee.am.model.Application;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.TaskManager;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AssignSystemCertificate extends AbstractTask<AssignSystemCertificateDefinition> {

    private final Logger logger = LoggerFactory.getLogger(AssignSystemCertificate.class);

    private final ApplicationService applicationService;

    private final CertificateRepository certificateRepository;

    private final TaskManager taskManager;

    private AssignSystemCertificateDefinition configuration;

    public AssignSystemCertificate(ApplicationService applicationService, CertificateRepository certificateRepository, TaskManager taskManager) {
        this(UUID.randomUUID().toString(), applicationService, certificateRepository, taskManager);
    }

    public AssignSystemCertificate(String taskId, ApplicationService applicationService, CertificateRepository certificateRepository, TaskManager taskManager) {
        super(taskId);
        this.applicationService = applicationService;
        this.certificateRepository = certificateRepository;
        this.taskManager = taskManager;
    }

    @Override
    public AssignSystemCertificateDefinition getDefinition() {
        return this.configuration;
    }

    public void setDefinition(AssignSystemCertificateDefinition definition) {
        this.configuration = definition;
    }

    @Override
    public TaskType type() {
        return TaskType.SIMPLE;
    }

    @Override
    public boolean rescheduledOnError() {
        return true;
    }

    @Override
    public void run() {
        final var domainId = this.configuration.getDomainId();
        final var renewedCertificate = this.configuration.getRenewedCertificate();
        final var deprecatedCertificate = this.configuration.getDeprecatedCertificate();
        this.logger.debug("Start assign system certificate for domain {}. (deprecated certificate: {} / new certificate: {})",
                domainId, deprecatedCertificate, renewedCertificate);

        this.taskManager.isActiveTask(getId())
                .flatMap(needProcessing -> {
                            if (needProcessing) {
                                return this.certificateRepository.findById(renewedCertificate)
                                        .map(Optional::ofNullable)
                                        .switchIfEmpty(Single.just(Optional.empty()))
                                        .flatMap(cert -> {
                                            if (cert.isPresent()) {
                                                return this.applicationService.findByDomain(domainId)
                                                        .flattenAsFlowable(apps -> apps)
                                                        .filter(app -> deprecatedCertificate.equals(app.getCertificate()))
                                                        .flatMapSingle(app -> {
                                                            Application toUpdateApp = new Application(app);
                                                            toUpdateApp.setCertificate(renewedCertificate);
                                                            return this.applicationService.update(toUpdateApp);
                                                        })
                                                        .count();
                                            } else {
                                                logger.warn("System certificate {} doesn't exist, unable to assigne it to applications of domain {}", renewedCertificate, domainId);
                                                return Single.just(0L);
                                            }
                                        });
                            } else {
                                return Single.just(-1L);
                            }
                        }
                )
                .subscribe(apps -> {
                            if (apps >= 0) {
                                this.logger.info("System certificate {} assigned to {} applications of domain {}", renewedCertificate, apps, domainId);
                                this.taskManager.remove(this.getId())
                                        .doOnError(error -> logger.warn("Unable to delete task {}", this.getId(), error))
                                        .subscribe();
                            } else {
                                this.logger.debug("Task already executed to assign system certificate for domain {}. (deprecated certificate: {} / new certificate: {})",
                                        domainId, deprecatedCertificate, renewedCertificate);
                            }
                        },
                        error -> {
                            logger.warn("System certificate {} can't be assigned to applications of domain {}: {}", renewedCertificate, domainId, error.getMessage());
                            if (rescheduledOnError()) {
                                logger.info("Reschedule task {} to assign system certificate {}", getId(), renewedCertificate);
                                this.schedule();
                            } else {
                                this.taskManager.markAsError(this.getId())
                                        .doOnError(e -> logger.warn("Unable to register error status for task {}", this.getId(), e))
                                        .subscribe();
                            }
                        });
    }
}
