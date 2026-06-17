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
 * Automation API mirror of {@link io.gravitee.am.model.oidc.ClientRegistrationSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationClientRegistrationSettings", title = "Client registration settings",
        description = "OpenID Connect Dynamic Client Registration configuration for the domain.")
public class AutomationClientRegistrationSettings {

    @Schema(description = "Whether localhost is permitted as a redirect URI host.", defaultValue = "false")
    private boolean allowLocalhostRedirectUri;

    @Schema(description = "Whether the unsecured http scheme is permitted in redirect URIs.",
            defaultValue = "false")
    private boolean allowHttpSchemeRedirectUri;

    @Schema(description = "Whether wildcards are permitted in redirect URIs.", defaultValue = "false")
    private boolean allowWildCardRedirectUri;

    @Schema(description = "Whether expression language is permitted in redirect URI parameters.",
            defaultValue = "false")
    private boolean allowRedirectUriParamsExpressionLanguage;

    @Schema(description = "Whether Dynamic Client Registration is enabled for the domain.", defaultValue = "false")
    private boolean dynamicClientRegistrationEnabled;

    @Schema(description = "Whether open (unauthenticated) Dynamic Client Registration is enabled for the domain.",
            defaultValue = "false")
    private boolean openDynamicClientRegistrationEnabled;

    @Schema(description = "Default scopes added to every client registration request.")
    private List<String> defaultScopes;

    @Schema(description = "Whether registered client scopes are restricted to an allowed list.",
            defaultValue = "false")
    private boolean allowedScopesEnabled;

    @Schema(description = "Scopes permitted on client registration requests when the allowed list is enabled.")
    private List<String> allowedScopes;

    @Schema(description = "Whether a client may be used as a template for dynamic client registration.",
            defaultValue = "false")
    private boolean clientTemplateEnabled;
}
