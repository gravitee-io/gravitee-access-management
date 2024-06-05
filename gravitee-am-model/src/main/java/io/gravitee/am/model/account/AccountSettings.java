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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;

import java.util.List;
import java.util.Optional;

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
     * Enable/Disable email notification for blocked account
     */
    private boolean sendRecoverAccountEmail;

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
     * Default identity provider used for user registration
     */
    private String defaultIdentityProviderForRegistration;

    /**
     * Auto login user after reset password process
     */
    private boolean autoLoginAfterResetPassword;

    /**
     * The redirect URI after reset password process
     */
    private String redirectUriAfterResetPassword;

    /**
     * Delete passwordless devices after reset password process
     */
    private boolean deletePasswordlessDevicesAfterResetPassword;

    /**
     * true if BotDetection enabled
     */
    private boolean useBotDetection = false;

    /**
     * The bot detection plugin identifier used to protect pages
     */
    private String botDetectionPlugin;

    /**
     * Remember me option, to avoid to sign in again during a predefined amount of time
     */
    private boolean rememberMe;

    /**
     * Amount of time while the cookie session is stored
     */
    private Integer rememberMeDuration;

    /**
     * Used a custom form to generate ForgotPassword page
     */
    private boolean resetPasswordCustomForm;

    /**
     * Fields used to generate the custom form of ForgotPassword page
     */
    private List<FormField> resetPasswordCustomFormFields;

    /**
     * If true, display the legacy form for "forgot password" page and If multiple user are found, then the customForm will be displayed
     */
    private boolean resetPasswordConfirmIdentity;
    /**
     * If true, all tokens generated for the user are invalidated after a reset password
     */
    private boolean resetPasswordInvalidateTokens;

    /**
     * Enable/Disable MFA challenge brut force attempts detection feature
     */
    private boolean mfaChallengeAttemptsDetectionEnabled;

    /**
     * Allowed max MFA challenge attempts
     */
    private Integer mfaChallengeMaxAttempts;

    /**
     * Amount of time after which the user MFA challenge attempts will be erased if max challenge attempts has not been reached
     */
    private Integer mfaChallengeAttemptsResetTime;

    /**
     * Enable/Disable alert email due to too many MFA challenge
     */
    private boolean mfaChallengeSendVerifyAlertEmail;

    /**
     * Enable/Disable email after user self registration
     */
    private boolean sendVerifyRegistrationAccountEmail;


    public AccountSettings() {
    }

    public AccountSettings(AccountSettings other) {
        this.inherited = other.inherited;
        this.loginAttemptsDetectionEnabled = other.loginAttemptsDetectionEnabled;
        this.maxLoginAttempts = other.maxLoginAttempts;
        this.loginAttemptsResetTime = other.loginAttemptsResetTime;
        this.accountBlockedDuration = other.accountBlockedDuration;
        this.sendRecoverAccountEmail = other.sendRecoverAccountEmail;
        this.completeRegistrationWhenResetPassword = other.completeRegistrationWhenResetPassword;
        this.autoLoginAfterRegistration = other.autoLoginAfterRegistration;
        this.redirectUriAfterRegistration = other.redirectUriAfterRegistration;
        this.dynamicUserRegistration = other.dynamicUserRegistration;
        this.autoLoginAfterResetPassword = other.autoLoginAfterResetPassword;
        this.redirectUriAfterResetPassword = other.redirectUriAfterResetPassword;
        this.deletePasswordlessDevicesAfterResetPassword = other.deletePasswordlessDevicesAfterResetPassword;
        this.rememberMe = other.rememberMe;
        this.rememberMeDuration = other.rememberMeDuration;
        this.resetPasswordConfirmIdentity = other.resetPasswordConfirmIdentity;
        this.resetPasswordCustomForm = other.resetPasswordCustomForm;
        this.resetPasswordCustomFormFields = other.resetPasswordCustomFormFields;
        this.resetPasswordInvalidateTokens = other.resetPasswordInvalidateTokens;
        this.useBotDetection = other.useBotDetection;
        this.botDetectionPlugin = other.botDetectionPlugin;
        this.defaultIdentityProviderForRegistration = other.defaultIdentityProviderForRegistration;
        this.mfaChallengeAttemptsDetectionEnabled = other.mfaChallengeAttemptsDetectionEnabled;
        this.mfaChallengeMaxAttempts = other.mfaChallengeMaxAttempts;
        this.mfaChallengeAttemptsResetTime = other.mfaChallengeAttemptsResetTime;
        this.mfaChallengeSendVerifyAlertEmail = other.mfaChallengeSendVerifyAlertEmail;
        this.sendVerifyRegistrationAccountEmail = other.sendVerifyRegistrationAccountEmail;
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

    public boolean isSendRecoverAccountEmail() {
        return sendRecoverAccountEmail;
    }

    public void setSendRecoverAccountEmail(boolean sendRecoverAccountEmail) {
        this.sendRecoverAccountEmail = sendRecoverAccountEmail;
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

    public String getDefaultIdentityProviderForRegistration() {
        return defaultIdentityProviderForRegistration;
    }

    public void setDefaultIdentityProviderForRegistration(String defaultIdentityProviderForRegistration) {
        this.defaultIdentityProviderForRegistration = defaultIdentityProviderForRegistration;
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

    public boolean isDeletePasswordlessDevicesAfterResetPassword() {
        return deletePasswordlessDevicesAfterResetPassword;
    }

    public void setDeletePasswordlessDevicesAfterResetPassword(boolean deletePasswordlessDevicesAfterResetPassword) {
        this.deletePasswordlessDevicesAfterResetPassword = deletePasswordlessDevicesAfterResetPassword;
    }

    public boolean isUseBotDetection() {
        return useBotDetection;
    }

    public void setUseBotDetection(boolean useBotDetection) {
        this.useBotDetection = useBotDetection;
    }

    public String getBotDetectionPlugin() {
        return botDetectionPlugin;
    }

    public void setBotDetectionPlugin(String botDetectionPlugin) {
        this.botDetectionPlugin = botDetectionPlugin;
    }

    public boolean isResetPasswordCustomForm() {
        return resetPasswordCustomForm;
    }

    public void setResetPasswordCustomForm(boolean resetPasswordCustomForm) {
        this.resetPasswordCustomForm = resetPasswordCustomForm;
    }

    public List<FormField> getResetPasswordCustomFormFields() {
        return resetPasswordCustomFormFields;
    }

    public void setResetPasswordCustomFormFields(List<FormField> resetPasswordCustomFormFields) {
        this.resetPasswordCustomFormFields = resetPasswordCustomFormFields;
    }

    public boolean isResetPasswordConfirmIdentity() {
        return resetPasswordConfirmIdentity;
    }

    public void setResetPasswordConfirmIdentity(boolean resetPasswordConfirmIdentity) {
        this.resetPasswordConfirmIdentity = resetPasswordConfirmIdentity;
    }

    public boolean isResetPasswordInvalidateTokens() {
        return resetPasswordInvalidateTokens;
    }

    public void setResetPasswordInvalidateTokens(boolean resetPasswordInvalidateTokens) {
        this.resetPasswordInvalidateTokens = resetPasswordInvalidateTokens;
    }

    public boolean isMfaChallengeAttemptsDetectionEnabled() {
        return mfaChallengeAttemptsDetectionEnabled;
    }

    public void setMfaChallengeAttemptsDetectionEnabled(boolean mfaChallengeAttemptsDetectionEnabled) {
        this.mfaChallengeAttemptsDetectionEnabled = mfaChallengeAttemptsDetectionEnabled;
    }

    public Integer getMfaChallengeMaxAttempts() {
        return mfaChallengeMaxAttempts;
    }

    public void setMfaChallengeMaxAttempts(Integer mfaChallengeMaxAttempts) {
        this.mfaChallengeMaxAttempts = mfaChallengeMaxAttempts;
    }

    public Integer getMfaChallengeAttemptsResetTime() {
        return mfaChallengeAttemptsResetTime;
    }

    public void setMfaChallengeAttemptsResetTime(Integer mfaChallengeAttemptsResetTime) {
        this.mfaChallengeAttemptsResetTime = mfaChallengeAttemptsResetTime;
    }

    public boolean isMfaChallengeSendVerifyAlertEmail() {
        return mfaChallengeSendVerifyAlertEmail;
    }

    public void setMfaChallengeSendVerifyAlertEmail(boolean mfaChallengeSendVerifyAlertEmail) {
        this.mfaChallengeSendVerifyAlertEmail = mfaChallengeSendVerifyAlertEmail;
    }

    public boolean isSendVerifyRegistrationAccountEmail() {
        return sendVerifyRegistrationAccountEmail;
    }

    public void setSendVerifyRegistrationAccountEmail(boolean sendVerifyRegistrationAccountEmail) {
        this.sendVerifyRegistrationAccountEmail = sendVerifyRegistrationAccountEmail;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }

    public Integer getRememberMeDuration() {
        return rememberMeDuration;
    }

    public void setRememberMeDuration(Integer rememberMeDuration) {
        this.rememberMeDuration = rememberMeDuration;
    }

    public static Optional<AccountSettings> getInstance(Client client, Domain domain) {
        return Optional.ofNullable(getInstance(domain, client));
    }

    public static AccountSettings getInstance(Domain domain, Client client) {
        if (client == null) {
            return domain.getAccountSettings();
        }
        // if client has no account config return domain config
        if (client.getAccountSettings() == null) {
            return domain.getAccountSettings();
        }

        // if client configuration is not inherited return the client config
        if (!client.getAccountSettings().isInherited()) {
            return client.getAccountSettings();
        }

        // return domain config
        return domain.getAccountSettings();
    }

    public static AccountSettings getInstance(Domain domain, Application application) {
        if (application == null) {
            return domain.getAccountSettings();
        }
        // if client has no account config return domain config
        if (application.getSettings() == null) {
            return domain.getAccountSettings();
        }
        if (application.getSettings().getAccount() == null) {
            return domain.getAccountSettings();
        }
        // if client configuration is not inherited return the client config
        if (!application.getSettings().getAccount().isInherited()) {
            return application.getSettings().getAccount();
        }

        // return domain config
        return domain.getAccountSettings();
    }
}
