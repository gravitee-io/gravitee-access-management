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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.service.model.openid.PatchClientRegistrationSettings;
import io.gravitee.am.service.model.openid.PatchOIDCSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class PatchDomainTest {

    @Test
    public void testPatchWithoutOidc() {
        //Build patcher
        PatchDomain patch = new PatchDomain();
        patch.setDescription(Optional.of("expectedDescription"));
        patch.setName(Optional.empty());

        //Build object to patch
        Domain toPatch = new Domain();
        toPatch.setDescription("oldDescription");
        toPatch.setName("oldName");
        toPatch.setPath("expectedPath");

        //apply patch
        Domain result = patch.patch(toPatch);

        //check.
        assertNotNull("was expecting a domain", result);
        assertEquals("description should have been updated", "expectedDescription", result.getDescription());
        assertNull("name should have been set to null", result.getName());
        assertEquals("path should not be updated", "expectedPath", result.getPath());
    }

    @Test
    public void testPatchWithEmptyOidc() {
        //Build patcher
        PatchDomain patch = new PatchDomain();
        patch.setOidc(Optional.empty());

        //Build object to patch with DCR enabled
        ClientRegistrationSettings dcr = ClientRegistrationSettings.defaultSettings();
        OIDCSettings oidc = OIDCSettings.defaultSettings();
        Domain toPatch = new Domain();

        dcr.setDynamicClientRegistrationEnabled(true);
        oidc.setClientRegistrationSettings(dcr);
        toPatch.setOidc(oidc);

        //apply patch
        Domain result = patch.patch(toPatch);

        //check.
        assertNotNull("was expecting a domain", result);
        assertNotNull(result.getOidc());
        assertNotNull(result.getOidc().getClientRegistrationSettings());
        assertFalse("should have been disabled", result.getOidc().getClientRegistrationSettings().isDynamicClientRegistrationEnabled());
    }

    @Test
    public void testPatchWithEnabledOidc() {
        //Build patcher
        PatchClientRegistrationSettings dcrPatcher = new PatchClientRegistrationSettings();
        dcrPatcher.setDynamicClientRegistrationEnabled(Optional.of(true));
        PatchOIDCSettings oidcPatcher = new PatchOIDCSettings();
        oidcPatcher.setClientRegistrationSettings(Optional.of(dcrPatcher));
        PatchDomain patch = new PatchDomain();
        patch.setOidc(Optional.of(oidcPatcher));

        //Build object to patch with DCR enabled
        Domain toPatch = new Domain();
        toPatch.setOidc(OIDCSettings.defaultSettings());

        //apply patch
        Domain result = patch.patch(toPatch);

        //check.
        assertNotNull("was expecting a domain", result);
        assertNotNull(result.getOidc());
        assertNotNull(result.getOidc().getClientRegistrationSettings());
        assertTrue("should have been enabled", result.getOidc().getClientRegistrationSettings().isDynamicClientRegistrationEnabled());
    }

    @Test
    public void testGetRequiredPermissions() {

        PatchDomain patchDomain = new PatchDomain();
        assertEquals(Collections.emptySet(), patchDomain.getRequiredPermissions());

        patchDomain.setName(Optional.of("patchName"));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setDescription(Optional.of("patchDescription"));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setEnabled(Optional.of(true));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setPath(Optional.of("patchPath"));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setLoginSettings(Optional.of(new LoginSettings()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setAccountSettings(Optional.of(new AccountSettings()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setTags(Optional.of(Collections.singleton("patchTag")));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        PatchOIDCSettings oidcSettings = new PatchOIDCSettings();
        patchDomain.setOidc(Optional.of(oidcSettings));
        assertEquals(Collections.emptySet(), patchDomain.getRequiredPermissions());
        oidcSettings.setClientRegistrationSettings(Optional.of(new PatchClientRegistrationSettings()));
        oidcSettings.setRedirectUriStrictMatching(Optional.of(true));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_OPENID)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setScim(Optional.of(new SCIMSettings()));
        assertEquals(new HashSet<>(Arrays.asList( Permission.DOMAIN_SCIM)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setUma(Optional.of(new UMASettings()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_UMA)), patchDomain.getRequiredPermissions());

        // Check multiple permissions.
        patchDomain = new PatchDomain();
        patchDomain.setPath(Optional.of("patchPath"));
        patchDomain.setOidc(Optional.of(oidcSettings));
        patchDomain.setScim(Optional.of(new SCIMSettings()));

        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS, Permission.DOMAIN_OPENID, Permission.DOMAIN_SCIM)), patchDomain.getRequiredPermissions());
    }
}
