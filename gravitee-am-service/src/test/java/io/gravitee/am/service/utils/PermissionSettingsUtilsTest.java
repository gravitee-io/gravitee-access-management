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

import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.model.*;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

import static io.gravitee.am.model.permissions.Permission.*;
import static org.junit.Assert.assertTrue;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PermissionSettingsUtilsTest {

    @Test
    public void must_have_nothing() {
        var app = new PatchApplication();
        assertTrue(app.getRequiredPermissions().isEmpty());
    }

    @Test
    public void must_have_nothing_with_null_app() {
        assertTrue(PermissionSettingUtils.getRequiredPermissions((PatchApplication) null).isEmpty());
        assertTrue(PermissionSettingUtils.getRequiredPermissions((PatchApplicationSettings) null).isEmpty());
    }

    @Test
    public void must_have_application_settings_permissions_with_basic_info() {
        var app = new PatchApplication();

        app.setName(Optional.of("A sample value"));
        app.setDescription(Optional.of("A sample value"));
        app.setEnabled(Optional.of(true));
        app.setTemplate(Optional.of(false));
        app.setMetadata(Optional.of(Map.of()));

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.size() == 1 && permissions.contains(APPLICATION_SETTINGS));
    }

    @Test
    public void must_have_nothing_with_empty_options() {
        var app = new PatchApplication();

        app.setName(Optional.empty());
        app.setDescription(Optional.empty());
        app.setEnabled(Optional.empty());
        app.setTemplate(Optional.empty());
        app.setMetadata(Optional.empty());

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.isEmpty());
    }

    @Test
    public void must_have_idp_permissions() {
        var app = new PatchApplication();
        app.setIdentityProviders(Optional.of(Set.of(new PatchApplicationIdentityProvider())));

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.size() == 1 && permissions.contains(APPLICATION_IDENTITY_PROVIDER));
    }

    @Test
    public void must_not_have_idp_permissions() {
        var app = new PatchApplication();
        app.setIdentityProviders(Optional.empty());

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.isEmpty());
    }

    @Test
    public void must_have_factor_permissions() {
        var app = new PatchApplication();
        app.setFactors(Optional.of(Set.of("factor1", "factor2")));

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.size() == 1 && permissions.contains(APPLICATION_FACTOR));
    }

    @Test
    public void must_not_have_factor_permissions() {
        var app = new PatchApplication();
        app.setFactors(Optional.empty());

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.isEmpty());
    }

    @Test
    public void must_have_certificate_permissions() {
        var app = new PatchApplication();
        app.setCertificate(Optional.of("certificate-id"));

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.size() == 1 && permissions.contains(APPLICATION_CERTIFICATE));
    }

    @Test
    public void must_not_have_certificate_permissions() {
        var app = new PatchApplication();
        app.setCertificate(Optional.empty());

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.size() == 1 && permissions.contains(APPLICATION_CERTIFICATE));
    }

    @Test
    public void must_have_application_settings_permissions_with_sub_settings() {
        var app = new PatchApplication();
        final PatchApplicationSettings settings = new PatchApplicationSettings();

        settings.setAccount(Optional.of(new AccountSettings()));
        settings.setLogin(Optional.of(new LoginSettings()));
        settings.setAdvanced(Optional.of(new PatchApplicationAdvancedSettings()));
        settings.setPasswordSettings(Optional.of(new PatchPasswordSettings()));
        settings.setMfa(Optional.of(new PatchMFASettings()));
        settings.setCookieSettings(Optional.of(new CookieSettings()));
        settings.setRiskAssessment(Optional.of(new RiskAssessmentSettings()));

        app.setSettings(Optional.of(settings));

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.size() == 1 && permissions.contains(APPLICATION_SETTINGS));
    }

    @Test
    public void must_have_nothing_with_application_settings_permissions_optional() {
        var app = new PatchApplication();
        final PatchApplicationSettings settings = new PatchApplicationSettings();

        settings.setAccount(Optional.empty());
        settings.setLogin(Optional.empty());
        settings.setAdvanced(Optional.empty());
        settings.setPasswordSettings(Optional.empty());
        settings.setMfa(Optional.empty());
        settings.setCookieSettings(Optional.empty());
        settings.setRiskAssessment(Optional.empty());

        app.setSettings(Optional.of(settings));

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.isEmpty());
    }

    @Test
    public void must_have_openid_permissions_with_sub_settings() {
        var app = new PatchApplication();
        final PatchApplicationSettings settings = new PatchApplicationSettings();
        settings.setOauth(Optional.of(new PatchApplicationOAuthSettings()));
        app.setSettings(Optional.of(settings));

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.size() == 1 && permissions.contains(APPLICATION_OPENID));
    }

    @Test
    public void must_have_saml_permissions_with_sub_settings() {
        var app = new PatchApplication();
        final PatchApplicationSettings settings = new PatchApplicationSettings();
        settings.setSaml(Optional.of(new PatchApplicationSAMLSettings()));
        app.setSettings(Optional.of(settings));

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.size() == 1 && permissions.contains(APPLICATION_SAML));
    }

    @Test
    public void must_have_all_permissions() {
        var app = new PatchApplication();

        app.setName(Optional.of("A sample value"));
        app.setDescription(Optional.of("A sample value"));
        app.setEnabled(Optional.of(true));
        app.setTemplate(Optional.of(false));
        app.setMetadata(Optional.of(Map.of()));
        app.setIdentityProviders(Optional.of(Set.of(new PatchApplicationIdentityProvider())));
        app.setFactors(Optional.of(Set.of("factor1", "factor2")));
        app.setCertificate(Optional.of("certificate-id"));
        final PatchApplicationSettings settings = new PatchApplicationSettings();

        settings.setAccount(Optional.of(new AccountSettings()));
        settings.setLogin(Optional.of(new LoginSettings()));
        settings.setAdvanced(Optional.of(new PatchApplicationAdvancedSettings()));
        settings.setPasswordSettings(Optional.of(new PatchPasswordSettings()));
        settings.setMfa(Optional.of(new PatchMFASettings()));
        settings.setCookieSettings(Optional.of(new CookieSettings()));
        settings.setRiskAssessment(Optional.of(new RiskAssessmentSettings()));
        settings.setOauth(Optional.of(new PatchApplicationOAuthSettings()));
        settings.setSaml(Optional.of(new PatchApplicationSAMLSettings()));
        app.setSettings(Optional.of(settings));

        final Set<Permission> permissions = app.getRequiredPermissions();
        assertTrue(permissions.size() == 6 && permissions.containsAll(
                List.of(
                        APPLICATION_SETTINGS,
                        APPLICATION_IDENTITY_PROVIDER,
                        APPLICATION_FACTOR,
                        APPLICATION_CERTIFICATE,
                        APPLICATION_OPENID,
                        APPLICATION_SAML
                )
        ));
    }
}
