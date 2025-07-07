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
package io.gravitee.am.model.login;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginSettings {

    /**
     * Login settings configuration inherited ?
     */
    private boolean inherited = true;
    /**
     * Enable/Disable forgot password feature
     */
    private boolean forgotPasswordEnabled;
    /**
     * Enable/Disable user registration feature
     */
    private boolean registerEnabled;
    /**
     * Enable/Disable remember me feature (not activate)
     */
    private boolean rememberMeEnabled;
    /**
     * Enable/Disable passwordless (WebAuthn) feature
     */
    private boolean passwordlessEnabled;
    /**
     * Enable/Disable passwordless (WebAuthn) remember device feature
     */
    private boolean passwordlessRememberDeviceEnabled;
    /**
     * Enable/Disable enforce password usage for passwordless (WebAuthn) feature
     */
    private boolean passwordlessEnforcePasswordEnabled;
    /**
     * Period of time (in secods) after which the user credentials (password or external IdP) is required to unlock passwordless feature
     */
    private Integer passwordlessEnforcePasswordMaxAge;
    /**
     * Enable/Disable passwordless (WebAuthn) device naming feature
     */
    private boolean passwordlessDeviceNamingEnabled;
    /**
     * Enable/Disable hide login form
     */
    private boolean hideForm;
    /**
     * Enable/Disable Identifier-first Login
     */
    private boolean identifierFirstEnabled;

    /**
     * Force reset password when it expires
     */
    private Boolean resetPasswordOnExpiration;

    public LoginSettings() {
    }

    public LoginSettings(LoginSettings other) {
        this.inherited = other.inherited;
        this.forgotPasswordEnabled = other.forgotPasswordEnabled;
        this.registerEnabled = other.registerEnabled;
        this.rememberMeEnabled = other.rememberMeEnabled;
        this.passwordlessEnabled = other.passwordlessEnabled;
        this.passwordlessRememberDeviceEnabled = other.passwordlessRememberDeviceEnabled;
        this.passwordlessEnforcePasswordEnabled = other.passwordlessEnforcePasswordEnabled;
        this.passwordlessEnforcePasswordMaxAge = other.passwordlessEnforcePasswordMaxAge;
        this.passwordlessDeviceNamingEnabled = other.passwordlessDeviceNamingEnabled;
        this.hideForm = !other.identifierFirstEnabled && other.hideForm;
        this.identifierFirstEnabled = other.identifierFirstEnabled;
        this.resetPasswordOnExpiration = other.resetPasswordOnExpiration;
    }

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

    public boolean isIdentifierFirstEnabled() {
        return identifierFirstEnabled;
    }

    public void setIdentifierFirstEnabled(boolean identifierFirstEnabled) {
        this.identifierFirstEnabled = identifierFirstEnabled;
    }

    public Boolean getResetPasswordOnExpiration() {
        return resetPasswordOnExpiration;
    }

    public void setResetPasswordOnExpiration(Boolean resetPasswordOnExpiration) {
        this.resetPasswordOnExpiration = resetPasswordOnExpiration;
    }

    public boolean isEnforcePasswordPolicyEnabled() {
        return passwordlessEnabled
                && passwordlessEnforcePasswordEnabled
                && passwordlessEnforcePasswordMaxAge != null;
    }

    public static LoginSettings getInstance(Domain domain, Client client) {
        // if client has no login config return domain config
        if (client == null || client.getLoginSettings() == null) {
            return domain.getLoginSettings();
        }

        // if client configuration is not inherited return the client config
        if (!client.getLoginSettings().isInherited()) {
            return client.getLoginSettings();
        }

        // return domain config
        return domain.getLoginSettings();
    }
}
