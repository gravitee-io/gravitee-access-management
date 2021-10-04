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
package io.gravitee.am.service.model.openid;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SecurityProfileSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.utils.SetterUtils;
import io.netty.channel.ChannelInboundInvoker;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class PatchOIDCSettings {

    public PatchOIDCSettings() {}

    @JsonProperty("clientRegistrationSettings")
    private Optional<PatchClientRegistrationSettings> clientRegistrationSettings;

    @JsonProperty("securityProfileSettings")
    private Optional<PatchSecurityProfileSettings> securityProfileSettings;

    @JsonProperty("cibaSettings")
    private Optional<PatchCIBASettings> cibaSettings;

    private Optional<Boolean> redirectUriStrictMatching;

    private Optional<List<String>> postLogoutRedirectUris;

    public Optional<PatchClientRegistrationSettings> getClientRegistrationSettings() {
        return clientRegistrationSettings;
    }

    public void setClientRegistrationSettings(Optional<PatchClientRegistrationSettings> clientRegistrationSettings) {
        this.clientRegistrationSettings = clientRegistrationSettings;
    }

    public Optional<PatchSecurityProfileSettings> getSecurityProfileSettings() {
        return securityProfileSettings;
    }

    public void setSecurityProfileSettings(Optional<PatchSecurityProfileSettings> securityProfileSettings) {
        this.securityProfileSettings = securityProfileSettings;
    }

    public Optional<Boolean> getRedirectUriStrictMatching() {
        return redirectUriStrictMatching;
    }

    public void setRedirectUriStrictMatching(Optional<Boolean> redirectUriStrictMatching) {
        this.redirectUriStrictMatching = redirectUriStrictMatching;
    }

    public Optional<List<String>> getPostLogoutRedirectUris() {
        return postLogoutRedirectUris;
    }

    public void setPostLogoutRedirectUris(Optional<List<String>> postLogoutRedirectUris) {
        this.postLogoutRedirectUris = postLogoutRedirectUris;
    }

    public Optional<PatchCIBASettings> getCibaSettings() {
        return cibaSettings;
    }

    public void setCibaSettings(Optional<PatchCIBASettings> cibaSettings) {
        this.cibaSettings = cibaSettings;
    }

    public OIDCSettings patch(OIDCSettings toPatch) {

        //If source may be null, in such case init with default values
        if (toPatch == null ) {
            toPatch = OIDCSettings.defaultSettings();
        }
        SetterUtils.safeSet(toPatch::setRedirectUriStrictMatching, this.getRedirectUriStrictMatching(), boolean.class);
        SetterUtils.safeSet(toPatch::setPostLogoutRedirectUris, this.getPostLogoutRedirectUris());

        if(getClientRegistrationSettings()!=null) {
            //If present apply settings, else return default settings.
            if(getClientRegistrationSettings().isPresent()) {
                PatchClientRegistrationSettings patcher = getClientRegistrationSettings().get();
                ClientRegistrationSettings source = toPatch.getClientRegistrationSettings();
                toPatch.setClientRegistrationSettings(patcher.patch(source));
            } else {
                toPatch.setClientRegistrationSettings(ClientRegistrationSettings.defaultSettings());
            }
        }

        if (getSecurityProfileSettings() != null) {
            if (getSecurityProfileSettings().isPresent()) {
                final PatchSecurityProfileSettings patcher = getSecurityProfileSettings().get();
                final SecurityProfileSettings source = toPatch.getSecurityProfileSettings();
                toPatch.setSecurityProfileSettings(patcher.patch(source));
            } else {
                toPatch.setSecurityProfileSettings(SecurityProfileSettings.defaultSettings());
            }
        }

        if (getCibaSettings() != null) {
            if (getCibaSettings().isPresent()) {
                final PatchCIBASettings patcher = getCibaSettings().get();
                final CIBASettings source = toPatch.getCibaSettings();
                toPatch.setCibaSettings(patcher.patch(source));
            } else {
                toPatch.setCibaSettings(CIBASettings.defaultSettings());
            }
        }

        return toPatch;
    }


    public Set<Permission> getRequiredPermissions() {

        Set<Permission> requiredPermissions = new HashSet<>();

        if (clientRegistrationSettings != null && clientRegistrationSettings.isPresent()
                || redirectUriStrictMatching != null && redirectUriStrictMatching.isPresent()) {
            requiredPermissions.add(Permission.DOMAIN_OPENID);
        }

        return requiredPermissions;
    }
}
