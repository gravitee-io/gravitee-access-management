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
package io.gravitee.am.service.model;

import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;

public class PatchLoginSettings {

    private Optional<Boolean> inherited;
    private Optional<Boolean> forgotPasswordEnabled;
    private Optional<Boolean> registerEnabled;
    private Optional<Boolean> rememberMeEnabled;
    private Optional<Boolean> passwordlessEnabled;
    private Optional<Boolean> passwordlessRememberDeviceEnabled;
    private Optional<Boolean> passwordlessEnforcePasswordEnabled;
    private Optional<Integer> passwordlessEnforcePasswordMaxAge;
    private Optional<Boolean> passwordlessDeviceNamingEnabled;
    private Optional<Boolean> hideForm;
    private Optional<Boolean> identifierFirstEnabled;
    private Optional<Boolean> resetPasswordOnExpiration;

    public Optional<Boolean> getInherited() {
        return inherited;
    }

    public void setInherited(Optional<Boolean> inherited) {
        this.inherited = inherited;
    }

    public Optional<Boolean> getForgotPasswordEnabled() {
        return forgotPasswordEnabled;
    }

    public void setForgotPasswordEnabled(Optional<Boolean> forgotPasswordEnabled) {
        this.forgotPasswordEnabled = forgotPasswordEnabled;
    }

    public Optional<Boolean> getRegisterEnabled() {
        return registerEnabled;
    }

    public void setRegisterEnabled(Optional<Boolean> registerEnabled) {
        this.registerEnabled = registerEnabled;
    }

    public Optional<Boolean> getRememberMeEnabled() {
        return rememberMeEnabled;
    }

    public void setRememberMeEnabled(Optional<Boolean> rememberMeEnabled) {
        this.rememberMeEnabled = rememberMeEnabled;
    }

    public Optional<Boolean> getPasswordlessEnabled() {
        return passwordlessEnabled;
    }

    public void setPasswordlessEnabled(Optional<Boolean> passwordlessEnabled) {
        this.passwordlessEnabled = passwordlessEnabled;
    }

    public Optional<Boolean> getPasswordlessRememberDeviceEnabled() {
        return passwordlessRememberDeviceEnabled;
    }

    public void setPasswordlessRememberDeviceEnabled(Optional<Boolean> passwordlessRememberDeviceEnabled) {
        this.passwordlessRememberDeviceEnabled = passwordlessRememberDeviceEnabled;
    }

    public Optional<Boolean> getPasswordlessEnforcePasswordEnabled() {
        return passwordlessEnforcePasswordEnabled;
    }

    public void setPasswordlessEnforcePasswordEnabled(Optional<Boolean> passwordlessEnforcePasswordEnabled) {
        this.passwordlessEnforcePasswordEnabled = passwordlessEnforcePasswordEnabled;
    }

    public Optional<Integer> getPasswordlessEnforcePasswordMaxAge() {
        return passwordlessEnforcePasswordMaxAge;
    }

    public void setPasswordlessEnforcePasswordMaxAge(Optional<Integer> passwordlessEnforcePasswordMaxAge) {
        this.passwordlessEnforcePasswordMaxAge = passwordlessEnforcePasswordMaxAge;
    }

    public Optional<Boolean> getPasswordlessDeviceNamingEnabled() {
        return passwordlessDeviceNamingEnabled;
    }

    public void setPasswordlessDeviceNamingEnabled(Optional<Boolean> passwordlessDeviceNamingEnabled) {
        this.passwordlessDeviceNamingEnabled = passwordlessDeviceNamingEnabled;
    }

    public Optional<Boolean> getHideForm() {
        return hideForm;
    }

    public void setHideForm(Optional<Boolean> hideForm) {
        this.hideForm = hideForm;
    }

    public Optional<Boolean> getIdentifierFirstEnabled() {
        return identifierFirstEnabled;
    }

    public void setIdentifierFirstEnabled(Optional<Boolean> identifierFirstEnabled) {
        this.identifierFirstEnabled = identifierFirstEnabled;
    }

    public Optional<Boolean> getResetPasswordOnExpiration() {
        return resetPasswordOnExpiration;
    }

    public void setResetPasswordOnExpiration(Optional<Boolean> resetPasswordOnExpiration) {
        this.resetPasswordOnExpiration = resetPasswordOnExpiration;
    }

    public LoginSettings patch(LoginSettings _toPatch) {
        LoginSettings toPatch = _toPatch == null ? new LoginSettings() : new LoginSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setInherited, this.inherited);
        SetterUtils.safeSet(toPatch::setForgotPasswordEnabled, this.forgotPasswordEnabled);
        SetterUtils.safeSet(toPatch::setRegisterEnabled, this.registerEnabled);
        SetterUtils.safeSet(toPatch::setRememberMeEnabled, this.rememberMeEnabled);
        SetterUtils.safeSet(toPatch::setPasswordlessEnabled, this.passwordlessEnabled);
        SetterUtils.safeSet(toPatch::setPasswordlessRememberDeviceEnabled, this.passwordlessRememberDeviceEnabled);
        SetterUtils.safeSet(toPatch::setPasswordlessEnforcePasswordEnabled, this.passwordlessEnforcePasswordEnabled);
        SetterUtils.safeSet(toPatch::setPasswordlessEnforcePasswordMaxAge, this.passwordlessEnforcePasswordMaxAge);
        SetterUtils.safeSet(toPatch::setPasswordlessDeviceNamingEnabled, this.passwordlessDeviceNamingEnabled);
        SetterUtils.safeSet(toPatch::setHideForm, this.hideForm);
        SetterUtils.safeSet(toPatch::setIdentifierFirstEnabled, this.identifierFirstEnabled);
        SetterUtils.safeSet(toPatch::setResetPasswordOnExpiration, this.resetPasswordOnExpiration);
        return toPatch;
    }
}
