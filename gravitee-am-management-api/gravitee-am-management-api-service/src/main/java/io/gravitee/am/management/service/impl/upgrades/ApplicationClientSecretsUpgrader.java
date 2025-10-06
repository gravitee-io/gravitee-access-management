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
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.APPLICATION_CLIENT_SECRETS_UPGRADER;
import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.APPLICATION_SCOPE_SETTINGS_UPGRADER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@ManagementRepositoryScope
public class ApplicationClientSecretsUpgrader extends SystemTaskUpgrader {
    private static final String TASK_ID = "client_secrets_migration";
    private static final String UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE =
            "Application client secrets can't be upgraded, other instance may process them or an upgrader has failed previously";

    private final Logger logger = LoggerFactory.getLogger(ApplicationClientSecretsUpgrader.class);

    public ApplicationClientSecretsUpgrader(@Lazy SystemTaskRepository systemTaskRepository, ApplicationService applicationService) {
        super(systemTaskRepository);
        this.applicationService = applicationService;
    }

    private final ApplicationService applicationService;

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
                        return migrateScopeSettings(updatedTask);
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

    private Single<Boolean> migrateScopeSettings(SystemTask task) {
        return applicationService.fetchAll()
                .flatMapPublisher(Flowable::fromIterable)
                .flatMapSingle(app -> {
                    logger.debug("Process application '{}'", app.getId());
                    // First, if the secrets don't exist at all, create the empty list
                    if (app.getSecretSettings() == null) {
                        app.setSecretSettings(new ArrayList<>());
                    }

                    if (app.getSettings() != null && app.getSettings().getOauth() != null) {
                        final ApplicationOAuthSettings oauthSettings = app.getSettings().getOauth();
                        var clientSecret = oauthSettings.getClientSecret();

                        // If there is not a historical secret, we can skip this application
                        if(clientSecret == null || clientSecret.isEmpty()) {
                            // do nothing
                            return Single.just(app);
                        }

                        // TODO: migrate the client secret into the new list of secrets
                        ClientSecret newSecret = new ClientSecret();
                        newSecret.setSecret(clientSecret);
                        newSecret.setCreatedAt(app.getCreatedAt());
                        newSecret.setExpiresAt(oauthSettings.getClientSecretExpiresAt());

                        logger.debug("Update settings for application '{}'", app.getId());
                    } else {
                        logger.debug("No scope to process for application '{}'", app.getId());
                    }
                    return applicationService.update(app);
                }).ignoreElements()
                .doOnError(err -> updateSystemTask(task, (SystemTaskStatus.FAILURE), task.getOperationId()).subscribe())
                .andThen(updateSystemTask(task, SystemTaskStatus.SUCCESS, task.getOperationId())
                        .map(__ -> true)
                        .onErrorResumeNext(err -> {
                            logger.error("Unable to update status for migrate scope options task: {}", err.getMessage());
                            return Single.just(false);
                        }))
                .onErrorResumeNext(err -> {
                    logger.error("Unable to migrate scope options for applications: {}", err.getMessage());
                    return Single.just(false);
                });
    }

    @Override
    public int getOrder() {
        return APPLICATION_CLIENT_SECRETS_UPGRADER;
    }

    @Override
    protected String getTaskId() {
        return TASK_ID;
    }
}
