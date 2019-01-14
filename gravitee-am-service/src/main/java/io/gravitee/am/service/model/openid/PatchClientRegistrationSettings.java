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

import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class PatchClientRegistrationSettings {

    public PatchClientRegistrationSettings() {}

    /**
     * Allow localhost host as redirect_uri
     */
    private Optional<Boolean> allowLocalhostRedirectUri;

    /**
     * Allow unsecured http scheme into redirect_uri
     */
    private Optional<Boolean> allowHttpSchemeRedirectUri;

    /**
     * Allow wildcard redirect uri
     */
    private Optional<Boolean> allowWildCardRedirectUri;

    /**
     * Domain Dynamic Client Registration enabled
     */
    private Optional<Boolean> isDynamicClientRegistrationEnabled;

    /**
     * Domain open Dynamic Client Registration enabled
     */
    private Optional<Boolean> isOpenDynamicClientRegistrationEnabled;


    public Optional<Boolean> getAllowLocalhostRedirectUri() {
        return allowLocalhostRedirectUri;
    }

    public void setAllowLocalhostRedirectUri(Optional<Boolean> allowLocalhostRedirectUri) {
        this.allowLocalhostRedirectUri = allowLocalhostRedirectUri;
    }

    public Optional<Boolean> getAllowHttpSchemeRedirectUri() {
        return allowHttpSchemeRedirectUri;
    }

    public void setAllowHttpSchemeRedirectUri(Optional<Boolean> allowHttpSchemeRedirectUri) {
        this.allowHttpSchemeRedirectUri = allowHttpSchemeRedirectUri;
    }

    public Optional<Boolean> getAllowWildCardRedirectUri() {
        return allowWildCardRedirectUri;
    }

    public void setAllowWildCardRedirectUri(Optional<Boolean> allowWildCardRedirectUri) {
        this.allowWildCardRedirectUri = allowWildCardRedirectUri;
    }

    public Optional<Boolean> isDynamicClientRegistrationEnabled() {
        return isDynamicClientRegistrationEnabled;
    }

    public void setDynamicClientRegistrationEnabled(Optional<Boolean> isDynamicClientRegistrationEnabled) {
        this.isDynamicClientRegistrationEnabled = isDynamicClientRegistrationEnabled;
    }

    public Optional<Boolean> isOpenDynamicClientRegistrationEnabled() {
        return isOpenDynamicClientRegistrationEnabled;
    }

    public void setOpenDynamicClientRegistrationEnabled(Optional<Boolean> isOpenDynamicClientRegistrationEnabled) {
        this.isOpenDynamicClientRegistrationEnabled = isOpenDynamicClientRegistrationEnabled;
    }


    public ClientRegistrationSettings patch(ClientRegistrationSettings toPatch) {
        ClientRegistrationSettings result=toPatch!=null?toPatch: ClientRegistrationSettings.defaultSettings();

        SetterUtils.safeSet(result::setAllowWildCardRedirectUri, this.getAllowWildCardRedirectUri(), boolean.class);
        SetterUtils.safeSet(result::setAllowHttpSchemeRedirectUri, this.getAllowHttpSchemeRedirectUri(), boolean.class);
        SetterUtils.safeSet(result::setAllowLocalhostRedirectUri, this.getAllowLocalhostRedirectUri(), boolean.class);
        SetterUtils.safeSet(result::setOpenDynamicClientRegistrationEnabled, this.isOpenDynamicClientRegistrationEnabled(), boolean.class);
        SetterUtils.safeSet(result::setDynamicClientRegistrationEnabled,this.isDynamicClientRegistrationEnabled(), boolean.class);

        return result;
    }
}
