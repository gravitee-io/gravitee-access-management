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
package io.gravitee.am.model;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SelfServiceAccountManagementSettings {

    private boolean enabled;

    private ResetPasswordSettings resetPassword;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ResetPasswordSettings getResetPassword() {
        return resetPassword;
    }

    public void setResetPassword(ResetPasswordSettings resetPassword) {
        this.resetPassword = resetPassword;
    }

    public static class ResetPasswordSettings {
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
    }

    public boolean resetPasswordWithOldValue() {
        return this.resetPassword != null && this.resetPassword.isOldPasswordRequired();
    }

    public boolean resetPasswordWithTokenAge() {
        return this.resetPassword != null && this.resetPassword.getTokenAge() > 0;
    }
}
