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

import static org.junit.Assert.*;

import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
        assertTrue("should be enabled", result.getClientRegistrationSettings().isDynamicClientRegistrationEnabled());
        assertTrue("should be enabled", result.getClientRegistrationSettings().isAllowLocalhostRedirectUri());
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
