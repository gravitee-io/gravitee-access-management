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
import lombok.Getter;
import lombok.Setter;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
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
     * Period of time (in seconds) after which the user credentials (password or external IdP) is required to unlock passwordless feature
     */
    private Integer passwordlessEnforcePasswordMaxAge;
    /**
     * Enable/Disable passwordless (WebAuthn) device naming feature
     */
    private boolean passwordlessDeviceNamingEnabled;
    /**
     * Enable/Disable Certificate Based Authentication feature
     */
    private boolean certificateBasedAuthEnabled;
    /**
     * Certificate Based Authentication URL
     */
    private String certificateBasedAuthUrl;
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
        this.certificateBasedAuthEnabled = other.certificateBasedAuthEnabled;
        this.certificateBasedAuthUrl = other.certificateBasedAuthUrl;
        this.hideForm = !other.identifierFirstEnabled && other.hideForm;
        this.identifierFirstEnabled = other.identifierFirstEnabled;
        this.resetPasswordOnExpiration = other.resetPasswordOnExpiration;
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
