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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OIDCSettings {

    private ClientRegistrationSettings clientRegistrationSettings;

    private SecurityProfileSettings securityProfileSettings;

    /**
     * Enable redirect_uri strict matching during OIDC flow (check for redirect_uri_mismatch exception)
     */
    private boolean redirectUriStrictMatching;

    /**
     * Array of URLs supplied by the RP to which it MAY request that the End-User's User Agent be redirected using the post_logout_redirect_uri parameter after a logout has been performed.
     */
    private List<String> postLogoutRedirectUris;

    /**
     * Array of URLs supplied by the RP to restrict the possible values of the request_uri parameter.
     * https://openid.net/specs/openid-connect-core-1_0.html#RequestUriParameter
     */
    private List<String> requestUris;

    private CIBASettings cibaSettings;

    private CIMDSettings cimdSettings;

    private SpiffeDomainSettings spiffeSettings;

    public OIDCSettings() {
    }

    public OIDCSettings(OIDCSettings other) {
        this.clientRegistrationSettings = other.clientRegistrationSettings != null
                ? new ClientRegistrationSettings(other.clientRegistrationSettings) : null;
        this.securityProfileSettings = other.securityProfileSettings != null
                ? new SecurityProfileSettings(other.securityProfileSettings) : null;
        this.redirectUriStrictMatching = other.redirectUriStrictMatching;
        this.postLogoutRedirectUris = other.postLogoutRedirectUris != null
                ? new ArrayList<>(other.postLogoutRedirectUris) : null;
        this.requestUris = other.requestUris != null ? new ArrayList<>(other.requestUris) : null;
        this.cibaSettings = other.cibaSettings != null ? new CIBASettings(other.cibaSettings) : null;
        this.cimdSettings = other.cimdSettings != null ? new CIMDSettings(other.cimdSettings) : null;
        this.spiffeSettings = other.spiffeSettings != null ? new SpiffeDomainSettings(other.spiffeSettings) : null;
    }

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

    public SecurityProfileSettings getSecurityProfileSettings() {
        return securityProfileSettings;
    }

    public void setSecurityProfileSettings(SecurityProfileSettings securityProfileSettings) {
        this.securityProfileSettings = securityProfileSettings;
    }

    public CIBASettings getCibaSettings() {
        return cibaSettings;
    }

    public void setCibaSettings(CIBASettings cibaSettings) {
        this.cibaSettings = cibaSettings;
    }

    public CIMDSettings getCimdSettings() {
        return cimdSettings;
    }

    public void setCimdSettings(CIMDSettings cimdSettings) {
        this.cimdSettings = cimdSettings;
    }

    public SpiffeDomainSettings getSpiffeSettings() {
        return spiffeSettings;
    }

    public void setSpiffeSettings(SpiffeDomainSettings spiffeSettings) {
        this.spiffeSettings = spiffeSettings;
    }

    public static OIDCSettings defaultSettings() {
        OIDCSettings defaultSettings = new OIDCSettings();
        defaultSettings.setClientRegistrationSettings(ClientRegistrationSettings.defaultSettings());
        defaultSettings.setSecurityProfileSettings(SecurityProfileSettings.defaultSettings());
        defaultSettings.setRedirectUriStrictMatching(false);
        defaultSettings.setCibaSettings(CIBASettings.defaultSettings());
        defaultSettings.setCimdSettings(CIMDSettings.defaultSettings());
        defaultSettings.setSpiffeSettings(SpiffeDomainSettings.defaultSettings());
        return defaultSettings;
    }

}
