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
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.EnrollmentSettings;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.MfaEnrollType;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.FactorService;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
 class ApplicationFactorSettingsUpgraderTest {
    private static final String FACTOR_ID_RECOVERY_CODE = UUID.randomUUID().toString();
    private static final String FACTOR_ID_OTP = UUID.randomUUID().toString();
    private static final String FACTOR_ID_SMS = UUID.randomUUID().toString();

    @InjectMocks
    private ApplicationFactorSettingsUpgrader upgrader = new ApplicationFactorSettingsUpgrader();

    @Mock
    private SystemTaskRepository systemTaskRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private FactorService factorService;

    @Test
    void should_ignore_if_task_completed() {
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.SUCCESS.name());
        when(systemTaskRepository.findById(any())).thenReturn(Maybe.just(task));

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(any());
        verify(applicationService, never()).findAll();
    }

    @Test
    void should_ignore_application_without_factor() {
        initializeSystemTask();
        final var nullFactorsApp = new Application();
        nullFactorsApp.setFactors(null);
        final var emptyFactorsApp = new Application();
        emptyFactorsApp.setFactors(Set.of());

        when(applicationService.findAll()).thenReturn(Single.just(Set.of(nullFactorsApp, emptyFactorsApp)));

        upgrader.upgrade();

        verifySystemTask();
        verify(applicationService, never()).update(any());
    }

    @Test
    void should_update_factor_application_but_settings_are_empty() {
        initializeSystemTask();
        initializeFactors();

        final var app = new Application();
        Set<String> factorIds = Set.of(FACTOR_ID_OTP, FACTOR_ID_RECOVERY_CODE);
        app.setFactors(factorIds);

        when(applicationService.findAll()).thenReturn(Single.just(Set.of(app)));
        when(applicationService.update(any())).thenReturn(Single.just(app));

        upgrader.upgrade();

        verifySystemTask();
        verify(applicationService).update(argThat(updatedApp ->
            updatedApp.getSettings() != null &&
                    updatedApp.getSettings().getMfa().getFactor() != null &&
                    updatedApp.getSettings().getMfa().getFactor().getApplicationFactors().stream().map(ApplicationFactorSettings::getId).allMatch(factorIds::contains) &&
                    updatedApp.getSettings().getMfa().getFactor().getDefaultFactorId().equals(FACTOR_ID_OTP) &&
                    updatedApp.getSettings().getMfa().getEnroll() != null &&
                    !updatedApp.getSettings().getMfa().getEnroll().isActive() &&
                    updatedApp.getSettings().getMfa().getChallenge() != null &&
                    !updatedApp.getSettings().getMfa().getChallenge().isActive() &&
                    updatedApp.getSettings().getMfa().getRememberDevice() != null &&
                    !updatedApp.getSettings().getMfa().getRememberDevice().isActive() &&
                    updatedApp.getSettings().getMfa().getStepUpAuthentication() != null &&
                    !updatedApp.getSettings().getMfa().getStepUpAuthentication().isActive()
        ));
    }

    @Test
    void should_update_factor_application_with_activate_mfa_to_optional() {
        initializeSystemTask();
        initializeFactors();

        final var app = new Application();
        Set<String> factorIds = Set.of(FACTOR_ID_OTP, FACTOR_ID_RECOVERY_CODE);
        app.setFactors(factorIds);
        final var settings = new ApplicationSettings();
        final var mfaSettings = new MFASettings();
        final var enrollment = new EnrollmentSettings();
        enrollment.setForceEnrollment(false);
        enrollment.setSkipTimeSeconds(120l);
        mfaSettings.setEnrollment(enrollment);
        settings.setMfa(mfaSettings);
        app.setSettings(settings);

        when(applicationService.findAll()).thenReturn(Single.just(Set.of(app)));
        when(applicationService.update(any())).thenReturn(Single.just(app));

        upgrader.upgrade();

        verifySystemTask();
        verify(applicationService).update(argThat(updatedApp ->
                checkFactorSettings(updatedApp, factorIds) &&
                        updatedApp.getSettings() != null &&
                        updatedApp.getSettings().getMfa().getEnroll() != null &&
                        updatedApp.getSettings().getMfa().getEnroll().isActive() &&
                        updatedApp.getSettings().getMfa().getEnroll().getType().equals(MfaEnrollType.OPTIONAL) &&
                        updatedApp.getSettings().getMfa().getEnroll().getForceEnrollment() == enrollment.getForceEnrollment() &&
                        updatedApp.getSettings().getMfa().getEnroll().getSkipTimeSeconds() == enrollment.getSkipTimeSeconds() &&
                        updatedApp.getSettings().getMfa().getChallenge() != null &&
                        updatedApp.getSettings().getMfa().getChallenge().isActive() &&
                        updatedApp.getSettings().getMfa().getChallenge().getType().equals(MfaChallengeType.REQUIRED) &&
                        updatedApp.getSettings().getMfa().getChallenge().getChallengeRule() == null &&
                        updatedApp.getSettings().getMfa().getStepUpAuthentication() != null &&
                        !updatedApp.getSettings().getMfa().getStepUpAuthentication().isActive()
        ));
    }

    @Test
    void should_update_factor_application_with_activate_mfa_to_required() {
        initializeSystemTask();
        initializeFactors();

        final var app = new Application();
        Set<String> factorIds = Set.of(FACTOR_ID_OTP, FACTOR_ID_RECOVERY_CODE);
        app.setFactors(factorIds);
        final var settings = new ApplicationSettings();
        final var mfaSettings = new MFASettings();
        final var enrollment = new EnrollmentSettings();
        enrollment.setForceEnrollment(true);
        mfaSettings.setEnrollment(enrollment);
        settings.setMfa(mfaSettings);
        app.setSettings(settings);

        when(applicationService.findAll()).thenReturn(Single.just(Set.of(app)));
        when(applicationService.update(any())).thenReturn(Single.just(app));

        upgrader.upgrade();

        verifySystemTask();
        verify(applicationService).update(argThat(updatedApp ->
                checkFactorSettings(updatedApp, factorIds) &&
                        updatedApp.getSettings() != null &&
                        updatedApp.getSettings().getMfa().getEnroll() != null &&
                        updatedApp.getSettings().getMfa().getEnroll().isActive() &&
                        updatedApp.getSettings().getMfa().getEnroll().getType().equals(MfaEnrollType.REQUIRED) &&
                        updatedApp.getSettings().getMfa().getEnroll().getForceEnrollment() == enrollment.getForceEnrollment() &&
                        updatedApp.getSettings().getMfa().getChallenge() != null &&
                        updatedApp.getSettings().getMfa().getChallenge().isActive() &&
                        updatedApp.getSettings().getMfa().getChallenge().getType().equals(MfaChallengeType.REQUIRED) &&
                        updatedApp.getSettings().getMfa().getChallenge().getChallengeRule() == null &&
                        updatedApp.getSettings().getMfa().getStepUpAuthentication() != null &&
                        !updatedApp.getSettings().getMfa().getStepUpAuthentication().isActive()
        ));
    }

    @Test
    void should_update_factor_application_with_activate_mfa_to_conditional() {
        initializeSystemTask();
        initializeFactors();

        final var app = new Application();
        Set<String> factorIds = Set.of(FACTOR_ID_OTP, FACTOR_ID_RECOVERY_CODE);
        app.setFactors(factorIds);
        final var settings = new ApplicationSettings();
        final var mfaSettings = new MFASettings();
        final var enrollment = new EnrollmentSettings();
        enrollment.setForceEnrollment(true);
        mfaSettings.setEnrollment(enrollment);
        mfaSettings.setAdaptiveAuthenticationRule(UUID.randomUUID().toString());
        settings.setMfa(mfaSettings);
        app.setSettings(settings);

        when(applicationService.findAll()).thenReturn(Single.just(Set.of(app)));
        when(applicationService.update(any())).thenReturn(Single.just(app));

        upgrader.upgrade();

        verifySystemTask();
        verify(applicationService).update(argThat(updatedApp ->
                checkFactorSettings(updatedApp, factorIds) &&
                        updatedApp.getSettings() != null &&
                        updatedApp.getSettings().getMfa().getEnroll() != null &&
                        updatedApp.getSettings().getMfa().getEnroll().isActive() &&
                        updatedApp.getSettings().getMfa().getEnroll().getType().equals(MfaEnrollType.REQUIRED) &&
                        updatedApp.getSettings().getMfa().getEnroll().getForceEnrollment() == enrollment.getForceEnrollment() &&
                        updatedApp.getSettings().getMfa().getChallenge() != null &&
                        updatedApp.getSettings().getMfa().getChallenge().isActive() &&
                        updatedApp.getSettings().getMfa().getChallenge().getType().equals(MfaChallengeType.CONDITIONAL) &&
                        updatedApp.getSettings().getMfa().getChallenge().getChallengeRule().equals(mfaSettings.getAdaptiveAuthenticationRule()) &&
                        updatedApp.getSettings().getMfa().getStepUpAuthentication() != null &&
                        !updatedApp.getSettings().getMfa().getStepUpAuthentication().isActive()
        ));
    }

    @Test
    void should_update_factor_application_with_activate_mfa_to_risk_based() {
        initializeSystemTask();
        initializeFactors();

        final var app = new Application();
        Set<String> factorIds = Set.of(FACTOR_ID_OTP, FACTOR_ID_RECOVERY_CODE);
        app.setFactors(factorIds);
        final var settings = new ApplicationSettings();
        final var mfaSettings = new MFASettings();
        final var enrollment = new EnrollmentSettings();
        enrollment.setForceEnrollment(true);
        mfaSettings.setEnrollment(enrollment);
        mfaSettings.setAdaptiveAuthenticationRule(UUID.randomUUID().toString());
        settings.setMfa(mfaSettings);
        final var riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        settings.setRiskAssessment(riskAssessment);
        app.setSettings(settings);

        when(applicationService.findAll()).thenReturn(Single.just(Set.of(app)));
        when(applicationService.update(any())).thenReturn(Single.just(app));

        upgrader.upgrade();

        verifySystemTask();
        verify(applicationService).update(argThat(updatedApp ->
                checkFactorSettings(updatedApp, factorIds) &&
                        updatedApp.getSettings() != null &&
                        updatedApp.getSettings().getMfa().getEnroll() != null &&
                        updatedApp.getSettings().getMfa().getEnroll().isActive() &&
                        updatedApp.getSettings().getMfa().getEnroll().getType().equals(MfaEnrollType.REQUIRED) &&
                        updatedApp.getSettings().getMfa().getEnroll().getForceEnrollment() == enrollment.getForceEnrollment() &&
                        updatedApp.getSettings().getMfa().getChallenge() != null &&
                        updatedApp.getSettings().getMfa().getChallenge().isActive() &&
                        updatedApp.getSettings().getMfa().getChallenge().getType().equals(MfaChallengeType.RISK_BASED) &&
                        updatedApp.getSettings().getMfa().getChallenge().getChallengeRule().equals(mfaSettings.getAdaptiveAuthenticationRule()) &&
                        updatedApp.getSettings().getRiskAssessment().isEnabled() &&
                        updatedApp.getSettings().getMfa().getStepUpAuthentication() != null &&
                        !updatedApp.getSettings().getMfa().getStepUpAuthentication().isActive()
        ));
    }

    @Test
    void should_update_factor_application_with_activate_mfa_to_conditional_and_stepUp() {
        initializeSystemTask();
        initializeFactors();

        final var app = new Application();
        Set<String> factorIds = Set.of(FACTOR_ID_OTP, FACTOR_ID_RECOVERY_CODE);
        app.setFactors(factorIds);
        final var settings = new ApplicationSettings();
        final var mfaSettings = new MFASettings();
        final var enrollment = new EnrollmentSettings();
        enrollment.setForceEnrollment(true);
        mfaSettings.setEnrollment(enrollment);
        mfaSettings.setAdaptiveAuthenticationRule(UUID.randomUUID().toString());
        mfaSettings.setStepUpAuthenticationRule(UUID.randomUUID().toString());
        settings.setMfa(mfaSettings);
        app.setSettings(settings);

        when(applicationService.findAll()).thenReturn(Single.just(Set.of(app)));
        when(applicationService.update(any())).thenReturn(Single.just(app));

        upgrader.upgrade();

        verifySystemTask();
        verify(applicationService).update(argThat(updatedApp ->
                checkFactorSettings(updatedApp, factorIds) &&
                        updatedApp.getSettings() != null &&
                        updatedApp.getSettings().getMfa().getEnroll() != null &&
                        updatedApp.getSettings().getMfa().getEnroll().isActive() &&
                        updatedApp.getSettings().getMfa().getEnroll().getType().equals(MfaEnrollType.REQUIRED) &&
                        updatedApp.getSettings().getMfa().getEnroll().getForceEnrollment() == enrollment.getForceEnrollment() &&
                        updatedApp.getSettings().getMfa().getChallenge() != null &&
                        updatedApp.getSettings().getMfa().getChallenge().isActive() &&
                        updatedApp.getSettings().getMfa().getChallenge().getType().equals(MfaChallengeType.CONDITIONAL) &&
                        updatedApp.getSettings().getMfa().getChallenge().getChallengeRule().equals(mfaSettings.getAdaptiveAuthenticationRule()) &&
                        updatedApp.getSettings().getMfa().getStepUpAuthentication() != null &&
                        updatedApp.getSettings().getMfa().getStepUpAuthentication().isActive() &&
                        updatedApp.getSettings().getMfa().getStepUpAuthentication().getStepUpAuthenticationRule().equals(mfaSettings.getStepUpAuthenticationRule())
        ));
    }

    private boolean checkFactorSettings(Application updatedApp, Set<String> factorIds) {
        return updatedApp.getSettings().getMfa().getFactor() != null &&
                updatedApp.getSettings().getMfa().getFactor().getApplicationFactors().stream().map(ApplicationFactorSettings::getId).allMatch(factorIds::contains) &&
                updatedApp.getSettings().getMfa().getFactor().getDefaultFactorId().equals(FACTOR_ID_OTP);
    }

    private void initializeFactors() {
        var otp = new Factor();
        otp.setFactorType(FactorType.OTP);
        otp.setId(FACTOR_ID_OTP);

        var sms = new Factor();
        sms.setFactorType(FactorType.SMS);
        sms.setId(FACTOR_ID_SMS);

        var recovery = new Factor();
        recovery.setFactorType(FactorType.RECOVERY_CODE);
        recovery.setId(FACTOR_ID_RECOVERY_CODE);

        when(factorService.findByDomain(any())).thenReturn(Flowable.just(recovery, otp, sms));
    }

    private void initializeSystemTask() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(args -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });
    }

    private void verifySystemTask() {
        verify(systemTaskRepository, times(1)).findById(anyString());
        verify(systemTaskRepository, times(2)).updateIf(any(), any());
    }
}
