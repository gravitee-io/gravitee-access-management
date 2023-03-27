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
package io.gravitee.am.repository.mongodb.management.internal.model.selfaccount;

import io.gravitee.am.model.SelfServiceAccountManagementSettings;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResetPasswordSettingsMongo {
    private boolean oldPasswordRequired;
    /**
     * Token age in second
     */
    private int tokenAge;

    public boolean isOldPasswordRequired() {
        return oldPasswordRequired;
    }

    public void setOldPasswordRequired(boolean oldPasswordRequired) {
        this.oldPasswordRequired = oldPasswordRequired;
    }

    public int getTokenAge() {
        return tokenAge;
    }

    public void setTokenAge(int tokenAge) {
        this.tokenAge = tokenAge;
    }

    public SelfServiceAccountManagementSettings.ResetPasswordSettings convert() {
        final var settings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        settings.setTokenAge(getTokenAge());
        settings.setOldPasswordRequired(isOldPasswordRequired());
        return settings;
    }

    public static ResetPasswordSettingsMongo convert(SelfServiceAccountManagementSettings.ResetPasswordSettings other) {
        if (other == null) {
            return null;
        }
        final var settings = new ResetPasswordSettingsMongo();
        settings.setTokenAge(other.getTokenAge());
        settings.setOldPasswordRequired(other.isOldPasswordRequired());
        return settings;
    }
}
