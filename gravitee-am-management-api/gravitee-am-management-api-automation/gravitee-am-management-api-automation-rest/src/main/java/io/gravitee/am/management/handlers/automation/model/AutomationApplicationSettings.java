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

import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Automation API view of {@link io.gravitee.am.model.application.ApplicationSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationApplicationSettings", title = "Application settings",
        description = "Settings of the application. The login, account and cookieSettings blocks, when omitted, " +
                "are inherited from the domain; any other omitted block is unset.")
public class AutomationApplicationSettings {

    @Schema(description = "OAuth 2.0 and OpenID Connect settings.")
    private AutomationApplicationOAuthSettings oauth;

    @Schema(description = "Login page settings. When omitted, inherited from the domain.")
    private LoginSettings login;

    @Schema(description = "User account settings. When omitted, inherited from the domain. " +
            "defaultIdentityProviderForRegistration references an identity provider by key or id:<uuid>.")
    private AutomationAccountSettings account;

    @Schema(description = "Session cookie settings. When omitted, inherited from the domain.")
    private AutomationCookieSettings cookieSettings;

    @Schema(description = "Multi-factor authentication settings.")
    private AutomationMfaSettings mfa;

    @Schema(description = "SAML 2.0 service provider settings.")
    private AutomationApplicationSAMLSettings saml;

    @Schema(description = "Advanced settings.")
    private AutomationAdvancedSettings advanced;

    @Schema(description = "Risk assessment settings. Effective only with the Enterprise Edition risk assessment plugin.")
    private AutomationRiskAssessmentSettings riskAssessment;

    @Schema(description = "Expiration of the client secrets of the application. When omitted, the domain " +
            "settings apply.")
    private SecretExpirationSettings secretExpirationSettings;

    @Schema(description = "SPIFFE workload identity settings.")
    private AutomationWorkloadIdentitySettings workloadIdentitySettings;
}
