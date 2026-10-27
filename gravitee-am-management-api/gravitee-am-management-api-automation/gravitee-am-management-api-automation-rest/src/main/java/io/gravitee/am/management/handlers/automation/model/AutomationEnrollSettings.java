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

import io.gravitee.am.model.MfaEnrollType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Automation API mirror of {@link io.gravitee.am.model.EnrollSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationEnrollSettings", title = "Enroll settings",
        description = "When users are asked to enroll a factor.")
public class AutomationEnrollSettings {

    @Schema(description = "Whether users are asked to enroll a factor.", defaultValue = "false")
    private boolean active;

    @Schema(description = "Whether users must enroll a factor.")
    private Boolean forceEnrollment;

    @Schema(description = "Expression Language rule of a conditional enrollment: when it evaluates to true, the " +
            "enrollment is skipped.", example = "{#context.attributes['user'].roles.contains('guest')}")
    private String enrollmentRule;

    @Schema(description = "Whether users can skip the enrollment.", defaultValue = "false")
    private boolean enrollmentSkipActive;

    @Schema(description = "Expression Language rule: when it evaluates to true, users can skip the enrollment.")
    private String enrollmentSkipRule;

    @Schema(description = "Duration, in seconds, during which a skipped enrollment is not asked again.",
            example = "36000")
    private Long skipTimeSeconds;

    @Schema(description = "optional: users can skip the enrollment. required: users must enroll. conditional: " +
            "enrollment required unless enrollmentRule evaluates to true.", example = "required")
    private MfaEnrollType type;
}
