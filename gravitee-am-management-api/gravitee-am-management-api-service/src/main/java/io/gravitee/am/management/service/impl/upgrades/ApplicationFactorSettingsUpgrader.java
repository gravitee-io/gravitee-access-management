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

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.MfaEnrollType;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.StepUpAuthenticationSettings;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.FactorService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.APPLICATION_FACTOR_UPGRADER;
import static java.lang.Boolean.FALSE;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasLength;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@ManagementRepositoryScope
public class ApplicationFactorSettingsUpgrader extends SystemTaskUpgrader {

    private static final String TASK_ID = "application_factor_settings_migration";
    private static final String UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE =
            "Settings for Application Factors can't be upgraded, other instance may process them or an upgrader has failed previously";

    private final Logger logger = LoggerFactory.getLogger(ApplicationFactorSettingsUpgrader.class);

    private final ApplicationService applicationService;
    private final FactorService factorService;

    public ApplicationFactorSettingsUpgrader(@Lazy SystemTaskRepository systemTaskRepository,
                                             ApplicationService applicationService,
                                             FactorService factorService) {
        super(systemTaskRepository);
        this.applicationService = applicationService;
        this.factorService = factorService;
    }

    @Override
    protected Single<Boolean> processUpgrade(String instanceOperationId, SystemTask task, String previousOperationId) {
        return updateSystemTask(task, (SystemTaskStatus.ONGOING), previousOperationId)
                .flatMap(updatedTask -> {
                    if (updatedTask.getOperationId().equals(instanceOperationId)) {
                        return migrateFactorSettings(updatedTask);
                    } else {
                        return Single.error(new IllegalStateException("Task " + getTaskId() + " already processed by another instance : trigger a retry"));
                    }
                });
    }

    private Single<Boolean> migrateFactorSettings(SystemTask task) {
        return applicationService.fetchAll()
                .flatMapPublisher(Flowable::fromIterable)
                .flatMapSingle(app -> {
                    logger.debug("Process application '{}'", app.getId());
                    if (!isEmpty(app.getFactors())) {
                        return factorService.findByDomain(app.getDomain())
                                .filter(factor -> factor.getFactorType().equals(FactorType.RECOVERY_CODE))
                                .map(Factor::getId)
                                .toList()
                                .flatMap(recoveryCodeIds -> {
                                    if (app.getSettings() == null) {
                                        logger.warn("Application '{}' enables factor but there is no settings set," +
                                                " migrate factor settings but leave MFA settings empty", app.getId());
                                        app.setSettings(buildEmptySettings());
                                    } else if (app.getSettings().getMfa() != null) {
                                        migrateEnrollSettings(app);
                                        migrateChallengeSettings(app);
                                        migrateStepUpSettings(app);
                                    }

                                    // transfer the list of factors into the new Factor Settings structure
                                    // consider the first factor of the list as the default one.
                                    moveFactorIdsIntoFactorSettings(app, recoveryCodeIds);

                                    logger.debug("Update factor settings for application '{}'", app.getId());
                                    return applicationService.update(app);
                                });
                    } else {
                        logger.debug("No factor to process for application '{}'", app.getId());
                    }
                    return Single.just(app);
                }).ignoreElements()
                .doOnError(err -> updateSystemTask(task, (SystemTaskStatus.FAILURE), task.getOperationId()).subscribe())
                .andThen(updateSystemTask(task, SystemTaskStatus.SUCCESS, task.getOperationId())
                        .map(__ -> true)
                        .onErrorResumeNext(err -> {
                            logger.error("Unable to update status for task {}: {}", TASK_ID, err.getMessage());
                            return Single.just(false);
                        }))
                .onErrorResumeNext(err -> {
                    logger.error("Unable to migrate factor settings for applications: {}", err.getMessage());
                    return Single.just(false);
                });
    }

    private static ApplicationSettings buildEmptySettings() {
        ApplicationSettings settings = new ApplicationSettings();
        MFASettings mfaSettings = new MFASettings();
        settings.setMfa(mfaSettings);

        final var enrollSettings = new EnrollSettings();
        mfaSettings.setEnroll(enrollSettings);
        enrollSettings.setActive(false);

        final var challengeSettings = new ChallengeSettings();
        mfaSettings.setChallenge(challengeSettings);
        challengeSettings.setActive(false);

        final var rememberDeviceSettings = new RememberDeviceSettings();
        mfaSettings.setRememberDevice(rememberDeviceSettings);
        rememberDeviceSettings.setActive(false);

        final var stepUpSettings = new StepUpAuthenticationSettings();
        mfaSettings.setStepUpAuthentication(stepUpSettings);
        stepUpSettings.setActive(false);
        return settings;
    }

    private void migrateEnrollSettings(Application app) {
        final var mfaSettings = app.getSettings().getMfa();
        final var enrollSettings = new EnrollSettings();
        mfaSettings.setEnroll(enrollSettings);
        enrollSettings.setActive(true);
        if (FALSE.equals(mfaSettings.getEnrollment().getForceEnrollment())) {
            enrollSettings.setType(MfaEnrollType.OPTIONAL);
            enrollSettings.setForceEnrollment(false);
            enrollSettings.setSkipTimeSeconds(mfaSettings.getEnrollment().getSkipTimeSeconds());
        } else {
            enrollSettings.setType(MfaEnrollType.REQUIRED);
            enrollSettings.setForceEnrollment(true);
        }
    }

    private void migrateChallengeSettings(Application app) {
        final var mfaSettings = app.getSettings().getMfa();
        final var challengeSettings = new ChallengeSettings();
        mfaSettings.setChallenge(challengeSettings);
        challengeSettings.setActive(true);
        if (app.getSettings().getRiskAssessment() != null && app.getSettings().getRiskAssessment().isEnabled() && hasLength(mfaSettings.getAdaptiveAuthenticationRule())) {
            challengeSettings.setType(MfaChallengeType.RISK_BASED);
            challengeSettings.setChallengeRule(mfaSettings.getAdaptiveAuthenticationRule());
        } else if (hasLength(mfaSettings.getAdaptiveAuthenticationRule())) {
            challengeSettings.setType(MfaChallengeType.CONDITIONAL);
            challengeSettings.setChallengeRule(mfaSettings.getAdaptiveAuthenticationRule());
        } else {
            challengeSettings.setType(MfaChallengeType.REQUIRED);
        }
    }

    private void migrateStepUpSettings(Application app) {
        final var mfaSettings = app.getSettings().getMfa();
        final var stepUpSettings = new StepUpAuthenticationSettings();
        stepUpSettings.setActive(hasLength(mfaSettings.getStepUpAuthenticationRule()));
        stepUpSettings.setStepUpAuthenticationRule(mfaSettings.getStepUpAuthenticationRule());
        mfaSettings.setStepUpAuthentication(stepUpSettings);
    }

    private static void moveFactorIdsIntoFactorSettings(Application app, List<String> recoveryCodeIds) {
        final var factorSettings = new FactorSettings();
        factorSettings.setApplicationFactors(new ArrayList<>());
        for (var id : app.getFactors()) {
            if (!hasLength(factorSettings.getDefaultFactorId()) && !recoveryCodeIds.contains(id)) {
                // default factor need to be the first factor from the list
                // but recovery code factor shouldn't be a default candidate
                factorSettings.setDefaultFactorId(id);
            }
            final var appFactorSettings = new ApplicationFactorSettings();
            appFactorSettings.setId(id);
            factorSettings.getApplicationFactors().add(appFactorSettings);
        }

        var mfaSettings = app.getSettings().getMfa();
        if (mfaSettings == null) {
            mfaSettings = new MFASettings();
            app.getSettings().setMfa(mfaSettings);
        }
        mfaSettings.setFactor(factorSettings);
    }

    @Override
    protected IllegalStateException getIllegalStateException() {
        return new IllegalStateException(UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE);
    }

    @Override
    protected String getTaskId() {
        return TASK_ID;
    }

    @Override
    public int getOrder() {
        return APPLICATION_FACTOR_UPGRADER;
    }
}
