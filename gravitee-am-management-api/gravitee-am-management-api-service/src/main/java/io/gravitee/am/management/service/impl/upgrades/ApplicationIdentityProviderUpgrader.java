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

import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.APPLICATION_IDENTITY_PROVIDER_UPGRADER;
import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@ManagementRepositoryScope
public class ApplicationIdentityProviderUpgrader extends SystemTaskUpgrader {

    private static final String TASK_ID = "application_identity_provider_migration";

    private final Logger logger = LoggerFactory.getLogger(ApplicationIdentityProviderUpgrader.class);
    private static final String UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE =
            "Settings for Application Identity Providers can't be upgraded, other instance may process them or an upgrader has failed previously";

    private final ApplicationService applicationRepository;

    private final IdentityProviderRepository identityProviderRepository;

    public ApplicationIdentityProviderUpgrader(@Lazy SystemTaskRepository systemTaskRepository,
                                               ApplicationService applicationRepository,
                                               @Lazy IdentityProviderRepository identityProviderRepository) {
        super(systemTaskRepository);
        this.applicationRepository = applicationRepository;
        this.identityProviderRepository = identityProviderRepository;
    }

    @Override
    protected Single<Boolean> processUpgrade(String instanceOperationId, SystemTask task, String conditionalOperationId) {
        return updateSystemTask(task, (SystemTaskStatus.ONGOING), conditionalOperationId)
                .flatMap(updatedTask -> {
                    if (updatedTask.getOperationId().equals(instanceOperationId)) {
                        return migrateApplicationIdentityProviders(task);
                    } else {
                        return Single.error(new IllegalStateException("Task " + getTaskId() + " already processed by another instance : trigger a retry"));
                    }
                });
    }

    private Single<Boolean> migrateApplicationIdentityProviders(SystemTask task) {
        return applicationRepository.fetchAll().flatMapPublisher(Flowable::fromIterable)
                .filter(application -> application.getIdentityProviders() != null && !application.getIdentityProviders().isEmpty())
                .flatMapSingle(this::addRuleInApplicationIdentityProvider)
                .ignoreElements()
                .doOnError(err -> updateSystemTask(task, (SystemTaskStatus.FAILURE), task.getOperationId()).subscribe())
                .andThen(updateSystemTask(task, SystemTaskStatus.SUCCESS, task.getOperationId())
                        .map(__ -> true)
                        .onErrorResumeNext(err -> {
                            logger.error("Unable to update application identityProviders options task: {}", err.getMessage(), err);
                            return Single.just(false);
                        }))
                .onErrorResumeNext(err -> {
                    logger.error("Unable to migrate application identityProvider options for applications: {}", err.getMessage(), err);
                    return Single.just(false);
                });
    }

    private Single<Application> addRuleInApplicationIdentityProvider(Application application) {
        logger.debug("Process application '{}'", application.getId());
        return identityProviderRepository.findAll(DOMAIN, application.getDomain())
                .toMap(IdentityProvider::getId)
                .flatMap(identities -> {
                    if (identities.isEmpty()) {
                        logger.debug("Application '{}' processed", application.getId());
                        return Single.just(application);
                    }
                    boolean modified = false;
                    for (ApplicationIdentityProvider appIdp : application.getIdentityProviders()) {
                        var idp = identities.get(appIdp.getIdentity());
                        if (idp != null) {
                            var whitelist = idp.getDomainWhitelist();
                            if (whitelist != null && !whitelist.isEmpty()) {
                                var selectionRule = whitelist.stream()
                                        .map(pattern -> format("#request.params['username'] matches '.+@%s$'", pattern))
                                        .collect(joining(" || "));
                                appIdp.setSelectionRule("{" + selectionRule + "}");
                                logger.debug("Rule '{}' added to application '{}' for identityProvider '{}'",
                                        appIdp.getSelectionRule(), application.getId(), appIdp.getIdentity());
                                modified = true;
                            }
                        }
                    }
                    logger.debug("Application '{}' processed", application.getId());
                    return modified ? applicationRepository.update(application) : Single.just(application);
                }).onErrorResumeNext(err -> {
                    logger.error("Unable to update application identityProvider options for applications: {}", err.getMessage(), err);
                    return Single.just(application);
                });
    }

    @Override
    protected String getTaskId() {
        return TASK_ID;
    }

    @Override
    public int getOrder() {
        return APPLICATION_IDENTITY_PROVIDER_UPGRADER;
    }

    @Override
    protected IllegalStateException getIllegalStateException() {
        return new IllegalStateException(UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE);
    }
}
