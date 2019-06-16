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

import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class ClientRegistrationSettings {

    /**
     * Allow localhost host as redirect_uri
     */
    private boolean allowLocalhostRedirectUri;

    /**
     * Allow unsecured http scheme into redirect_uri
     */
    private boolean allowHttpSchemeRedirectUri;

    /**
     * Allow wildcard redirect uri
     */
    private boolean allowWildCardRedirectUri;

    /**
     * Domain Dynamic Client Registration enabled
     */
    private boolean isDynamicClientRegistrationEnabled;

    /**
     * Domain open Dynamic Client Registration enabled
     */
    private boolean isOpenDynamicClientRegistrationEnabled;

    /**
     * Define some default scopes to add on each client registration request
     */
    private List<String> defaultScopes;

    /**
     * Filter scopes on Client Registration through an allowed list of scopes enabled.
     */
    private boolean isAllowedScopesEnabled;

    /**
     * Define allowed scopes for each client registration request
     */
    private List<String> allowedScopes;


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
}
