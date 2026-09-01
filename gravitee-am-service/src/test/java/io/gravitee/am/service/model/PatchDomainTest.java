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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.DomainVersion;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.openid.PatchClientRegistrationSettings;
import io.gravitee.am.service.model.openid.PatchOIDCSettings;
import io.gravitee.am.service.model.openid.PatchSpiffeDomainSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
        toPatch.setVersion(DomainVersion.V2_0);
        toPatch.setDescription("oldDescription");
        toPatch.setName("oldName");
        toPatch.setPath("/expectedPath");

        //apply patch
        Domain result = patch.patch(toPatch);

        //check.
        assertNotNull("was expecting a domain", result);
        assertEquals("description should have been updated", "expectedDescription", result.getDescription());
        assertNull("name should have been set to null", result.getName());
        assertEquals("path should not be updated", "/expectedPath", result.getPath());
        assertEquals("version should not be updated", DomainVersion.V2_0, result.getVersion());
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
    public void testPatchWithPasswordPolicy() {
        //Build patcher
        PatchPasswordSettings pwdPolicyPatcher = new PatchPasswordSettings();
        pwdPolicyPatcher.setOldPasswords(Optional.of((short) 24));
        pwdPolicyPatcher.setPasswordHistoryEnabled(Optional.of(true));

        PatchDomain patch = new PatchDomain();
        patch.setPasswordSettings(Optional.of(pwdPolicyPatcher));

        Domain toPatch = new Domain();
        toPatch.setPasswordSettings(new PasswordSettings());

        //apply patch
        Domain result = patch.patch(toPatch);

        //check.
        assertNotNull("was expecting a domain", result);
        assertNotNull(result.getPasswordSettings());
        assertTrue(result.getPasswordSettings().isPasswordHistoryEnabled());
        assertEquals(24, result.getPasswordSettings().getOldPasswords().shortValue());
    }

    @Test
    public void testPatchWithPasswordPolicyInheritDefault() {
        PatchPasswordSettings pwdPolicyPatcher = new PatchPasswordSettings();
        pwdPolicyPatcher.setInherited(Optional.of(true));

        PatchDomain patch = new PatchDomain();
        patch.setPasswordSettings(Optional.of(pwdPolicyPatcher));

        Domain toPatch = new Domain();
        toPatch.setPasswordSettings(new PasswordSettings());

        Domain result = patch.patch(toPatch);

        assertNotNull("was expecting a domain", result);
        assertNull(result.getPasswordSettings());
    }

    @Test
    public void testPatchWithLoginSettingsShouldKeepUnspecifiedProperties() {
        PatchLoginSettings patchedLoginSettings = new PatchLoginSettings();
        patchedLoginSettings.setForgotPasswordEnabled(Optional.of(true));

        PatchDomain patch = new PatchDomain();
        patch.setLoginSettings(Optional.of(patchedLoginSettings));

        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setRegisterEnabled(true);
        loginSettings.setRememberMeEnabled(true);
        loginSettings.setPasswordlessEnabled(true);
        loginSettings.setPasswordlessRememberDeviceEnabled(true);
        loginSettings.setPasswordlessEnforcePasswordEnabled(true);
        loginSettings.setPasswordlessEnforcePasswordMaxAge(3600);
        loginSettings.setPasswordlessDeviceNamingEnabled(true);
        loginSettings.setHideForm(true);
        loginSettings.setResetPasswordOnExpiration(true);

        Domain toPatch = new Domain();
        toPatch.setLoginSettings(loginSettings);

        Domain result = patch.patch(toPatch);

        assertTrue(result.getLoginSettings().isForgotPasswordEnabled());
        assertTrue(result.getLoginSettings().isRegisterEnabled());
        assertTrue(result.getLoginSettings().isRememberMeEnabled());
        assertTrue(result.getLoginSettings().isPasswordlessEnabled());
        assertTrue(result.getLoginSettings().isPasswordlessRememberDeviceEnabled());
        assertTrue(result.getLoginSettings().isPasswordlessEnforcePasswordEnabled());
        assertEquals(3600, result.getLoginSettings().getPasswordlessEnforcePasswordMaxAge().intValue());
        assertTrue(result.getLoginSettings().isPasswordlessDeviceNamingEnabled());
        assertTrue(result.getLoginSettings().isHideForm());
        assertTrue(result.getLoginSettings().getResetPasswordOnExpiration());
    }

    @Test(expected = InvalidParameterException.class)
    public void testPatchWithPasswordPolicy_missingOldPassword() {
        //Build patcher
        PatchPasswordSettings pwdPolicyPatcher = new PatchPasswordSettings();
        pwdPolicyPatcher.setPasswordHistoryEnabled(Optional.of(true));

        PatchDomain patch = new PatchDomain();
        patch.setPasswordSettings(Optional.of(pwdPolicyPatcher));

        Domain toPatch = new Domain();
        toPatch.setPasswordSettings(new PasswordSettings());

        //apply patch
        patch.patch(toPatch);
    }

    @Test(expected = InvalidParameterException.class)
    public void testPatchWithPasswordPolicy_outOfRange_min_OldPassword() {
        //Build patcher
        PatchPasswordSettings pwdPolicyPatcher = new PatchPasswordSettings();
        pwdPolicyPatcher.setOldPasswords(Optional.of((short) -5));
        pwdPolicyPatcher.setPasswordHistoryEnabled(Optional.of(true));

        PatchDomain patch = new PatchDomain();
        patch.setPasswordSettings(Optional.of(pwdPolicyPatcher));

        Domain toPatch = new Domain();
        toPatch.setPasswordSettings(new PasswordSettings());

        //apply patch
        patch.patch(toPatch);
    }

    @Test(expected = InvalidParameterException.class)
    public void testPatchWithPasswordPolicy_outOfRange_max_OldPassword() {
        //Build patcher
        PatchPasswordSettings pwdPolicyPatcher = new PatchPasswordSettings();
        pwdPolicyPatcher.setOldPasswords(Optional.of((short) 25));
        pwdPolicyPatcher.setPasswordHistoryEnabled(Optional.of(true));

        PatchDomain patch = new PatchDomain();
        patch.setPasswordSettings(Optional.of(pwdPolicyPatcher));

        Domain toPatch = new Domain();
        toPatch.setPasswordSettings(new PasswordSettings());

        //apply patch
        patch.patch(toPatch);
    }

    @Test
    public void testPatchWithCertificateSettings() {
        CertificateSettings certificateSettings = new CertificateSettings();
        certificateSettings.setFallbackCertificate("cert-id-123");

        PatchDomain patch = new PatchDomain();
        patch.setCertificateSettings(Optional.of(certificateSettings));

        Domain toPatch = new Domain();

        Domain result = patch.patch(toPatch);

        assertNotNull("was expecting a domain", result);
        assertNotNull(result.getCertificateSettings());
        assertEquals("cert-id-123", result.getCertificateSettings().getFallbackCertificate());
    }

    @Test
    public void shouldPatchKeyRetrievalSettings() {
        PatchKeyRetrievalSettings patchKeyRetrieval = new PatchKeyRetrievalSettings();
        patchKeyRetrieval.setFetchTimeoutMs(Optional.of(1234));
        patchKeyRetrieval.setAllowPrivateIpAddress(Optional.of(true));
        PatchDomain patch = new PatchDomain();
        patch.setKeyRetrievalSettings(Optional.of(patchKeyRetrieval));

        Domain result = patch.patch(new Domain());

        assertEquals(1234, result.getKeyRetrievalSettings().getFetchTimeoutMs());
        assertTrue(result.getKeyRetrievalSettings().isAllowPrivateIpAddress());
        assertEquals(KeyRetrievalSettings.DEFAULT_CACHE_TTL_SECONDS, result.getKeyRetrievalSettings().getCacheTtlSeconds());
    }

    @Test
    public void shouldRelocateRetrievalLimitsWrittenAgainstTheSpiffeBlock() {
        PatchSpiffeDomainSettings patchSpiffe = new PatchSpiffeDomainSettings();
        patchSpiffe.setEnabled(Optional.of(true));
        patchSpiffe.setFetchTimeoutMs(Optional.of(1234));
        PatchOIDCSettings patchOidc = new PatchOIDCSettings();
        patchOidc.setWorkloadIdentitySettings(Optional.of(patchSpiffe));
        PatchDomain patch = new PatchDomain();
        patch.setOidc(Optional.of(patchOidc));

        Domain result = patch.patch(new Domain());

        assertEquals(1234, result.getKeyRetrievalSettings().getFetchTimeoutMs());
        assertTrue(result.getOidc().getWorkloadIdentitySettings().isEnabled());
        assertNull(result.getOidc().getWorkloadIdentitySettings().getFetchTimeoutMs());
    }

    @Test
    public void shouldPreferKeyRetrievalSettingsOverTheDeprecatedSpiffeLimits() {
        PatchSpiffeDomainSettings patchSpiffe = new PatchSpiffeDomainSettings();
        patchSpiffe.setFetchTimeoutMs(Optional.of(1234));
        PatchOIDCSettings patchOidc = new PatchOIDCSettings();
        patchOidc.setWorkloadIdentitySettings(Optional.of(patchSpiffe));
        PatchKeyRetrievalSettings patchKeyRetrieval = new PatchKeyRetrievalSettings();
        patchKeyRetrieval.setFetchTimeoutMs(Optional.of(999));
        PatchDomain patch = new PatchDomain();
        patch.setOidc(Optional.of(patchOidc));
        patch.setKeyRetrievalSettings(Optional.of(patchKeyRetrieval));

        Domain result = patch.patch(new Domain());

        assertEquals(999, result.getKeyRetrievalSettings().getFetchTimeoutMs());
    }

    @Test
    public void shouldRejectNonPositiveKeyRetrievalLimit() {
        PatchKeyRetrievalSettings patchKeyRetrieval = new PatchKeyRetrievalSettings();
        patchKeyRetrieval.setCacheMaxEntries(Optional.of(0));
        PatchDomain patch = new PatchDomain();
        patch.setKeyRetrievalSettings(Optional.of(patchKeyRetrieval));

        assertThrows(InvalidParameterException.class, () -> patch.patch(new Domain()));
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
        patchDomain.setLoginSettings(Optional.of(new PatchLoginSettings()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setAccountSettings(Optional.of(new AccountSettings()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setTags(Optional.of(Collections.singleton("patchTag")));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setCertificateSettings(Optional.of(new CertificateSettings()));
        assertEquals(new HashSet<>(Arrays.asList(Permission.DOMAIN_SETTINGS)), patchDomain.getRequiredPermissions());

        patchDomain = new PatchDomain();
        patchDomain.setKeyRetrievalSettings(Optional.of(new PatchKeyRetrievalSettings()));
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
