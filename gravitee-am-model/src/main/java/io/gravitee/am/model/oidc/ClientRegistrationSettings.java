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
package io.gravitee.am.model.oidc;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Schema(title = "Client registration settings", description = "OpenID Connect Dynamic Client Registration " +
        "configuration for the domain.")
public class ClientRegistrationSettings {

    @Schema(description = "Whether localhost is permitted as a redirect URI host.", defaultValue = "false")
    private boolean allowLocalhostRedirectUri;

    @Schema(description = "Whether the unsecured http scheme is permitted in redirect URIs.",
            defaultValue = "false")
    private boolean allowHttpSchemeRedirectUri;

    @Schema(description = "Whether wildcards are permitted in redirect URIs.", defaultValue = "false")
    private boolean allowWildCardRedirectUri;

    @Schema(description = "Whether Dynamic Client Registration is enabled for the domain.", defaultValue = "false")
    private boolean isDynamicClientRegistrationEnabled;

    @Schema(description = "Whether open (unauthenticated) Dynamic Client Registration is enabled for the domain.",
            defaultValue = "false")
    private boolean isOpenDynamicClientRegistrationEnabled;

    @Schema(description = "Whether expression language is permitted in redirect URI parameters.",
            defaultValue = "false")
    private boolean allowRedirectUriParamsExpressionLanguage;

    @Schema(description = "Default scopes added to every client registration request.")
    private List<String> defaultScopes;

    @Schema(description = "Whether registered client scopes are restricted to an allowed list.",
            defaultValue = "false")
    private boolean isAllowedScopesEnabled;

    @Schema(description = "Scopes permitted on client registration requests when the allowed list is enabled.")
    private List<String> allowedScopes;

    @Schema(description = "Whether a client may be used as a template for dynamic client registration.",
            defaultValue = "false")
    private boolean isClientTemplateEnabled;

    public ClientRegistrationSettings() {
    }

    public ClientRegistrationSettings(ClientRegistrationSettings other) {
        this.allowLocalhostRedirectUri = other.allowLocalhostRedirectUri;
        this.allowHttpSchemeRedirectUri = other.allowHttpSchemeRedirectUri;
        this.allowWildCardRedirectUri = other.allowWildCardRedirectUri;
        this.isDynamicClientRegistrationEnabled = other.isDynamicClientRegistrationEnabled;
        this.isOpenDynamicClientRegistrationEnabled = other.isOpenDynamicClientRegistrationEnabled;
        this.allowRedirectUriParamsExpressionLanguage = other.allowRedirectUriParamsExpressionLanguage;
        this.defaultScopes = other.defaultScopes != null ? new ArrayList<>(other.defaultScopes) : null;
        this.isAllowedScopesEnabled = other.isAllowedScopesEnabled;
        this.allowedScopes = other.allowedScopes != null ? new ArrayList<>(other.allowedScopes) : null;
        this.isClientTemplateEnabled = other.isClientTemplateEnabled;
    }

    public boolean isAllowLocalhostRedirectUri() {
        return allowLocalhostRedirectUri;
    }

    public void setAllowLocalhostRedirectUri(boolean allowLocalhostRedirectUri) {
        this.allowLocalhostRedirectUri = allowLocalhostRedirectUri;
    }

    public boolean isAllowHttpSchemeRedirectUri() {
        return allowHttpSchemeRedirectUri;
    }

    public void setAllowHttpSchemeRedirectUri(boolean allowHttpSchemeRedirectUri) {
        this.allowHttpSchemeRedirectUri = allowHttpSchemeRedirectUri;
    }

    public boolean isDynamicClientRegistrationEnabled() {
        return isDynamicClientRegistrationEnabled;
    }

    public void setDynamicClientRegistrationEnabled(boolean isDynamicClientRegistrationEnabled) {
        this.isDynamicClientRegistrationEnabled = isDynamicClientRegistrationEnabled;
    }

    public boolean isOpenDynamicClientRegistrationEnabled() {
        return isOpenDynamicClientRegistrationEnabled;
    }

    public void setOpenDynamicClientRegistrationEnabled(boolean isOpenDynamicClientRegistrationEnabled) {
        this.isOpenDynamicClientRegistrationEnabled = isOpenDynamicClientRegistrationEnabled;
    }

    public boolean isAllowWildCardRedirectUri() {
        return allowWildCardRedirectUri;
    }

    public void setAllowWildCardRedirectUri(boolean allowWildCardRedirectUri) {
        this.allowWildCardRedirectUri = allowWildCardRedirectUri;
    }

    public static ClientRegistrationSettings defaultSettings() {
        //By default all boolean are set to false.
        return new ClientRegistrationSettings();
    }

    public List<String> getDefaultScopes() {
        return defaultScopes;
    }

    public void setDefaultScopes(List<String> defaultScopes) {
        this.defaultScopes = defaultScopes;
    }

    public boolean isAllowedScopesEnabled() {
        return isAllowedScopesEnabled;
    }

    public void setAllowedScopesEnabled(boolean allowedScopesEnabled) {
        isAllowedScopesEnabled = allowedScopesEnabled;
    }

    public List<String> getAllowedScopes() {
        return allowedScopes;
    }

    public void setAllowedScopes(List<String> allowedScopes) {
        this.allowedScopes = allowedScopes;
    }

    public boolean isClientTemplateEnabled() {
        return isClientTemplateEnabled;
    }

    public void setClientTemplateEnabled(boolean clientTemplateEnabled) {
        isClientTemplateEnabled = clientTemplateEnabled;
    }

    public boolean isAllowRedirectUriParamsExpressionLanguage() {
        return allowRedirectUriParamsExpressionLanguage;
    }

    public void setAllowRedirectUriParamsExpressionLanguage(boolean allowRedirectUriParamsExpressionLanguage) {
        this.allowRedirectUriParamsExpressionLanguage = allowRedirectUriParamsExpressionLanguage;
    }
}
