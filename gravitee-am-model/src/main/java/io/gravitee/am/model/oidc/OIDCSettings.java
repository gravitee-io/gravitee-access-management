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

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OIDCSettings {

    private ClientRegistrationSettings clientRegistrationSettings;

    /**
     * Enable redirect_uri strict matching during OIDC flow (check for redirect_uri_mismatch exception)
     */
    private boolean redirectUriStrictMatching;

    public ClientRegistrationSettings getClientRegistrationSettings() {
        return clientRegistrationSettings!=null?clientRegistrationSettings: ClientRegistrationSettings.defaultSettings();
    }

    public void setClientRegistrationSettings(ClientRegistrationSettings clientRegistrationSettings) {
        this.clientRegistrationSettings = clientRegistrationSettings;
    }

    public boolean isRedirectUriStrictMatching() {
        return redirectUriStrictMatching;
    }

    public void setRedirectUriStrictMatching(boolean redirectUriStrictMatching) {
        this.redirectUriStrictMatching = redirectUriStrictMatching;
    }

    public static OIDCSettings defaultSettings() {
        OIDCSettings defaultSettings = new OIDCSettings();
        defaultSettings.setClientRegistrationSettings(ClientRegistrationSettings.defaultSettings());
        defaultSettings.setRedirectUriStrictMatching(false);
        return defaultSettings;
    }

}
