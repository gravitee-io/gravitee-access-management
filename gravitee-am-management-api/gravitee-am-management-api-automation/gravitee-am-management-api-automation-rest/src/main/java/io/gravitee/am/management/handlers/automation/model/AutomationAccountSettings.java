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
package io.gravitee.am.management.handlers.automation.model;

import io.gravitee.am.model.account.FormField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Automation API mirror of {@link io.gravitee.am.model.account.AccountSettings}, wrapped
 * because {@link #defaultIdentityProviderForRegistration} is a cross-resource reference
 * expressed in the key-only contract: the {@code key} of an identity provider that exists
 * under this domain (created via the identity-providers endpoints, resolved against the IdP
 * rows at apply time). A reference to an IdP that does not exist is rejected with {@code 400}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationAccountSettings", title = "Account settings",
        description = "User account settings for the domain: brute-force protection, registration, " +
                "password reset, remember-me, and MFA challenge behavior.")
public class AutomationAccountSettings {

    @Schema(description = "Whether account settings are inherited from the parent (domain). " +
            "When true, the other fields are ignored. Has no effect when applied to domains.", defaultValue = "true")
    private boolean inherited = true;

    @Schema(description = "Whether brute-force authentication attempts are detected and blocked.",
            defaultValue = "false")
    private boolean loginAttemptsDetectionEnabled;

    @Schema(description = "Maximum number of failed login attempts before the account is blocked.", example = "10")
    private Integer maxLoginAttempts;

    @Schema(description = "Time, in seconds, after which the login attempt counter is reset when the maximum " +
            "has not been reached.", example = "600")
    private Integer loginAttemptsResetTime;

    @Schema(description = "Duration, in seconds, for which the account remains blocked after too many failed " +
            "login attempts.", example = "7200")
    private Integer accountBlockedDuration;

    @Schema(description = "Whether to send an account-recovery email.", defaultValue = "false")
    private boolean sendRecoverAccountEmail;

    @Schema(description = "Whether to send a registration-verification email.", defaultValue = "false")
    private boolean sendVerifyRegistrationAccountEmail;

    @Schema(description = "Whether resetting a password also completes a pending registration.",
            defaultValue = "false")
    private boolean completeRegistrationWhenResetPassword;

    @Schema(description = "Whether the user is automatically logged in after completing registration.",
            defaultValue = "false")
    private boolean autoLoginAfterRegistration;

    @Schema(description = "URL the user is redirected to after registration.",
            example = "https://app.example.com/welcome")
    private String redirectUriAfterRegistration;

    @Schema(description = "Whether dynamic (self-service) user registration is enabled.", defaultValue = "false")
    private boolean dynamicUserRegistration;

    /**
     * The {@code key} of an identity provider that exists under this domain, to use as the
     * default for user registration. Resolved against the domain's IdPs at apply time; a
     * value that does not match any existing IdP is rejected with {@code 400}. May be null.
     */
    @Schema(description = "Key of an identity provider that exists under this domain, used as the default " +
            "for user registration. Resolved against the domain's identity providers when applied; a value " +
            "that does not match an existing identity provider is rejected with a 400 response.",
            example = "users-idp")
    private String defaultIdentityProviderForRegistration;

    @Schema(description = "Whether the user is automatically logged in after a password reset.",
            defaultValue = "false")
    private boolean autoLoginAfterResetPassword;

    @Schema(description = "URL the user is redirected to after a password reset.",
            example = "https://app.example.com/signin")
    private String redirectUriAfterResetPassword;

    @Schema(description = "Whether passwordless (WebAuthn) devices are deleted when the password is reset.",
            defaultValue = "false")
    private boolean deletePasswordlessDevicesAfterResetPassword;

    @Schema(description = "Whether users can remain logged in for a fixed duration (remember-me).",
            defaultValue = "false")
    private boolean rememberMe;

    @Schema(description = "Duration, in seconds, for which a remembered session stays valid.", example = "604800")
    private Integer rememberMeDuration;

    @Schema(description = "Whether a custom form is used for the password-reset step.", defaultValue = "false")
    private boolean resetPasswordCustomForm;

    @Schema(description = "Custom fields rendered on the password-reset form.")
    private List<FormField> resetPasswordCustomFormFields;

    @Schema(description = "Whether the user must confirm their identity before resetting a password.",
            defaultValue = "false")
    private boolean resetPasswordConfirmIdentity;

    @Schema(description = "Whether existing tokens are invalidated when the password is reset.",
            defaultValue = "false")
    private boolean resetPasswordInvalidateTokens;

    @Schema(description = "Whether failed MFA challenge attempts are detected and blocked.", defaultValue = "false")
    private boolean mfaChallengeAttemptsDetectionEnabled;

    @Schema(description = "Maximum number of failed MFA challenge attempts before the user is blocked.",
            example = "3")
    private Integer mfaChallengeMaxAttempts;

    @Schema(description = "Time, in seconds, after which the MFA challenge attempt counter is reset.",
            example = "600")
    private Integer mfaChallengeAttemptsResetTime;

    @Schema(description = "Whether to send an alert email after too many failed MFA challenge attempts.",
            defaultValue = "false")
    private boolean mfaChallengeSendVerifyAlertEmail;
}
