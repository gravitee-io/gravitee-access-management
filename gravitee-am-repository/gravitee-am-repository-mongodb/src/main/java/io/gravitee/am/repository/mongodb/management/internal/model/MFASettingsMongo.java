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

import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;

import java.util.Objects;

import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFASettingsMongo {

    private String loginRule;
    private String stepUpAuthenticationRule;
    private String adaptiveAuthenticationRule;
    private RememberDeviceSettingsMongo rememberDevice;

    public String getLoginRule() {
        return loginRule;
    }

    public void setLoginRule(String loginRule) {
        this.loginRule = loginRule;
    }

    public String getStepUpAuthenticationRule() {
        return stepUpAuthenticationRule;
    }

    public void setStepUpAuthenticationRule(String stepUpAuthenticationRule) {
        this.stepUpAuthenticationRule = stepUpAuthenticationRule;
    }

    public String getAdaptiveAuthenticationRule() {
        return adaptiveAuthenticationRule;
    }

    public void setAdaptiveAuthenticationRule(String adaptiveAuthenticationRule) {
        this.adaptiveAuthenticationRule = adaptiveAuthenticationRule;
    }

    public RememberDeviceSettingsMongo getRememberDevice() {
        return rememberDevice;
    }

    public void setRememberDevice(RememberDeviceSettingsMongo rememberDevice) {
        this.rememberDevice = rememberDevice;
    }

    public MFASettings convert() {
        MFASettings mfaSettings = new MFASettings();
        mfaSettings.setLoginRule(getLoginRule());
        mfaSettings.setStepUpAuthenticationRule(getStepUpAuthenticationRule());
        mfaSettings.setAdaptiveAuthenticationRule(getAdaptiveAuthenticationRule());
        mfaSettings.setRememberDevice(ofNullable(rememberDevice).orElse(new RememberDeviceSettingsMongo()).convert());
        return mfaSettings;
    }

    public static MFASettingsMongo convert(MFASettings mfaSettings) {
        return ofNullable(mfaSettings).filter(Objects::nonNull).map(settings -> {
            MFASettingsMongo mfaSettingsMongo = new MFASettingsMongo();
            mfaSettingsMongo.setLoginRule(settings.getLoginRule());
            mfaSettingsMongo.setStepUpAuthenticationRule(settings.getStepUpAuthenticationRule());
            mfaSettingsMongo.setAdaptiveAuthenticationRule(settings.getAdaptiveAuthenticationRule());
            mfaSettingsMongo.setRememberDevice(RememberDeviceSettingsMongo.convert(ofNullable(mfaSettings.getRememberDevice()).orElse(new RememberDeviceSettings())));
            return mfaSettingsMongo;
        }).orElse(null);
    }
}
