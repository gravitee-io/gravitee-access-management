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
package io.gravitee.am.model.account;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountSettings {

    /**
     * Account settings configuration inherited ?
     */
    private boolean inherited = true;

    /**
     * Enable/Disable authentication brut force attempts detection feature
     */
    private boolean loginAttemptsDetectionEnabled;
    /**
     * Max login failures after which the account will be blocked
     */
    private Integer maxLoginAttempts;
    /**
     * Amount of time after which the user login attempts will be erased if max attempts has not been reached
     */
    private Integer loginAttemptsResetTime;
    /**
     * Amount of time while the user will be blocked
     */
    private Integer accountBlockedDuration;

    /**
     * Complete user registration when the user renewed his password
     */
    private boolean completeRegistrationWhenResetPassword;

    /**
     * Auto login user after registration process
     */
    private boolean autoLoginAfterRegistration;

    /**
     * The redirect URI after registration process
     */
    private String redirectUriAfterRegistration;

    /**
     * Add user registration url and user registration access token if this option is enabled
     */
    private boolean dynamicUserRegistration;

    /**
     * Auto login user after reset password process
     */
    private boolean autoLoginAfterResetPassword;

    /**
     * The redirect URI after reset password process
     */
    private String redirectUriAfterResetPassword;

    public AccountSettings() {
    }

    public AccountSettings(AccountSettings other) {
        this.inherited = other.inherited;
        this.loginAttemptsDetectionEnabled = other.loginAttemptsDetectionEnabled;
        this.maxLoginAttempts = other.maxLoginAttempts;
        this.loginAttemptsResetTime = other.loginAttemptsResetTime;
        this.accountBlockedDuration = other.accountBlockedDuration;
        this.completeRegistrationWhenResetPassword = other.completeRegistrationWhenResetPassword;
        this.autoLoginAfterRegistration = other.autoLoginAfterRegistration;
        this.redirectUriAfterRegistration = other.redirectUriAfterRegistration;
        this.autoLoginAfterResetPassword = other.autoLoginAfterResetPassword;
        this.redirectUriAfterResetPassword = other.redirectUriAfterResetPassword;
    }

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
}
