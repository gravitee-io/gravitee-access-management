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
 * Automation API mirror of {@link io.gravitee.am.model.application.ApplicationScopeSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationScopeSettings", title = "Scope settings",
        description = "A scope the application can request, with its settings.")
public class AutomationScopeSettings {

    @NotNull
    @Schema(description = "Key of a scope of the domain. The scope must exist, otherwise the request is rejected " +
            "with 400.", example = "openid")
    private String scope;

    @Schema(description = "Whether the scope is granted when the authorization request carries no scope " +
            "parameter.", defaultValue = "false")
    private boolean defaultScope;

    @Schema(description = "Duration, in seconds, of the user consent to this scope. When absent, the domain scope " +
            "setting applies.", example = "86400")
    private Integer scopeApproval;

    @Schema(description = "Whether the user cannot deselect this scope on the consent page.", defaultValue = "false")
    private boolean requiredScope;
}
