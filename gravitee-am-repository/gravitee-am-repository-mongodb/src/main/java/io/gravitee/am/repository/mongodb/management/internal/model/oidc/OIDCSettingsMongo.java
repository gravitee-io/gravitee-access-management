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
package io.gravitee.am.repository.mongodb.management.internal.model.oidc;

import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class OIDCSettingsMongo {

    private ClientRegistrationSettingsMongo clientRegistrationSettings;
    private SecurityProfileSettingsMongo securityProfileSettings;
    private boolean redirectUriStrictMatching;
    private List<String> postLogoutRedirectUris;
    private List<String> requestUris;
    private CIBASettingsMongo cibaSettings;
    private TokenExchangeSettingsMongo tokenExchangeSettings;

    public ClientRegistrationSettingsMongo getClientRegistrationSettings() {
        return clientRegistrationSettings;
    }

    public void setClientRegistrationSettings(ClientRegistrationSettingsMongo clientRegistrationSettings) {
        this.clientRegistrationSettings = clientRegistrationSettings;
    }

    public boolean isRedirectUriStrictMatching() {
        return redirectUriStrictMatching;
    }

    public void setRedirectUriStrictMatching(boolean redirectUriStrictMatching) {
        this.redirectUriStrictMatching = redirectUriStrictMatching;
    }

    public List<String> getPostLogoutRedirectUris() {
        return postLogoutRedirectUris;
    }

    public void setPostLogoutRedirectUris(List<String> postLogoutRedirectUris) {
        this.postLogoutRedirectUris = postLogoutRedirectUris;
    }

    public List<String> getRequestUris() {
        return requestUris;
    }

    public void setRequestUris(List<String> requestUris) {
        this.requestUris = requestUris;
    }

    public SecurityProfileSettingsMongo getSecurityProfileSettings() {
        return securityProfileSettings;
    }

    public void setSecurityProfileSettings(SecurityProfileSettingsMongo securityProfileSettings) {
        this.securityProfileSettings = securityProfileSettings;
    }

    public CIBASettingsMongo getCibaSettings() {
        return cibaSettings;
    }

    public void setCibaSettings(CIBASettingsMongo cibaSettings) {
        this.cibaSettings = cibaSettings;
    }

    public TokenExchangeSettingsMongo getTokenExchangeSettings() {
        return tokenExchangeSettings;
    }

    public void setTokenExchangeSettings(TokenExchangeSettingsMongo tokenExchangeSettings) {
        this.tokenExchangeSettings = tokenExchangeSettings;
    }
}
