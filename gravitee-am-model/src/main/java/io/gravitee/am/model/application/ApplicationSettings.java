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
package io.gravitee.am.model.application;

import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationSettings {
    /**
     * OAuth 2.0/OIDC Client settings
     */
    private ApplicationOAuthSettings oauth;
    /**
     * SAML 2.0 Service Provider settings
     */
    private ApplicationSAMLSettings saml;
    /**
     * User Account settings
     */
    private AccountSettings account;
    /**
     * Login settings
     */
    private LoginSettings login;
    /**
     * Advanced settings
     */
    private ApplicationAdvancedSettings advanced;

    /**
     * Password settings
     */
    private PasswordSettings passwordSettings;

    /**
     * MFA settings
     */
    private MFASettings mfa;

    public ApplicationSettings() {
    }

    public ApplicationSettings(ApplicationSettings other) {
        this.oauth = other.oauth != null ? new ApplicationOAuthSettings(other.oauth) : null;
        this.saml = other.saml != null ? new ApplicationSAMLSettings(other.saml) : null;
        this.account = other.account != null ? new AccountSettings(other.account) : null;
        this.login = other.login != null ? new LoginSettings(other.login) : null;
        this.advanced = other.advanced != null ? new ApplicationAdvancedSettings(other.advanced) : null;
        this.passwordSettings = Optional.ofNullable(other.passwordSettings).map(PasswordSettings::new).orElse(null);
        this.mfa = other.mfa != null ? new MFASettings(other.mfa) : null;
    }

    public ApplicationOAuthSettings getOauth() {
        return oauth;
    }

    public void setOauth(ApplicationOAuthSettings oauth) {
        this.oauth = oauth;
    }

    public ApplicationSAMLSettings getSaml() {
        return saml;
    }

    public void setSaml(ApplicationSAMLSettings saml) {
        this.saml = saml;
    }

    public AccountSettings getAccount() {
        return account;
    }

    public void setAccount(AccountSettings account) {
        this.account = account;
    }

    public LoginSettings getLogin() {
        return login;
    }

    public void setLogin(LoginSettings login) {
        this.login = login;
    }

    public ApplicationAdvancedSettings getAdvanced() {
        return advanced != null ? advanced : new ApplicationAdvancedSettings();
    }

    public void setAdvanced(ApplicationAdvancedSettings advanced) {
        this.advanced = advanced;
    }

    public PasswordSettings getPasswordSettings() {
        return passwordSettings;
    }

    public void setPasswordSettings(PasswordSettings passwordSettings) {
        this.passwordSettings = passwordSettings;
    }

    public MFASettings getMfa() {
        return mfa;
    }

    public void setMfa(MFASettings mfa) {
        this.mfa = mfa;
    }

    public void copyTo(Client client) {
        client.setAccountSettings(this.account);
        client.setLoginSettings(this.login);
        client.setPasswordSettings(this.passwordSettings);
        Optional.ofNullable(this.oauth).ifPresent(o->o.copyTo(client));
        Optional.ofNullable(getAdvanced()).ifPresent(a->a.copyTo(client));
        client.setMfaSettings(this.mfa);
        Optional.ofNullable(this.saml).ifPresent(s->s.copyTo(client));
    }
}
