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

import io.gravitee.am.model.MfaChallengeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Automation API mirror of {@link io.gravitee.am.model.ChallengeSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationChallengeSettings", title = "Challenge settings",
        description = "When users are challenged for a second factor.")
public class AutomationChallengeSettings {

    @Schema(description = "Whether users are challenged for a second factor.", defaultValue = "false")
    private boolean active;

    @Schema(description = "Expression Language rule of a conditional challenge: when it evaluates to true, the " +
            "challenge is skipped.", example = "{#context.attributes['geoip']['country_iso_code'] == 'FR'}")
    private String challengeRule;

    @Schema(description = "required: always challenge. conditional: challenge unless challengeRule evaluates to " +
            "true. risk_based: challenge according to the risk assessment.", example = "required")
    private MfaChallengeType type;
}
