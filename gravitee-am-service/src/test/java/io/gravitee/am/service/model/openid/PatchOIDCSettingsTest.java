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
import io.gravitee.am.model.oidc.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.service.exception.InvalidParameterException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;
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
    public void shouldPatchKeyRetrievalSettings() {
        PatchKeyRetrievalSettings patchKeyRetrieval = new PatchKeyRetrievalSettings();
        patchKeyRetrieval.setFetchTimeoutMs(Optional.of(1234));
        patchKeyRetrieval.setAllowPrivateIpAddress(Optional.of(true));
        PatchOIDCSettings patch = new PatchOIDCSettings();
        patch.setKeyRetrievalSettings(Optional.of(patchKeyRetrieval));

        OIDCSettings result = patch.patch(OIDCSettings.defaultSettings());

        assertEquals(1234, result.getKeyRetrievalSettings().getFetchTimeoutMs());
        assertTrue(result.getKeyRetrievalSettings().isAllowPrivateIpAddress());
        assertEquals(KeyRetrievalSettings.DEFAULT_CACHE_TTL_SECONDS, result.getKeyRetrievalSettings().getCacheTtlSeconds());
    }

    @Test
    public void shouldRelocateRetrievalLimitsWrittenAgainstTheSpiffeBlock() {
        PatchSpiffeDomainSettings patchSpiffe = new PatchSpiffeDomainSettings();
        patchSpiffe.setEnabled(Optional.of(true));
        patchSpiffe.setFetchTimeoutMs(Optional.of(1234));
        PatchOIDCSettings patch = new PatchOIDCSettings();
        patch.setWorkloadIdentitySettings(Optional.of(patchSpiffe));

        OIDCSettings result = patch.patch(OIDCSettings.defaultSettings());

        assertEquals(1234, result.getKeyRetrievalSettings().getFetchTimeoutMs());
        assertTrue(result.getWorkloadIdentitySettings().isEnabled());
        assertNull(result.getWorkloadIdentitySettings().getFetchTimeoutMs());
    }

    @Test
    public void shouldPreferKeyRetrievalSettingsOverTheDeprecatedSpiffeLimits() {
        PatchSpiffeDomainSettings patchSpiffe = new PatchSpiffeDomainSettings();
        patchSpiffe.setFetchTimeoutMs(Optional.of(1234));
        PatchKeyRetrievalSettings patchKeyRetrieval = new PatchKeyRetrievalSettings();
        patchKeyRetrieval.setFetchTimeoutMs(Optional.of(999));
        PatchOIDCSettings patch = new PatchOIDCSettings();
        patch.setWorkloadIdentitySettings(Optional.of(patchSpiffe));
        patch.setKeyRetrievalSettings(Optional.of(patchKeyRetrieval));

        OIDCSettings result = patch.patch(OIDCSettings.defaultSettings());

        assertEquals(999, result.getKeyRetrievalSettings().getFetchTimeoutMs());
    }

    @Test
    public void shouldRejectNonPositiveKeyRetrievalLimit() {
        PatchKeyRetrievalSettings patchKeyRetrieval = new PatchKeyRetrievalSettings();
        patchKeyRetrieval.setCacheMaxEntries(Optional.of(0));
        PatchOIDCSettings patch = new PatchOIDCSettings();
        patch.setKeyRetrievalSettings(Optional.of(patchKeyRetrieval));

        assertThrows(InvalidParameterException.class, () -> patch.patch(OIDCSettings.defaultSettings()));
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
