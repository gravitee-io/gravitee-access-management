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

import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.DeviceFlowSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.Permission;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class PatchOIDCSettingsTest {

    @Test
    public void testPatchToNullValue() {
        //Build patcher
        PatchOIDCSettings nullSettings = new PatchOIDCSettings();

        //apply patch on null object
        OIDCSettings result = nullSettings.patch(null);

        assertNotNull(result);
        assertNotNull(result.getClientRegistrationSettings());
        assertFalse("should be disabled by default", result.getClientRegistrationSettings().isDynamicClientRegistrationEnabled());
    }

    @Test
    public void testPatchToCIBA() {
        //Build patcher
        PatchOIDCSettings patchOIDCSettings = new PatchOIDCSettings();
        PatchCIBASettings patchCiba = new PatchCIBASettings();
        patchCiba.setEnabled(Optional.of(true));
        patchOIDCSettings.setCibaSettings(Optional.of(patchCiba));

        // build settings to patch
        OIDCSettings settings = new OIDCSettings();
        final CIBASettings cibaSettings = new CIBASettings();
        cibaSettings.setEnabled(false);
        settings.setCibaSettings(cibaSettings);
        assertFalse("CIBA settings shall be false before update", settings.getCibaSettings().isEnabled());

        //apply patch on null object
        OIDCSettings result = patchOIDCSettings.patch(settings);

        assertNotNull(result);
        assertNotNull(result.getCibaSettings());
        assertTrue("CIBA settings shall be true after update", result.getCibaSettings().isEnabled());
    }

    @Test
    public void testPatchRequireDpopForAll() {
        PatchDPoPSettings dpopPatcher = new PatchDPoPSettings();
        dpopPatcher.setRequireDpopForAll(Optional.of(true));
        PatchOIDCSettings patcher = new PatchOIDCSettings();
        patcher.setDpopSettings(Optional.of(dpopPatcher));

        OIDCSettings settings = new OIDCSettings();
        assertNull("dpopSettings shall default to null", settings.getDpopSettings());

        OIDCSettings result = patcher.patch(settings);

        assertNotNull(result);
        assertNotNull(result.getDpopSettings());
        assertTrue("requireDpopForAll shall be true after update", result.getDpopSettings().isRequireDpopForAll());
    }

    @Test
    public void testPatchDpopSigningAlgorithms() {
        PatchDPoPSettings dpopPatcher = new PatchDPoPSettings();
        dpopPatcher.setDpopSigningAlgorithms(Optional.of(java.util.List.of("ES256", "ES384")));
        PatchOIDCSettings patcher = new PatchOIDCSettings();
        patcher.setDpopSettings(Optional.of(dpopPatcher));

        OIDCSettings settings = new OIDCSettings();

        OIDCSettings result = patcher.patch(settings);

        assertNotNull(result);
        assertNotNull(result.getDpopSettings());
        assertEquals(java.util.List.of("ES256", "ES384"), result.getDpopSettings().getDpopSigningAlgorithms());
    }

    @Test
    public void shouldLeaveDeviceFlowSettingsAbsentWhenNotPatched() {
        OIDCSettings result = new PatchOIDCSettings().patch(new OIDCSettings());

        assertNull("device flow settings shall be absent unless patched", result.getDeviceFlowSettings());
    }

    @Test
    public void shouldPatchDeviceFlowSettings() {
        PatchOIDCSettings patcher = new PatchOIDCSettings();
        PatchDeviceFlowSettings deviceFlowPatcher = new PatchDeviceFlowSettings();
        deviceFlowPatcher.setEnabled(Optional.of(true));
        deviceFlowPatcher.setDeviceCodeExpiry(Optional.of(900));
        deviceFlowPatcher.setPollingInterval(Optional.of(10));
        patcher.setDeviceFlowSettings(Optional.of(deviceFlowPatcher));

        OIDCSettings result = patcher.patch(new OIDCSettings());

        assertNotNull(result.getDeviceFlowSettings());
        assertTrue("device flow shall be enabled after update", result.getDeviceFlowSettings().isEnabled());
        assertEquals(900, result.getDeviceFlowSettings().getDeviceCodeExpiry());
        assertEquals(10, result.getDeviceFlowSettings().getPollingInterval());
    }

    @Test
    public void shouldKeepUnpatchedDeviceFlowFields() {
        DeviceFlowSettings existing = new DeviceFlowSettings();
        existing.setEnabled(true);
        existing.setDeviceCodeExpiry(1200);
        existing.setPollingInterval(15);
        OIDCSettings settings = new OIDCSettings();
        settings.setDeviceFlowSettings(existing);

        PatchOIDCSettings patcher = new PatchOIDCSettings();
        PatchDeviceFlowSettings deviceFlowPatcher = new PatchDeviceFlowSettings();
        deviceFlowPatcher.setEnabled(Optional.of(false));
        patcher.setDeviceFlowSettings(Optional.of(deviceFlowPatcher));

        OIDCSettings result = patcher.patch(settings);

        assertFalse(result.getDeviceFlowSettings().isEnabled());
        assertEquals(1200, result.getDeviceFlowSettings().getDeviceCodeExpiry());
        assertEquals(15, result.getDeviceFlowSettings().getPollingInterval());
    }

    @Test
    public void shouldResetDeviceFlowSettingsToDefaultsWhenPatchedEmpty() {
        DeviceFlowSettings existing = new DeviceFlowSettings();
        existing.setEnabled(true);
        existing.setDeviceCodeExpiry(1200);
        OIDCSettings settings = new OIDCSettings();
        settings.setDeviceFlowSettings(existing);

        PatchOIDCSettings patcher = new PatchOIDCSettings();
        patcher.setDeviceFlowSettings(Optional.empty());

        OIDCSettings result = patcher.patch(settings);

        assertFalse(result.getDeviceFlowSettings().isEnabled());
        assertEquals(DeviceFlowSettings.DEFAULT_DEVICE_CODE_EXPIRY_IN_SEC, result.getDeviceFlowSettings().getDeviceCodeExpiry());
        assertEquals(DeviceFlowSettings.DEFAULT_POLLING_INTERVAL_IN_SEC, result.getDeviceFlowSettings().getPollingInterval());
    }

    @Test
    public void shouldRequireOpenidPermissionToPatchDeviceFlowSettings() {
        PatchOIDCSettings patcher = new PatchOIDCSettings();
        patcher.setDeviceFlowSettings(Optional.of(new PatchDeviceFlowSettings()));

        assertTrue(patcher.getRequiredPermissions().contains(Permission.DOMAIN_OPENID));
    }

    @Test
    public void testPatchToEmptyValue() {
        //Build patcher
        PatchOIDCSettings emptySettings = new PatchOIDCSettings();
        emptySettings.setClientRegistrationSettings(Optional.empty());

        //apply patch to empty object
        OIDCSettings result = emptySettings.patch(new OIDCSettings());

        assertNotNull(result);
        assertNotNull(result.getClientRegistrationSettings());
        assertFalse("should be disabled by default", result.getClientRegistrationSettings().isDynamicClientRegistrationEnabled());
    }

    @Test
    public void testPatchSettingsToEmptyValue() {
        //Build patcher
        PatchOIDCSettings patcher = new PatchOIDCSettings();
        PatchClientRegistrationSettings dcrPatcher = new PatchClientRegistrationSettings();
        dcrPatcher.setDynamicClientRegistrationEnabled(Optional.of(true));
        dcrPatcher.setAllowLocalhostRedirectUri(Optional.of(true));
        patcher.setClientRegistrationSettings(Optional.of(dcrPatcher));

        //apply patch
        OIDCSettings result = patcher.patch(new OIDCSettings());

        assertNotNull(result);
        assertNotNull(result.getClientRegistrationSettings());
        assertTrue("should be enabled",result.getClientRegistrationSettings().isDynamicClientRegistrationEnabled());
        assertTrue("should be enabled",result.getClientRegistrationSettings().isAllowLocalhostRedirectUri());
        assertFalse("should be disabled by default", result.getClientRegistrationSettings().isOpenDynamicClientRegistrationEnabled());
    }

    @Test
    public void testPatchEmtpySettings() {
        //Build object to patch
        ClientRegistrationSettings dcrSettings = new ClientRegistrationSettings();
        dcrSettings.setDynamicClientRegistrationEnabled(true);
        dcrSettings.setOpenDynamicClientRegistrationEnabled(false);
        dcrSettings.setAllowLocalhostRedirectUri(true);
        dcrSettings.setAllowHttpSchemeRedirectUri(false);
        dcrSettings.setAllowWildCardRedirectUri(true);
        OIDCSettings toPatch = new OIDCSettings();
        toPatch.setClientRegistrationSettings(dcrSettings);

        //Build patcher
        PatchOIDCSettings patcher = new PatchOIDCSettings();
        PatchClientRegistrationSettings dcrPatcher = new PatchClientRegistrationSettings();
        dcrPatcher.setDynamicClientRegistrationEnabled(Optional.of(false));
        dcrPatcher.setOpenDynamicClientRegistrationEnabled(Optional.of(true));
        dcrPatcher.setAllowLocalhostRedirectUri(Optional.of(false));
        dcrPatcher.setAllowHttpSchemeRedirectUri(Optional.of(true));
        dcrPatcher.setAllowWildCardRedirectUri(Optional.of(false));
        patcher.setClientRegistrationSettings(Optional.of(dcrPatcher));

        //apply patch
        OIDCSettings result = patcher.patch(toPatch);

        assertNotNull(result);
        assertNotNull(result.getClientRegistrationSettings());
        assertFalse(result.getClientRegistrationSettings().isDynamicClientRegistrationEnabled());
        assertTrue(result.getClientRegistrationSettings().isOpenDynamicClientRegistrationEnabled());
        assertFalse(result.getClientRegistrationSettings().isAllowLocalhostRedirectUri());
        assertTrue(result.getClientRegistrationSettings().isAllowHttpSchemeRedirectUri());
        assertFalse(result.getClientRegistrationSettings().isAllowWildCardRedirectUri());
    }
}
