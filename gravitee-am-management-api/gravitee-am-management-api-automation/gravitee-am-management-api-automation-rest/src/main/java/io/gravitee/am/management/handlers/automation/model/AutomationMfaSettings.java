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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Automation API mirror of {@link io.gravitee.am.model.MFASettings}.
 * <p>
 * Factors and the device identifier have no Automation API endpoint: they are referenced by
 * {@code id:<uuid>} only.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationMfaSettings", title = "MFA settings",
        description = "Multi-factor authentication settings of the application.")
public class AutomationMfaSettings {

    @Schema(description = "Legacy MFA login rule, kept for round-trip fidelity. Use challenge and enroll to " +
            "configure when MFA applies.")
    private String loginRule;

    @Schema(description = "Factors offered to the users of the application.")
    private AutomationFactorSettings factor;

    @Deprecated
    @Schema(description = "Deprecated: use stepUpAuthentication. Expression Language rule triggering step-up " +
            "authentication.", deprecated = true)
    private String stepUpAuthenticationRule;

    @Schema(description = "Step-up authentication: asks for MFA again during a session when its rule matches.")
    private AutomationStepUpAuthenticationSettings stepUpAuthentication;

    @Deprecated
    @Schema(description = "Deprecated: use challenge.challengeRule. Expression Language rule of the adaptive " +
            "authentication.", deprecated = true)
    private String adaptiveAuthenticationRule;

    @Schema(description = "Remember device: skips the MFA challenge on a device already used to authenticate.")
    private AutomationRememberDeviceSettings rememberDevice;

    @Deprecated
    @Schema(description = "Deprecated: use enroll. Legacy enrollment settings.", deprecated = true)
    private AutomationEnrollmentSettings enrollment;

    @Schema(description = "When users are challenged for a second factor.")
    private AutomationChallengeSettings challenge;

    @Schema(description = "When users are asked to enroll a factor.")
    private AutomationEnrollSettings enroll;
}
