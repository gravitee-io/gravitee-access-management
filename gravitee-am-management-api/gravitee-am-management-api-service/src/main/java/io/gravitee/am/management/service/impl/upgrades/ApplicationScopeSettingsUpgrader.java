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
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.APPLICATION_SCOPE_SETTINGS_UPGRADER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ApplicationScopeSettingsUpgrader extends SystemTaskUpgrader {
    private static final String TASK_ID = "scope_settings_migration";
    private static final String UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE =
            "Settings for Application Scopes can't be upgraded, other instance may process them or an upgrader has failed previously";

    private final Logger logger = LoggerFactory.getLogger(ApplicationScopeSettingsUpgrader.class);

    @Autowired
    private ApplicationService applicationService;

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

    private Single<SystemTask> updateSystemTask(SystemTask task, SystemTaskStatus status, String operationId) {
        task.setUpdatedAt(new Date());
        task.setStatus(status.name());
        return systemTaskRepository.updateIf(task, operationId);
    }

    private Single<Boolean> migrateScopeSettings(SystemTask task) {
        return applicationService.findAll()
                .flatMapPublisher(Flowable::fromIterable)
                .flatMapSingle(app -> {
                    logger.debug("Process application '{}'", app.getId());
                    if (app.getSettings() != null && app.getSettings().getOauth() != null) {
                        final ApplicationOAuthSettings oauthSettings = app.getSettings().getOauth();
                        List<ApplicationScopeSettings> scopeSettings = new ArrayList<>();
                        if (oauthSettings.getScopes() != null && !oauthSettings.getScopes().isEmpty()) {
                            logger.debug("Process scope options for application '{}'", app.getId());
                            for (String scope : oauthSettings.getScopes()) {
                                ApplicationScopeSettings setting = new ApplicationScopeSettings();
                                setting.setScope(scope);
                                setting.setDefaultScope(oauthSettings.getDefaultScopes() != null && oauthSettings.getDefaultScopes().contains(scope));
                                if (oauthSettings.getScopeApprovals() != null && oauthSettings.getScopeApprovals().containsKey(scope)) {
                                    setting.setScopeApproval(oauthSettings.getScopeApprovals().get(scope));
                                }
                                scopeSettings.add(setting);
                            }

                            oauthSettings.setScopeSettings(scopeSettings);

                            // remove old values
                            oauthSettings.setScopes(null);
                            oauthSettings.setDefaultScopes(null);
                            oauthSettings.setScopeApprovals(null);

                            logger.debug("Update settings for application '{}'", app.getId());
                            return applicationService.update(app);
                        } else {
                            logger.debug("No scope to process for application '{}'", app.getId());
                        }
                    } else {
                        logger.debug("No scope to process for application '{}'", app.getId());
                    }
                    return Single.just(app);
                }).ignoreElements()
                .doOnError(err -> updateSystemTask(task, (SystemTaskStatus.FAILURE), task.getOperationId()).subscribe())
                .andThen(updateSystemTask(task, SystemTaskStatus.SUCCESS, task.getOperationId())
                        .map(__ -> true)
                        .onErrorResumeNext((err) -> {
                            logger.error("Unable to update status for migrate scope options task: {}", err.getMessage());
                            return Single.just(false);
                        }))
                .onErrorResumeNext((err) -> {
                    logger.error("Unable to migrate scope options for applications: {}", err.getMessage());
                    return Single.just(false);
                });
    }

    @Override
    public int getOrder() {
        return APPLICATION_SCOPE_SETTINGS_UPGRADER;
    }

    @Override
    protected String getTaskId() {
        return TASK_ID;
    }
}
