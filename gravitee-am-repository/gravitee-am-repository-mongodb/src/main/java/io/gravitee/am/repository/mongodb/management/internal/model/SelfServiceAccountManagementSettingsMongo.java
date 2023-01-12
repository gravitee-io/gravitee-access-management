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

import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.repository.mongodb.management.internal.model.selfaccount.ResetPasswordSettingsMongo;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SelfServiceAccountManagementSettingsMongo {

    private boolean enabled;

    private ResetPasswordSettingsMongo resetPassword;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ResetPasswordSettingsMongo getResetPassword() {
        return resetPassword;
    }

    public void setResetPassword(ResetPasswordSettingsMongo resetPassword) {
        this.resetPassword = resetPassword;
    }

    public SelfServiceAccountManagementSettings convert() {
        SelfServiceAccountManagementSettings selfAccountManagementSettings = new SelfServiceAccountManagementSettings();
        selfAccountManagementSettings.setEnabled(isEnabled());
        if (resetPassword != null) {
            selfAccountManagementSettings.setResetPassword(resetPassword.convert());
        }
        return selfAccountManagementSettings;
    }

    public static SelfServiceAccountManagementSettingsMongo convert(SelfServiceAccountManagementSettings other) {
        if (other == null) {
            return null;
        }
        SelfServiceAccountManagementSettingsMongo selfAccountManagementSettings = new SelfServiceAccountManagementSettingsMongo();
        selfAccountManagementSettings.setEnabled(other.isEnabled());
        selfAccountManagementSettings.setResetPassword(ResetPasswordSettingsMongo.convert(other.getResetPassword()));
        return selfAccountManagementSettings;
    }
}
