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

import io.gravitee.am.model.account.AccountSettings;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountSettingsMongo {

    private boolean inherited;
    private boolean loginAttemptsDetectionEnabled;
    private Integer maxLoginAttempts;
    private Integer loginAttemptsResetTime;
    private Integer accountBlockedDuration;
    private boolean completeRegistrationWhenResetPassword;
    private boolean autoLoginAfterRegistration;
    private String redirectUriAfterRegistration;
    private boolean dynamicUserRegistration;
    private boolean autoLoginAfterResetPassword;
    private String redirectUriAfterResetPassword;

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public boolean isLoginAttemptsDetectionEnabled() {
        return loginAttemptsDetectionEnabled;
    }

    public void setLoginAttemptsDetectionEnabled(boolean loginAttemptsDetectionEnabled) {
        this.loginAttemptsDetectionEnabled = loginAttemptsDetectionEnabled;
    }

    public Integer getMaxLoginAttempts() {
        return maxLoginAttempts;
    }

    public void setMaxLoginAttempts(Integer maxLoginAttempts) {
        this.maxLoginAttempts = maxLoginAttempts;
    }

    public Integer getLoginAttemptsResetTime() {
        return loginAttemptsResetTime;
    }

    public void setLoginAttemptsResetTime(Integer loginAttemptsResetTime) {
        this.loginAttemptsResetTime = loginAttemptsResetTime;
    }

    public Integer getAccountBlockedDuration() {
        return accountBlockedDuration;
    }

    public void setAccountBlockedDuration(Integer accountBlockedDuration) {
        this.accountBlockedDuration = accountBlockedDuration;
    }

    public boolean isCompleteRegistrationWhenResetPassword() {
        return completeRegistrationWhenResetPassword;
    }

    public void setCompleteRegistrationWhenResetPassword(boolean completeRegistrationWhenResetPassword) {
        this.completeRegistrationWhenResetPassword = completeRegistrationWhenResetPassword;
    }

    public boolean isAutoLoginAfterRegistration() {
        return autoLoginAfterRegistration;
    }

    public void setAutoLoginAfterRegistration(boolean autoLoginAfterRegistration) {
        this.autoLoginAfterRegistration = autoLoginAfterRegistration;
    }

    public String getRedirectUriAfterRegistration() {
        return redirectUriAfterRegistration;
    }

    public void setRedirectUriAfterRegistration(String redirectUriAfterRegistration) {
        this.redirectUriAfterRegistration = redirectUriAfterRegistration;
    }

    public boolean isDynamicUserRegistration() {
        return dynamicUserRegistration;
    }

    public void setDynamicUserRegistration(boolean dynamicUserRegistration) {
        this.dynamicUserRegistration = dynamicUserRegistration;
    }

    public boolean isAutoLoginAfterResetPassword() {
        return autoLoginAfterResetPassword;
    }

    public void setAutoLoginAfterResetPassword(boolean autoLoginAfterResetPassword) {
        this.autoLoginAfterResetPassword = autoLoginAfterResetPassword;
    }

    public String getRedirectUriAfterResetPassword() {
        return redirectUriAfterResetPassword;
    }

    public void setRedirectUriAfterResetPassword(String redirectUriAfterResetPassword) {
        this.redirectUriAfterResetPassword = redirectUriAfterResetPassword;
    }

    public AccountSettings convert() {
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setInherited(isInherited());
        accountSettings.setLoginAttemptsDetectionEnabled(isLoginAttemptsDetectionEnabled());
        accountSettings.setMaxLoginAttempts(getMaxLoginAttempts());
        accountSettings.setLoginAttemptsResetTime(getLoginAttemptsResetTime());
        accountSettings.setAccountBlockedDuration(getAccountBlockedDuration());
        accountSettings.setCompleteRegistrationWhenResetPassword(isCompleteRegistrationWhenResetPassword());
        accountSettings.setAutoLoginAfterRegistration(isAutoLoginAfterRegistration());
        accountSettings.setRedirectUriAfterRegistration(getRedirectUriAfterRegistration());
        accountSettings.setDynamicUserRegistration(isDynamicUserRegistration());
        accountSettings.setAutoLoginAfterResetPassword(isAutoLoginAfterResetPassword());
        accountSettings.setRedirectUriAfterResetPassword(getRedirectUriAfterResetPassword());
        return accountSettings;
    }

    public static AccountSettingsMongo convert(AccountSettings accountSettings) {
        if (accountSettings == null) {
            return null;
        }
        AccountSettingsMongo accountSettingsMongo = new AccountSettingsMongo();
        accountSettingsMongo.setInherited(accountSettings.isInherited());
        accountSettingsMongo.setLoginAttemptsDetectionEnabled(accountSettings.isLoginAttemptsDetectionEnabled());
        accountSettingsMongo.setMaxLoginAttempts(accountSettings.getMaxLoginAttempts());
        accountSettingsMongo.setLoginAttemptsResetTime(accountSettings.getLoginAttemptsResetTime());
        accountSettingsMongo.setAccountBlockedDuration(accountSettings.getAccountBlockedDuration());
        accountSettingsMongo.setCompleteRegistrationWhenResetPassword(accountSettings.isCompleteRegistrationWhenResetPassword());
        accountSettingsMongo.setAutoLoginAfterRegistration(accountSettings.isAutoLoginAfterRegistration());
        accountSettingsMongo.setRedirectUriAfterRegistration(accountSettings.getRedirectUriAfterRegistration());
        accountSettingsMongo.setDynamicUserRegistration(accountSettings.isDynamicUserRegistration());
        accountSettingsMongo.setAutoLoginAfterResetPassword(accountSettings.isAutoLoginAfterResetPassword());
        accountSettingsMongo.setRedirectUriAfterResetPassword(accountSettings.getRedirectUriAfterResetPassword());
        return accountSettingsMongo;
    }
}
