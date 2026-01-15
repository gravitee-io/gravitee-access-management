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

import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.PostLoginAction;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.utils.PermissionSettingUtils;
import io.gravitee.am.service.utils.SetterUtils;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PatchApplicationSettings {

    private Optional<AccountSettings> account;
    private Optional<LoginSettings> login;
    private Optional<PostLoginAction> postLoginAction;
    private Optional<PatchApplicationOAuthSettings> oauth;
    private Optional<PatchApplicationSAMLSettings> saml;
    private Optional<PatchApplicationAdvancedSettings> advanced;
    private Optional<PatchPasswordSettings> passwordSettings;
    private Optional<PatchMFASettings> mfa;
    private Optional<CookieSettings> cookieSettings;
    private Optional<RiskAssessmentSettings> riskAssessment;
    private Optional<SecretExpirationSettings> secretExpirationSettings;

    public ApplicationSettings patch(ApplicationSettings _toPatch) {
        // create new object for audit purpose (patch json result)
        ApplicationSettings toPatch = _toPatch == null ? new ApplicationSettings() : new ApplicationSettings(_toPatch);

        // set values
        SetterUtils.safeSet(toPatch::setAccount, this.getAccount());
        SetterUtils.safeSet(toPatch::setLogin, this.getLogin());
        SetterUtils.safeSet(toPatch::setPostLoginAction, this.getPostLoginAction());
        SetterUtils.safeSet(toPatch::setCookieSettings, this.getCookieSettings());
        SetterUtils.safeSet(toPatch::setRiskAssessment, this.getRiskAssessment());
        SetterUtils.safeSet(toPatch::setSecretExpirationSettings, this.getSecretExpirationSettings());
        if (this.getOauth() != null && this.getOauth().isPresent()) {
            toPatch.setOauth(this.getOauth().get().patch(toPatch.getOauth()));
        }
        if (this.getAdvanced() != null && this.getAdvanced().isPresent()) {
            toPatch.setAdvanced(this.getAdvanced().get().patch(toPatch.getAdvanced()));
        }
        if (this.getPasswordSettings() != null && this.getPasswordSettings().isPresent()) {
            toPatch.setPasswordSettings(this.getPasswordSettings().get().patch(toPatch.getPasswordSettings()));
        }
        if (this.getMfa() != null && this.getMfa().isPresent()) {
            toPatch.setMfa(this.getMfa().get().patch(toPatch.getMfa()));
        }
        if (this.getSaml() != null && this.getSaml().isPresent()) {
            toPatch.setSaml(this.getSaml().get().patch(toPatch.getSaml()));
        }
        return toPatch;
    }

    public Set<Permission> getRequiredPermissions() {
        return PermissionSettingUtils.getRequiredPermissions(this);
    }
}
