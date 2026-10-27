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

import java.util.List;

/**
 * Automation API mirror of {@link io.gravitee.am.model.FactorSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationFactorSettings", title = "Factor settings",
        description = "Factors offered to the users of the application.")
public class AutomationFactorSettings {

    @Schema(description = "Factor proposed first, as id:<uuid> (factors have no Automation API endpoint).",
            example = "id:5e8f1c0a-2b3d-4f5e-8a9b-0c1d2e3f4a5b")
    private String defaultFactorId;

    @Schema(description = "Factors available to the application.")
    private List<AutomationApplicationFactorSettings> applicationFactors;
}
