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
 * Automation API mirror of {@code io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationRiskAssessmentSettings", title = "Risk assessment settings",
        description = "Risk assessment of the authentication attempts, used by risk-based MFA. Effective only with " +
                "the Enterprise Edition risk assessment plugin.")
public class AutomationRiskAssessmentSettings {

    @Schema(description = "Whether risk assessment is enabled.", defaultValue = "false")
    private boolean enabled;

    @Schema(description = "Assessment of the device used to authenticate (unknown devices).")
    private AutomationAssessmentSettings deviceAssessment;

    @Schema(description = "Assessment of the reputation of the IP address used to authenticate.")
    private AutomationAssessmentSettings ipReputationAssessment;

    @Schema(description = "Assessment of the travel speed between two consecutive authentications (geo-velocity).")
    private AutomationAssessmentSettings geoVelocityAssessment;
}
