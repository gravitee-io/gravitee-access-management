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
 * Automation API representation of an identity provider enabled on an application.
 * <p>
 * {@code identity} references an identity provider of the same domain by {@code key} or by
 * {@code id:<uuid>}. The reference is eventually consistent: it is stored as given and never checked
 * against the domain's identity providers.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationApplicationIdentityProvider", title = "Application identity provider",
        description = "An identity provider users can authenticate with on this application.")
public class AutomationApplicationIdentityProvider {

    @NotNull
    @Schema(description = "Reference to an identity provider of the domain: its key, or id:<uuid> for one the " +
            "Automation API does not manage. The reference is not checked against existing identity providers: " +
            "it can name one created after the application or since deleted, and resolves whenever that identity " +
            "provider exists.",
            example = "customer-users")
    private String identity;

    @Schema(description = "Expression Language rule selecting this identity provider for the current request. " +
            "When empty, the identity provider is always selectable.",
            example = "{#request.params['idp'][0] == 'customers'}")
    private String selectionRule;

    @Schema(description = "Evaluation order among the application's identity providers: lowest first; negative " +
            "values come last.", defaultValue = "0")
    private int priority;
}
