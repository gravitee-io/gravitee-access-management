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

import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchApplicationSettings {

    // SETTINGS
    private Optional<AccountSettings> account;

    //OPENID
    private Optional<PatchApplicationOAuthSettings> oauth;

    //SETTING
    private Optional<PatchApplicationAdvancedSettings> advanced;

    public Optional<AccountSettings> getAccount() {
        return account;
    }

    public void setAccount(Optional<AccountSettings> account) {
        this.account = account;
    }

    public Optional<PatchApplicationOAuthSettings> getOauth() {
        return oauth;
    }

    public void setOauth(Optional<PatchApplicationOAuthSettings> oauth) {
        this.oauth = oauth;
    }

    public Optional<PatchApplicationAdvancedSettings> getAdvanced() {
        return advanced;
    }

    public void setAdvanced(Optional<PatchApplicationAdvancedSettings> advanced) {
        this.advanced = advanced;
    }

    public ApplicationSettings patch(ApplicationSettings _toPatch) {
        // create new object for audit purpose (patch json result)
        ApplicationSettings toPatch = _toPatch == null ? new ApplicationSettings() : new ApplicationSettings(_toPatch);

        // set values
        SetterUtils.safeSet(toPatch::setAccount, this.getAccount());
        if (this.getOauth() != null && this.getOauth().isPresent()) {
            toPatch.setOauth(this.getOauth().get().patch(toPatch.getOauth()));
        }
        if (this.getAdvanced() != null && this.getAdvanced().isPresent()) {
            toPatch.setAdvanced(this.getAdvanced().get().patch(toPatch.getAdvanced()));
        }
        return toPatch;
    }

    public Set<Permission> getRequiredPermissions() {

        Set<Permission> requiredPermissions = new HashSet<>();

        if (account != null && account.isPresent()
                || advanced != null && advanced.isPresent()) {
            requiredPermissions.add(Permission.APPLICATION_SETTINGS);
        }

        if (oauth != null && oauth.isPresent()) {
            requiredPermissions.add(Permission.APPLICATION_OPENID);
        }

        return requiredPermissions;
    }
}
