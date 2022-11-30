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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.permissions.Permission;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchApplicationTest {

    @Test
    public void updateClientName_NoSettings() {
        final var NEW_NAME = "MyNewName";
        final var NAME = "MyName";

        final var app = new Application();
        app.setName(NAME);

        final var patchApplication = new PatchApplication();
        patchApplication.setName(Optional.of(NEW_NAME));

        final var updatedApp = patchApplication.patch(app);
        assertEquals(NEW_NAME, updatedApp.getName());
        assertNull(updatedApp.getSettings());
    }

    @Test
    public void updateClientName_WithSettings() {
        final var NEW_NAME = "MyNewName";
        final var NAME = "MyName";

        final var oauthSettings = new ApplicationOAuthSettings();
        oauthSettings.setClientName(NAME);
        final var settings = new ApplicationSettings();
        settings.setOauth(oauthSettings);
        final var app = new Application();
        app.setName(NAME);
        app.setSettings(settings);

        final var patchApplication = new PatchApplication();
        patchApplication.setName(Optional.of(NEW_NAME));

        final var updatedApp = patchApplication.patch(app);
        assertEquals(NEW_NAME, updatedApp.getName());
        assertNotNull(updatedApp.getSettings());
        assertNotNull(updatedApp.getSettings().getOauth());
        assertEquals(NEW_NAME, updatedApp.getSettings().getOauth().getClientName());
    }


    @Test
    public void getRequiredPermissions() {

        PatchApplication patchApplication = new PatchApplication();
        assertEquals(Collections.emptySet(), patchApplication.getRequiredPermissions());

        patchApplication.setName(Optional.of("patchName"));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_SETTINGS)), patchApplication.getRequiredPermissions());

        patchApplication = new PatchApplication();
        patchApplication.setDescription(Optional.of("patchDescription"));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_SETTINGS)), patchApplication.getRequiredPermissions());

        patchApplication = new PatchApplication();
        patchApplication.setEnabled(Optional.of(true));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_SETTINGS)), patchApplication.getRequiredPermissions());

        patchApplication = new PatchApplication();
        patchApplication.setTemplate(Optional.of(true));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_SETTINGS)), patchApplication.getRequiredPermissions());

        patchApplication = new PatchApplication();
        patchApplication.setIdentities(Optional.of(Collections.singleton("patchIdentity")));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_IDENTITY_PROVIDER)), patchApplication.getRequiredPermissions());

        patchApplication = new PatchApplication();
        patchApplication.setFactors(Optional.of(Collections.singleton("patchFactor")));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_FACTOR)), patchApplication.getRequiredPermissions());

        patchApplication = new PatchApplication();
        patchApplication.setCertificate(Optional.of("patchCertificate"));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_CERTIFICATE)), patchApplication.getRequiredPermissions());

        patchApplication = new PatchApplication();
        patchApplication.setMetadata(Optional.of(Collections.emptyMap()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_SETTINGS)), patchApplication.getRequiredPermissions());

        patchApplication = new PatchApplication();
        PatchApplicationSettings patchApplicationSettings = new PatchApplicationSettings();
        patchApplication.setSettings(Optional.of(patchApplicationSettings));
        assertEquals(Collections.emptySet(), patchApplication.getRequiredPermissions());
        patchApplicationSettings.setOauth(Optional.of(new PatchApplicationOAuthSettings()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_OPENID)), patchApplication.getRequiredPermissions());
        patchApplicationSettings.setOauth(Optional.empty());
        patchApplicationSettings.setAccount(Optional.of(new AccountSettings()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_SETTINGS)), patchApplication.getRequiredPermissions());
        patchApplicationSettings.setAccount(Optional.empty());
        patchApplicationSettings.setAdvanced(Optional.of(new PatchApplicationAdvancedSettings()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_SETTINGS)), patchApplication.getRequiredPermissions());

        // Check multiple permissions.
        patchApplication = new PatchApplication();
        patchApplication.setTemplate(Optional.of(true));
        patchApplication.setIdentities(Optional.of(Collections.singleton("patchIdentity")));
        patchApplication.setFactors(Optional.of(Collections.singleton("patchFactor")));
        patchApplication.setCertificate(Optional.of("patchCertificate"));
        patchApplicationSettings = new PatchApplicationSettings();
        patchApplicationSettings.setOauth(Optional.of(new PatchApplicationOAuthSettings()));
        patchApplication.setSettings(Optional.of(patchApplicationSettings));

        assertEquals(new HashSet<>(Arrays.asList(Permission.APPLICATION_SETTINGS, Permission.APPLICATION_OPENID, Permission.APPLICATION_IDENTITY_PROVIDER,
                Permission.APPLICATION_CERTIFICATE, Permission.APPLICATION_FACTOR)), patchApplication.getRequiredPermissions());
    }
}