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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationSettingsMongo {

    private ApplicationOAuthSettingsMongo oauth;
    private AccountSettingsMongo account;
    private LoginSettingsMongo login;
    private ApplicationAdvancedSettingsMongo advanced;
    private PasswordSettingsMongo passwordSettings;
    private MFASettingsMongo mfa;
    private CookieSettingsMongo cookieSettings;

    public ApplicationOAuthSettingsMongo getOauth() {
        return oauth;
    }

    public void setOauth(ApplicationOAuthSettingsMongo oauth) {
        this.oauth = oauth;
    }

    public AccountSettingsMongo getAccount() {
        return account;
    }

    public void setAccount(AccountSettingsMongo account) {
        this.account = account;
    }

    public LoginSettingsMongo getLogin() {
        return login;
    }

    public void setLogin(LoginSettingsMongo login) {
        this.login = login;
    }

    public ApplicationAdvancedSettingsMongo getAdvanced() {
        return advanced;
    }

    public void setAdvanced(ApplicationAdvancedSettingsMongo advanced) {
        this.advanced = advanced;
    }

    public PasswordSettingsMongo getPasswordSettings() {
        return passwordSettings;
    }

    public void setPasswordSettings(PasswordSettingsMongo passwordSettings) {
        this.passwordSettings = passwordSettings;
    }

    public MFASettingsMongo getMfa() {
        return mfa;
    }

    public void setMfa(MFASettingsMongo mfa) {
        this.mfa = mfa;
    }

    public CookieSettingsMongo getCookieSettings() {
        return cookieSettings;
    }

    public void setCookieSettings(CookieSettingsMongo cookieSettings) {
        this.cookieSettings = cookieSettings;
    }
}
