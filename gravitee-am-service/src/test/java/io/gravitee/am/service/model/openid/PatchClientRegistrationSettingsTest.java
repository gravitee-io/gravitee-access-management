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

import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class PatchClientRegistrationSettingsTest {

    @Test
    public void testPatchDefaultSettings() {
        PatchClientRegistrationSettings emptySettings = new PatchClientRegistrationSettings();
        ClientRegistrationSettings result = emptySettings.patch(null);

        assertFalse("should be disabled by default", result.isDynamicClientRegistrationEnabled());
        assertFalse("should be disabled by default", result.isOpenDynamicClientRegistrationEnabled());
        assertFalse("should be disabled by default", result.isAllowLocalhostRedirectUri());
        assertFalse("should be disabled by default", result.isAllowLocalhostRedirectUri());
        assertFalse("should be disabled by default", result.isAllowWildCardRedirectUri());
    }

    @Test
    public void testPatch() {
        PatchClientRegistrationSettings patcher = new PatchClientRegistrationSettings();
        patcher.setDynamicClientRegistrationEnabled(Optional.of(false));
        patcher.setOpenDynamicClientRegistrationEnabled(Optional.empty());

        ClientRegistrationSettings toPatch = ClientRegistrationSettings.defaultSettings();
        toPatch.setDynamicClientRegistrationEnabled(true);
        toPatch.setOpenDynamicClientRegistrationEnabled(true);
        toPatch.setAllowLocalhostRedirectUri(true);

        ClientRegistrationSettings result = patcher.patch(toPatch);

        assertFalse("should be disabled by default", result.isDynamicClientRegistrationEnabled());
        assertFalse("should be disabled by default", result.isOpenDynamicClientRegistrationEnabled());
        assertTrue("should be disabled by default", result.isAllowLocalhostRedirectUri());
    }
}
