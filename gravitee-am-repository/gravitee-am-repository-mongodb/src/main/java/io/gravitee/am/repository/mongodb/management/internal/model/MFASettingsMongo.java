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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.EnrollmentSettings;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.StepUpAuthenticationSettings;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
public class MFASettingsMongo {

    private String loginRule;
    private FactorMongoSettings factor;
    private String stepUpAuthenticationRule;
    private StepUpAuthenticationMongoSettings stepUpAuthentication;
    private String adaptiveAuthenticationRule;
    private RememberDeviceSettingsMongo rememberDevice;
    private EnrollmentSettingsMongo enrollment;
    private EnrollSettingsMongo enroll;
    private ChallengeSettingsMongo challenge;

    public MFASettings convert() {
        MFASettings mfaSettings = new MFASettings();
        mfaSettings.setLoginRule(getLoginRule());
        mfaSettings.setFactor(ofNullable(getFactor()).orElse(new FactorMongoSettings()).convert());
        mfaSettings.setStepUpAuthenticationRule(getStepUpAuthenticationRule());
        mfaSettings.setStepUpAuthentication(ofNullable(getStepUpAuthentication()).orElse(new StepUpAuthenticationMongoSettings()).convert());
        mfaSettings.setAdaptiveAuthenticationRule(getAdaptiveAuthenticationRule());
        mfaSettings.setRememberDevice(ofNullable(getRememberDevice()).orElse(new RememberDeviceSettingsMongo()).convert());
        mfaSettings.setEnrollment(ofNullable(getEnrollment()).orElse(new EnrollmentSettingsMongo()).convert());
        mfaSettings.setEnroll(ofNullable(getEnroll()).orElse(new EnrollSettingsMongo()).convert());
        mfaSettings.setChallenge(ofNullable(getChallenge()).orElse(new ChallengeSettingsMongo()).convert());
        return mfaSettings;
    }

    public static MFASettingsMongo convert(MFASettings mfaSettings) {
        return ofNullable(mfaSettings).filter(Objects::nonNull).map(settings -> {
            MFASettingsMongo mfaSettingsMongo = new MFASettingsMongo();
            mfaSettingsMongo.setLoginRule(settings.getLoginRule());
            mfaSettingsMongo.setFactor(FactorMongoSettings.convert(ofNullable(mfaSettings.getFactor()).orElse(new FactorSettings())));
            mfaSettingsMongo.setStepUpAuthenticationRule(settings.getStepUpAuthenticationRule());
            mfaSettingsMongo.setStepUpAuthentication(StepUpAuthenticationMongoSettings.convert(ofNullable(mfaSettings.getStepUpAuthentication()).orElse(new StepUpAuthenticationSettings())));
            mfaSettingsMongo.setAdaptiveAuthenticationRule(settings.getAdaptiveAuthenticationRule());
            mfaSettingsMongo.setRememberDevice(RememberDeviceSettingsMongo.convert(ofNullable(mfaSettings.getRememberDevice()).orElse(new RememberDeviceSettings())));
            mfaSettingsMongo.setEnrollment(EnrollmentSettingsMongo.convert(ofNullable(mfaSettings.getEnrollment()).orElse(new EnrollmentSettings())));
            mfaSettingsMongo.setEnroll(EnrollSettingsMongo.convert(ofNullable(mfaSettings.getEnroll()).orElse(new EnrollSettings())));
            mfaSettingsMongo.setChallenge(ChallengeSettingsMongo.convert(ofNullable(mfaSettings.getChallenge()).orElse(new ChallengeSettings())));
            return mfaSettingsMongo;
        }).orElse(null);
    }
}
