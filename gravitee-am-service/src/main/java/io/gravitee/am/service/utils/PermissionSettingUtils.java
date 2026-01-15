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

package io.gravitee.am.service.utils;

import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.model.PatchApplication;
import io.gravitee.am.service.model.PatchApplicationSettings;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PermissionSettingUtils {

    /**
     * Returns the list of required permission depending on what fields are filled.
     *
     * Ex: if settings.oauth is filled, {@link Permission#APPLICATION_OPENID} will be added to the list of required permissions cause it means the user want to update this information.
     *
     * @return the list of required permissions.
     */

    public static Set<Permission> getRequiredPermissions(PatchApplication application) {
        var requiredPermissions = new HashSet<Permission>();

        if (application == null) {
            return requiredPermissions;
        }

        setApplicationSettingsPermissions(application, requiredPermissions);
        setIdentityProvidersPermissions(application, requiredPermissions);
        setFactorsPermissions(application, requiredPermissions);
        setCertificatePermission(application, requiredPermissions);
        setSubApplicationSettingsPermissions(application, requiredPermissions);

        return requiredPermissions;
    }

    private static void setApplicationSettingsPermissions(PatchApplication application, Set<Permission> requiredPermissions) {
        var name = application.getName();
        var description = application.getDescription();
        var enabled = application.getEnabled();
        var template = application.getTemplate();
        var metadata = application.getMetadata();

        if (name != null && name.isPresent()
                || description != null && description.isPresent()
                || enabled != null && enabled.isPresent()
                || template != null && template.isPresent()
                || metadata != null && metadata.isPresent()) {

            requiredPermissions.add(Permission.APPLICATION_SETTINGS);
        }
    }

    private static void setIdentityProvidersPermissions(PatchApplication application, Set<Permission> requiredPermissions) {
        var identityProviders = application.getIdentityProviders();
        if (identityProviders != null && identityProviders.isPresent()) {
            requiredPermissions.add(Permission.APPLICATION_IDENTITY_PROVIDER);
        }
    }

    private static void setFactorsPermissions(PatchApplication application, Set<Permission> requiredPermissions) {
        var factors = application.getFactors();
        if (factors != null && factors.isPresent()) {
            requiredPermissions.add(Permission.APPLICATION_FACTOR);
        }
    }

    private static void setCertificatePermission(PatchApplication application, HashSet<Permission> requiredPermissions) {
        var certificate = application.getCertificate();
        if (certificate != null) {
            requiredPermissions.add(Permission.APPLICATION_CERTIFICATE);
        }
    }

    private static void setSubApplicationSettingsPermissions(PatchApplication application, HashSet<Permission> requiredPermissions) {
        var settings = application.getSettings();
        if (settings != null && settings.isPresent()) {
            requiredPermissions.addAll(settings.get().getRequiredPermissions());
        }
    }

    public static Set<Permission> getRequiredPermissions(PatchApplicationSettings settings) {
        var requiredPermissions = new HashSet<Permission>();

        if (settings == null) {
            return requiredPermissions;
        }

        setApplicationSettingsPermission(settings, requiredPermissions);
        setOpenIDPermission(settings, requiredPermissions);
        setSamlPermission(settings, requiredPermissions);
        setSecretExpirationSettings(settings, requiredPermissions);

        return requiredPermissions;
    }

    private static void setApplicationSettingsPermission(PatchApplicationSettings settings, Set<Permission> requiredPermissions) {
        var account = settings.getAccount();
        var login = settings.getLogin();
        var postLoginAction = settings.getPostLoginAction();
        var advanced = settings.getAdvanced();
        var passwordSettings = settings.getPasswordSettings();
        var mfa = settings.getMfa();
        var cookieSettings = settings.getCookieSettings();
        var riskAssessment = settings.getRiskAssessment();

        if (account != null && account.isPresent()
                || login != null && login.isPresent()
                || postLoginAction != null && postLoginAction.isPresent()
                || advanced != null && advanced.isPresent()
                || passwordSettings != null && passwordSettings.isPresent()
                || mfa != null && mfa.isPresent()
                || cookieSettings != null && cookieSettings.isPresent()
                || riskAssessment != null && riskAssessment.isPresent()
        ) {
            requiredPermissions.add(Permission.APPLICATION_SETTINGS);
        }
    }

    private static void setOpenIDPermission(PatchApplicationSettings settings, Set<Permission> requiredPermissions) {
        var oauth = settings.getOauth();
        if (oauth != null && oauth.isPresent()) {
            requiredPermissions.add(Permission.APPLICATION_OPENID);
        }
    }

    private static void setSamlPermission(PatchApplicationSettings settings, Set<Permission> requiredPermissions) {
        var saml = settings.getSaml();
        if (saml != null && saml.isPresent()) {
            requiredPermissions.add(Permission.APPLICATION_SAML);
        }
    }

    private static void setSecretExpirationSettings(PatchApplicationSettings settings, Set<Permission> requiredPermissions) {
        var expiration = settings.getSecretExpirationSettings();
        if(expiration != null && expiration.isPresent()) {
            requiredPermissions.add(Permission.APPLICATION_SETTINGS);
        }
    }
}
