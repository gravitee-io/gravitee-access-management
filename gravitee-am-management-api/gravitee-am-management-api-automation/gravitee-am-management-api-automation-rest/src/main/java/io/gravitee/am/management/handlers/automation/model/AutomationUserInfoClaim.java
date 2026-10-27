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
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Automation API mirror of {@link io.gravitee.am.model.UserInfoClaim}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationUserInfoClaim", title = "UserInfo claim",
        description = "A custom claim added to the UserInfo responses of the application.")
public class AutomationUserInfoClaim {

    @NotNull
    @Schema(description = "Name of the claim.", example = "department")
    private String claimName;

    @NotNull
    @Schema(description = "Value of the claim: a literal or an Expression Language expression.",
            example = "{#context.attributes['user']['additionalInformation']['department']}")
    private String claimValue;
}
