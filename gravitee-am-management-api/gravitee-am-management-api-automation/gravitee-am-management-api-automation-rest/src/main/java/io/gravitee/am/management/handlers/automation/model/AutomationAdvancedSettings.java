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
 * Automation API mirror of {@link io.gravitee.am.model.application.ApplicationAdvancedSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationAdvancedSettings", title = "Advanced settings",
        description = "Advanced application settings.")
public class AutomationAdvancedSettings {

    @Schema(description = "Whether the user consent page is skipped: requested scopes are granted without asking " +
            "the user.", defaultValue = "false")
    private boolean skipConsent;

    @Schema(description = "Whether the domain flows are executed for this application in addition to its own " +
            "flows.", defaultValue = "true")
    private boolean flowsInherited = true;
}
