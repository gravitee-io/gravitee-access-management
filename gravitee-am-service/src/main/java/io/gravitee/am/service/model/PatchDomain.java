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
package io.gravitee.am.service.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.service.model.openid.PatchOIDCSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;
import java.util.Set;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class PatchDomain {

    public PatchDomain() {
    }

    private Optional<String> name;
    private Optional<String> description;
    private Optional<Boolean> enabled;
    private Optional<String> path;
    private Optional<Set<String>> identities;
    private Optional<Set<String>> oauth2Identities;
    @JsonProperty("oidc")
    private Optional<PatchOIDCSettings> oidc;
    private Optional<SCIMSettings> scim;
    private Optional<LoginSettings> loginSettings;

    public Optional<String> getName() {
        return name;
    }

    public void setName(Optional<String> name) {
        this.name = name;
    }

    public Optional<String> getDescription() {
        return description;
    }

    public void setDescription(Optional<String> description) {
        this.description = description;
    }

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public Optional<String> getPath() {
        return path;
    }

    public void setPath(Optional<String> path) {
        this.path = path;
    }

    public Optional<Set<String>> getIdentities() {
        return identities;
    }

    public void setIdentities(Optional<Set<String>> identities) {
        this.identities = identities;
    }

    public Optional<Set<String>> getOauth2Identities() {
        return oauth2Identities;
    }

    public void setOauth2Identities(Optional<Set<String>> oauth2Identities) {
        this.oauth2Identities = oauth2Identities;
    }

    public Optional<PatchOIDCSettings> getOidc() {
        return oidc;
    }

    public void setOidc(Optional<PatchOIDCSettings> oidc) {
        this.oidc = oidc;
    }

    public Optional<SCIMSettings> getScim() {
        return scim;
    }

    public void setScim(Optional<SCIMSettings> scim) {
        this.scim = scim;
    }

    public Optional<LoginSettings> getLoginSettings() {
        return loginSettings;
    }

    public void setLoginSettings(Optional<LoginSettings> loginSettings) {
        this.loginSettings = loginSettings;
    }

    public Domain patch(Domain _toPatch) {
        // create new object for audit purpose (patch json result)
        Domain toPatch = new Domain(_toPatch);

        SetterUtils.safeSet(toPatch::setName, this.getName());
        SetterUtils.safeSet(toPatch::setDescription, this.getDescription());
        SetterUtils.safeSet(toPatch::setEnabled, this.getEnabled(), boolean.class);
        SetterUtils.safeSet(toPatch::setPath, this.getPath());
        SetterUtils.safeSet(toPatch::setIdentities, this.getIdentities());
        SetterUtils.safeSet(toPatch::setOauth2Identities, this.getOauth2Identities());
        SetterUtils.safeSet(toPatch::setScim, this.getScim());
        SetterUtils.safeSet(toPatch::setLoginSettings, this.getLoginSettings());

        if (this.getOidc() != null) {
            if (this.getOidc().isPresent()) {
                PatchOIDCSettings patcher = this.getOidc().get();
                toPatch.setOidc(patcher.patch(toPatch.getOidc()));
            } else {
                toPatch.setOidc(OIDCSettings.defaultSettings());
            }
        }

        return toPatch;
    }
}
