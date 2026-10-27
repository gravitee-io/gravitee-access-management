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
 * Automation API mirror of {@link io.gravitee.am.model.StepUpAuthenticationSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationStepUpAuthenticationSettings", title = "Step-up authentication settings",
        description = "Step-up authentication: asks for MFA again during a session when its rule matches.")
public class AutomationStepUpAuthenticationSettings {

    @Schema(description = "Whether step-up authentication is enabled.", defaultValue = "false")
    private boolean active;

    @Schema(description = "Expression Language rule: when it evaluates to true, the user is challenged again.",
            example = "{#request.params['scope'][0].contains('write')}")
    private String stepUpAuthenticationRule;
}
