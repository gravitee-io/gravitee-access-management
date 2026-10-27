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

import io.gravitee.risk.assessment.api.assessment.Assessment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Automation API mirror of {@code io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationAssessmentSettings", title = "Assessment settings",
        description = "Settings of one risk assessment.")
public class AutomationAssessmentSettings {

    @Schema(description = "Whether this assessment is enabled.", defaultValue = "false")
    private boolean enabled;

    @Schema(description = "Score threshold of each risk level: an assessment scoring at or above a threshold " +
            "reaches that level.", example = "{\"HIGH\": 1.0}", additionalPropertiesSchema = Double.class)
    private Map<Assessment, Double> thresholds;
}
