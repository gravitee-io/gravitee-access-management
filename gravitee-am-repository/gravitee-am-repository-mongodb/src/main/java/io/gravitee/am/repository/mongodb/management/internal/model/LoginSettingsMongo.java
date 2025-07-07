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

import io.gravitee.am.model.login.LoginSettings;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginSettingsMongo {

    private boolean inherited;
    private boolean forgotPasswordEnabled;
    private boolean registerEnabled;
    private boolean rememberMeEnabled;
    private boolean passwordlessEnabled;
    private boolean passwordlessRememberDeviceEnabled;
    private boolean passwordlessEnforcePasswordEnabled;
    private Integer passwordlessEnforcePasswordMaxAge;
    private boolean passwordlessDeviceNamingEnabled;
    private boolean hideForm;
    private boolean identifierFirstLoginEnabled;

    private Boolean resetPasswordOnExpiration;

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public boolean isForgotPasswordEnabled() {
        return forgotPasswordEnabled;
    }

    public void setForgotPasswordEnabled(boolean forgotPasswordEnabled) {
        this.forgotPasswordEnabled = forgotPasswordEnabled;
    }

    public boolean isRegisterEnabled() {
        return registerEnabled;
    }

    public void setRegisterEnabled(boolean registerEnabled) {
        this.registerEnabled = registerEnabled;
    }

    public boolean isRememberMeEnabled() {
        return rememberMeEnabled;
    }

    public void setRememberMeEnabled(boolean rememberMeEnabled) {
        this.rememberMeEnabled = rememberMeEnabled;
    }

    public boolean isPasswordlessEnabled() {
        return passwordlessEnabled;
    }

    public void setPasswordlessEnabled(boolean passwordlessEnabled) {
        this.passwordlessEnabled = passwordlessEnabled;
    }

    public boolean isPasswordlessRememberDeviceEnabled() {
        return passwordlessRememberDeviceEnabled;
    }

    public void setPasswordlessRememberDeviceEnabled(boolean passwordlessRememberDeviceEnabled) {
        this.passwordlessRememberDeviceEnabled = passwordlessRememberDeviceEnabled;
    }

    public boolean isPasswordlessEnforcePasswordEnabled() {
        return passwordlessEnforcePasswordEnabled;
    }

    public void setPasswordlessEnforcePasswordEnabled(boolean passwordlessEnforcePasswordEnabled) {
        this.passwordlessEnforcePasswordEnabled = passwordlessEnforcePasswordEnabled;
    }

    public Integer getPasswordlessEnforcePasswordMaxAge() {
        return passwordlessEnforcePasswordMaxAge;
    }

    public void setPasswordlessEnforcePasswordMaxAge(Integer passwordlessEnforcePasswordMaxAge) {
        this.passwordlessEnforcePasswordMaxAge = passwordlessEnforcePasswordMaxAge;
    }

    public boolean isPasswordlessDeviceNamingEnabled() {
        return passwordlessDeviceNamingEnabled;
    }

    public void setPasswordlessDeviceNamingEnabled(boolean passwordlessDeviceNamingEnabled) {
        this.passwordlessDeviceNamingEnabled = passwordlessDeviceNamingEnabled;
    }

    public boolean isHideForm() {
        return hideForm;
    }

    public void setHideForm(boolean hideForm) {
        this.hideForm = hideForm;
    }

    public boolean isIdentifierFirstLoginEnabled() {
        return identifierFirstLoginEnabled;
    }

    public void setIdentifierFirstLoginEnabled(boolean identifierFirstLoginEnabled) {
        this.identifierFirstLoginEnabled = identifierFirstLoginEnabled;
    }

    public Boolean getResetPasswordOnExpiration() {
        return resetPasswordOnExpiration;
    }

    public void setResetPasswordOnExpiration(Boolean resetPasswordOnExpiration) {
        this.resetPasswordOnExpiration = resetPasswordOnExpiration;
    }

    public LoginSettings convert() {
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setInherited(isInherited());
        loginSettings.setForgotPasswordEnabled(isForgotPasswordEnabled());
        loginSettings.setRegisterEnabled(isRegisterEnabled());
        loginSettings.setRememberMeEnabled(isRememberMeEnabled());
        loginSettings.setPasswordlessEnabled(isPasswordlessEnabled());
        loginSettings.setPasswordlessRememberDeviceEnabled(isPasswordlessRememberDeviceEnabled());
        loginSettings.setPasswordlessEnforcePasswordEnabled(isPasswordlessEnforcePasswordEnabled());
        loginSettings.setPasswordlessEnforcePasswordMaxAge(getPasswordlessEnforcePasswordMaxAge());
        loginSettings.setPasswordlessDeviceNamingEnabled(isPasswordlessDeviceNamingEnabled());
        loginSettings.setHideForm(!isIdentifierFirstLoginEnabled() && isHideForm());
        loginSettings.setIdentifierFirstEnabled(isIdentifierFirstLoginEnabled());
        loginSettings.setResetPasswordOnExpiration(getResetPasswordOnExpiration());

        return loginSettings;
    }

    public static LoginSettingsMongo convert(LoginSettings loginSettings) {
        if (loginSettings == null) {
            return null;
        }

        LoginSettingsMongo loginSettingsMongo = new LoginSettingsMongo();
        loginSettingsMongo.setInherited(loginSettings.isInherited());
        loginSettingsMongo.setForgotPasswordEnabled(loginSettings.isForgotPasswordEnabled());
        loginSettingsMongo.setRegisterEnabled(loginSettings.isRegisterEnabled());
        loginSettingsMongo.setRememberMeEnabled(loginSettings.isRememberMeEnabled());
        loginSettingsMongo.setPasswordlessEnabled(loginSettings.isPasswordlessEnabled());
        loginSettingsMongo.setPasswordlessRememberDeviceEnabled(loginSettings.isPasswordlessRememberDeviceEnabled());
        loginSettingsMongo.setPasswordlessEnforcePasswordEnabled(loginSettings.isPasswordlessEnforcePasswordEnabled());
        loginSettingsMongo.setPasswordlessEnforcePasswordMaxAge(loginSettings.getPasswordlessEnforcePasswordMaxAge());
        loginSettingsMongo.setPasswordlessDeviceNamingEnabled(loginSettings.isPasswordlessDeviceNamingEnabled());
        loginSettingsMongo.setHideForm(!loginSettings.isIdentifierFirstEnabled() && loginSettings.isHideForm());
        loginSettingsMongo.setIdentifierFirstLoginEnabled(loginSettings.isIdentifierFirstEnabled());
        loginSettingsMongo.setResetPasswordOnExpiration(loginSettings.getResetPasswordOnExpiration());

        return loginSettingsMongo;
    }
}
