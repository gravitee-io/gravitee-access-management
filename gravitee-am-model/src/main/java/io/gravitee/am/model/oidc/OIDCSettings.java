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
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Schema(title = "OpenID Connect settings", description = "OpenID Connect configuration for the domain, including " +
        "dynamic client registration, security profile, and logout settings.")
public class OIDCSettings {

    @Schema(description = "Dynamic Client Registration settings for the domain.")
    private ClientRegistrationSettings clientRegistrationSettings;

    @Schema(description = "Security profile settings (for example, FAPI) for the domain.")
    private SecurityProfileSettings securityProfileSettings;

    @Schema(description = "Whether redirect_uri values are matched strictly during OIDC flows.",
            defaultValue = "false")
    private boolean redirectUriStrictMatching;

    @Schema(description = "Demonstrating Proof-of-Possession (RFC 9449) settings for the domain.")
    private DPoPSettings dpopSettings;

    @Schema(description = "URLs to which a relying party may request the user be redirected after logout, via " +
            "the post_logout_redirect_uri parameter.")
    private List<String> postLogoutRedirectUris;

    @Schema(description = "URLs that restrict the permitted values of the request_uri parameter.")
    private List<String> requestUris;

    private CIBASettings cibaSettings;

    private CIMDSettings cimdSettings;

    private SpiffeDomainSettings workloadIdentitySettings;

    public OIDCSettings() {
    }

    public OIDCSettings(OIDCSettings other) {
        this.clientRegistrationSettings = other.clientRegistrationSettings != null
                ? new ClientRegistrationSettings(other.clientRegistrationSettings) : null;
        this.securityProfileSettings = other.securityProfileSettings != null
                ? new SecurityProfileSettings(other.securityProfileSettings) : null;
        this.redirectUriStrictMatching = other.redirectUriStrictMatching;
        this.dpopSettings = other.dpopSettings != null ? new DPoPSettings(other.dpopSettings) : null;
        this.postLogoutRedirectUris = other.postLogoutRedirectUris != null
                ? new ArrayList<>(other.postLogoutRedirectUris) : null;
        this.requestUris = other.requestUris != null ? new ArrayList<>(other.requestUris) : null;
        this.cibaSettings = other.cibaSettings != null ? new CIBASettings(other.cibaSettings) : null;
        this.cimdSettings = other.cimdSettings != null ? new CIMDSettings(other.cimdSettings) : null;
        this.workloadIdentitySettings = other.workloadIdentitySettings != null ? new SpiffeDomainSettings(other.workloadIdentitySettings) : null;
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

    public DPoPSettings getDpopSettings() {
        return dpopSettings;
    }

    public void setDpopSettings(DPoPSettings dpopSettings) {
        this.dpopSettings = dpopSettings;
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

    public SpiffeDomainSettings getWorkloadIdentitySettings() {
        return workloadIdentitySettings;
    }

    public void setWorkloadIdentitySettings(SpiffeDomainSettings workloadIdentitySettings) {
        this.workloadIdentitySettings = workloadIdentitySettings;
    }

    public static OIDCSettings defaultSettings() {
        OIDCSettings defaultSettings = new OIDCSettings();
        defaultSettings.setClientRegistrationSettings(ClientRegistrationSettings.defaultSettings());
        defaultSettings.setSecurityProfileSettings(SecurityProfileSettings.defaultSettings());
        defaultSettings.setRedirectUriStrictMatching(false);
        defaultSettings.setDpopSettings(DPoPSettings.defaultSettings());
        defaultSettings.setCibaSettings(CIBASettings.defaultSettings());
        defaultSettings.setCimdSettings(CIMDSettings.defaultSettings());
        defaultSettings.setWorkloadIdentitySettings(SpiffeDomainSettings.defaultSettings());
        return defaultSettings;
    }

}
