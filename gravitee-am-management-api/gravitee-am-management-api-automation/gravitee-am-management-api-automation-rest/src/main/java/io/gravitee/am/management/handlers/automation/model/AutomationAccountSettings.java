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
public class AutomationAccountSettings {

    private boolean inherited = true;

    private boolean loginAttemptsDetectionEnabled;
    private Integer maxLoginAttempts;
    private Integer loginAttemptsResetTime;
    private Integer accountBlockedDuration;

    private boolean sendRecoverAccountEmail;
    private boolean sendVerifyRegistrationAccountEmail;
    private boolean completeRegistrationWhenResetPassword;

    private boolean autoLoginAfterRegistration;
    private String redirectUriAfterRegistration;
    private boolean dynamicUserRegistration;

    /**
     * The {@code key} of an identity provider that exists under this domain, to use as the
     * default for user registration. Resolved against the domain's IdPs at apply time; a
     * value that does not match any existing IdP is rejected with {@code 400}. May be null.
     */
    @Schema(description = "key of an identity provider that exists under this domain")
    private String defaultIdentityProviderForRegistration;

    private boolean autoLoginAfterResetPassword;
    private String redirectUriAfterResetPassword;
    private boolean deletePasswordlessDevicesAfterResetPassword;

    private boolean rememberMe;
    private Integer rememberMeDuration;

    private boolean resetPasswordCustomForm;
    private List<FormField> resetPasswordCustomFormFields;
    private boolean resetPasswordConfirmIdentity;
    private boolean resetPasswordInvalidateTokens;

    private boolean mfaChallengeAttemptsDetectionEnabled;
    private Integer mfaChallengeMaxAttempts;
    private Integer mfaChallengeAttemptsResetTime;
    private boolean mfaChallengeSendVerifyAlertEmail;
}
