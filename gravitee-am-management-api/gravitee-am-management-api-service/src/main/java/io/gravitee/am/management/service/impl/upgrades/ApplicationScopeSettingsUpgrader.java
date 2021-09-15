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
import io.gravitee.am.model.SystemTaskTypes;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.APPLICATION_SCOPE_SETTINGS_UPGRADER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ApplicationScopeSettingsUpgrader implements Upgrader, Ordered {
    private static final String TASK_ID = "scope_settings_migration";
    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(ApplicationScopeSettingsUpgrader.class);

    @Autowired
    @Lazy
    private SystemTaskRepository systemTaskRepository;

    @Autowired
    @Lazy
    private ApplicationRepository applicationRepository;

    @Override
    public boolean upgrade() {
        final String instanceOperationId = UUID.randomUUID().toString();
        boolean upgraded = systemTaskRepository.findById(TASK_ID)
                .switchIfEmpty(Single.defer(() -> createSystemTask(instanceOperationId)))
                .flatMap(task -> {
                    switch (SystemTaskStatus.valueOf(task.getStatus())) {
                        case INITIALIZED:
                            return processUpgrade(instanceOperationId, task, instanceOperationId);
                        case FAILURE:
                            // In Failure case, we use the operationId of the read task otherwise update will always fail
                            // force the task.operationId to assign the task to the instance
                            String previousOperationId = task.getOperationId();
                            task.setOperationId(instanceOperationId);
                            return processUpgrade(instanceOperationId, task, previousOperationId);
                        case ONGOING:
                            // wait until status change
                            return Single.error(new IllegalStateException("ONGOING task " + TASK_ID + " : trigger a retry"));
                        default:
                            // SUCCESS case
                            return Single.just(true);
                    }
                }).retryWhen(new RetryWithDelay(3, 5000)).blockingGet();

        if (!upgraded) {
            throw new IllegalStateException("Settings for Application Scopes can't be upgraded, other instance may process them or an upgrader has failed previously");
        }

        return upgraded;
    }

    private Single<Boolean> processUpgrade(String instanceOperationId, SystemTask task, String conditionalOperationId) {
        return updateSystemTask(task, (SystemTaskStatus.ONGOING), conditionalOperationId)
                .flatMap(updatedTask -> {
                    if (updatedTask.getOperationId().equals(instanceOperationId)) {
                        return migrateScopeSettings(updatedTask);
                    } else {
                        return Single.error(new IllegalStateException("Task " + TASK_ID + " already processed by another instance : trigger a retry"));
                    }
                })
                .map(__ -> true);
    }

    private Single<SystemTask> createSystemTask(String operationId) {
        SystemTask  systemTask = new SystemTask();
        systemTask.setId(TASK_ID);
        systemTask.setType(SystemTaskTypes.UPGRADER.name());
        systemTask.setStatus(SystemTaskStatus.INITIALIZED.name());
        systemTask.setCreatedAt(new Date());
        systemTask.setUpdatedAt(systemTask.getCreatedAt());
        systemTask.setOperationId(operationId);
        return systemTaskRepository.create(systemTask).onErrorResumeNext(err -> {
            logger.warn("SystemTask {} can't be created due to '{}'", TASK_ID, err.getMessage());
            // if the creation fails, try to find the task, this will allow to manage the retry properly
            return systemTaskRepository.findById(systemTask.getId()).toSingle();
        });
    }

    private Single<SystemTask>  updateSystemTask(SystemTask task, SystemTaskStatus status, String operationId) {
        task.setUpdatedAt(new Date());
        task.setStatus(status.name());
        return systemTaskRepository.updateIf(task, operationId);
    }

    private Single<Boolean> migrateScopeSettings(SystemTask task) {
        return applicationRepository.findAll().flatMapSingle(app -> {
                    logger.debug("Process application '{}'", app.getId());
                    if (app.getSettings() != null && app.getSettings().getOauth() != null) {
                        final ApplicationOAuthSettings oauthSettings = app.getSettings().getOauth();
                        List<ApplicationScopeSettings> scopeSettings = new ArrayList<>();
                        if (oauthSettings.getScopes() != null && !oauthSettings.getScopes().isEmpty()) {
                            logger.debug("Process scope options for application '{}'", app.getId());
                            for (String scope: oauthSettings.getScopes()) {
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
                            return applicationRepository.update(app);
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

    private class RetryWithDelay implements Function<Flowable<Throwable>, Publisher<?>> {
        private final int maxRetries;
        private final int retryDelayMillis;
        private int retryCount;

        public RetryWithDelay(int retries, int delay) {
            this.maxRetries = retries;
            this.retryDelayMillis = delay;
            this.retryCount = 0;
        }

        @Override
        public Publisher<?> apply(@NonNull Flowable<Throwable> attempts) throws Exception {
            return attempts
                    .flatMap((throwable) -> {
                        if (++retryCount < maxRetries) {
                            // When this Observable calls onNext, the original
                            // Observable will be retried (i.e. re-subscribed).
                            return Flowable.timer(retryDelayMillis * (retryCount + 1),
                                    TimeUnit.MILLISECONDS);
                        }
                        // Max retries hit. Just pass the error along.
                        return Flowable.error(throwable);
                    });
        }
    }
}
