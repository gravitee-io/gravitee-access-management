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

import io.gravitee.am.model.CertificateSettings;
import io.gravitee.am.model.CorsSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.service.model.openid.PatchOIDCSettings;
import io.gravitee.am.service.utils.SetterUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Setter
@Getter
@NoArgsConstructor
public class PatchDomain {

    private Optional<String> name;
    private Optional<String> description;
    private Optional<Boolean> enabled;
    private Optional<Boolean> alertEnabled;
    private Optional<String> path;
    private Optional<Boolean> vhostMode;
    private Optional<List<VirtualHost>> vhosts;
    private Optional<PatchOIDCSettings> oidc;
    private Optional<UMASettings> uma;
    private Optional<SCIMSettings> scim;
    private Optional<LoginSettings> loginSettings;
    private Optional<WebAuthnSettings> webAuthnSettings;
    private Optional<AccountSettings> accountSettings;
    private Optional<PatchPasswordSettings> passwordSettings;
    private Optional<SelfServiceAccountManagementSettings> selfServiceAccountManagementSettings;
    private Optional<Set<String>> tags;
    private Optional<Boolean> master;
    private Optional<PatchSAMLSettings> saml;
    private Optional<CorsSettings> corsSettings;
    private Optional<String> dataPlaneId;
    private Optional<SecretExpirationSettings> secretSettings;
    private Optional<TokenExchangeSettings> tokenExchangeSettings;
    private Optional<CertificateSettings> certificateSettings;

    public Domain patch(Domain _toPatch) {
        // create new object for audit purpose (patch json result)
        Domain toPatch = new Domain(_toPatch);

        SetterUtils.safeSet(toPatch::setName, this.getName());
        SetterUtils.safeSet(toPatch::setDescription, this.getDescription());
        SetterUtils.safeSet(toPatch::setEnabled, this.getEnabled(), boolean.class);
        SetterUtils.safeSet(toPatch::setAlertEnabled, this.getAlertEnabled(), boolean.class);
        SetterUtils.safeSet(toPatch::setPath, this.getPath());
        SetterUtils.safeSet(toPatch::setVhostMode, this.getVhostMode());
        SetterUtils.safeSet(toPatch::setVhosts, this.getVhosts());
        SetterUtils.safeSet(toPatch::setUma, this.getUma());
        SetterUtils.safeSet(toPatch::setScim, this.getScim());
        SetterUtils.safeSet(toPatch::setLoginSettings, this.getLoginSettings());
        SetterUtils.safeSet(toPatch::setWebAuthnSettings, this.getWebAuthnSettings());
        SetterUtils.safeSet(toPatch::setAccountSettings, this.getAccountSettings());
        SetterUtils.safeSet(toPatch::setSelfServiceAccountManagementSettings, this.getSelfServiceAccountManagementSettings());
        SetterUtils.safeSet(toPatch::setTags, this.getTags());
        SetterUtils.safeSet(toPatch::setMaster, this.getMaster(), boolean.class);
        SetterUtils.safeSet(toPatch::setCorsSettings, this.getCorsSettings());
        SetterUtils.safeSet(toPatch::setDataPlaneId, this.getDataPlaneId());
        SetterUtils.safeSet(toPatch::setSecretExpirationSettings, this.getSecretSettings());
        SetterUtils.safeSet(toPatch::setTokenExchangeSettings, this.getTokenExchangeSettings());
        SetterUtils.safeSet(toPatch::setCertificateSettings, this.getCertificateSettings());

        if (this.getOidc() != null) {
            if (this.getOidc().isPresent()) {
                PatchOIDCSettings patcher = this.getOidc().get();
                toPatch.setOidc(patcher.patch(toPatch.getOidc()));
            } else {
                toPatch.setOidc(OIDCSettings.defaultSettings());
            }
        }

        if (passwordSettings != null && passwordSettings.isPresent()) {
            var ps = passwordSettings.get();
            if (ps.getInherited() != null && ps.getInherited().isPresent() && Boolean.TRUE.equals(ps.getInherited().get())) {
                toPatch.setPasswordSettings(null);
                setPasswordSettings(Optional.empty());
            } else {
                toPatch.setPasswordSettings(ps.patch(toPatch.getPasswordSettings()));
            }
        }

        if (this.getSaml() != null && this.getSaml().isPresent()) {
            toPatch.setSaml(this.getSaml().get().patch(toPatch.getSaml()));
        }

        return toPatch;
    }


    /**
     * Returns the list of required permission depending on what fields are filled.
     * <p>
     * Ex: if settings.oauth is filled, {@link Permission#DOMAIN_OPENID} will be added to the list of required permissions cause it means the user want to update this information.
     *
     * @return the list of required permissions.
     */
    public Set<Permission> getRequiredPermissions() {

        Set<Permission> requiredPermissions = new HashSet<>();

        if (name != null && name.isPresent()
                || description != null && description.isPresent()
                || enabled != null && enabled.isPresent()
                || path != null && path.isPresent()
                || vhostMode != null && vhostMode.isPresent()
                || vhosts != null && vhosts.isPresent()
                || loginSettings != null && loginSettings.isPresent()
                || webAuthnSettings != null && webAuthnSettings.isPresent()
                || accountSettings != null && accountSettings.isPresent()
                || passwordSettings != null && passwordSettings.isPresent()
                || selfServiceAccountManagementSettings != null && selfServiceAccountManagementSettings.isPresent()
                || tags != null && tags.isPresent()
                || master != null && master.isPresent()
                || secretSettings != null && secretSettings.isPresent()
                || tokenExchangeSettings != null && tokenExchangeSettings.isPresent()
                || certificateSettings != null && certificateSettings.isPresent()) {

            requiredPermissions.add(Permission.DOMAIN_SETTINGS);
        }

        if (alertEnabled != null && alertEnabled.isPresent()) {
            requiredPermissions.add(Permission.DOMAIN_ALERT);
        }

        if (oidc != null && oidc.isPresent()) {
            requiredPermissions.addAll(oidc.get().getRequiredPermissions());
        }

        if (saml != null && saml.isPresent()) {
            requiredPermissions.addAll(saml.get().getRequiredPermissions());
        }

        if (uma != null && uma.isPresent()) {
            requiredPermissions.add(Permission.DOMAIN_UMA);
        }

        if (scim != null && scim.isPresent()) {
            requiredPermissions.add(Permission.DOMAIN_SCIM);
        }

        return requiredPermissions;
    }

}
