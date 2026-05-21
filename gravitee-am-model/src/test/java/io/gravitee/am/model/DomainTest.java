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
package io.gravitee.am.model;

import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainTest {


    @Test
    public void isDynamicClientRegistrationEnabled_defaultSettings() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isDynamicClientRegistrationEnabled();
        assertFalse("By default dcr settings should be disabled",isEnabled);
    }

    @Test
    public void isDynamicClientRegistrationEnabled_missingOidcSettings() {
        Domain domain = new Domain();

        boolean isEnabled = domain.isDynamicClientRegistrationEnabled();
        assertFalse("By default dcr settings should be disabled",isEnabled);
    }

    @Test
    public void isOpenDynamicClientRegistrationEnabled_defaultSettings() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isOpenDynamicClientRegistrationEnabled();
        assertFalse("By default should be disabled",isEnabled);
    }

    @Test
    public void isOpenDynamicClientRegistrationEnabled_isOpenButDcrIsDisabled() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());
        domain.getOidc().getClientRegistrationSettings().setOpenDynamicClientRegistrationEnabled(true);

        boolean isEnabled = domain.isOpenDynamicClientRegistrationEnabled();
        assertFalse("Should be disabled if dcr is not enabled...",isEnabled);
    }

    @Test
    public void isOpenDynamicClientRegistrationEnabled_ok() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());
        domain.getOidc().getClientRegistrationSettings().setDynamicClientRegistrationEnabled(true);
        domain.getOidc().getClientRegistrationSettings().setOpenDynamicClientRegistrationEnabled(true);

        boolean isEnabled = domain.isOpenDynamicClientRegistrationEnabled();
        Assert.assertTrue("Should be enabled", isEnabled);
    }

    @Test
    public void isRedirectUriLocalhostAllowed() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isRedirectUriLocalhostAllowed();
        assertFalse("By default dcr settings should be disabled",isEnabled);
    }

    @Test
    public void isRedirectUriLocalhostAllowed_missingOidcSettings() {
        Domain domain = new Domain();

        boolean isEnabled = domain.isRedirectUriLocalhostAllowed();
        assertFalse("By default dcr settings should be disabled",isEnabled);
    }

    @Test
    public void isRedirectUriUnsecuredHttpSchemeAllowed() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isRedirectUriUnsecuredHttpSchemeAllowed();
        assertFalse("By default dcr settings should be disabled",isEnabled);
    }

    @Test
    public void isRedirectUriUnsecuredHttpSchemeAllowed_missingOidcSettings() {
        Domain domain = new Domain();

        boolean isEnabled = domain.isRedirectUriUnsecuredHttpSchemeAllowed();
        assertFalse("By default dcr settings should be disabled",isEnabled);
    }

    @Test
    public void isRedirectUriWildcardAllowed() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isRedirectUriWildcardAllowed();
        assertFalse("By default dcr settings should be disabled",isEnabled);
    }

    @Test
    public void isRedirectUriWildcardAllowed_missingOidcSettings() {
        Domain domain = new Domain();

        boolean isEnabled = domain.isRedirectUriWildcardAllowed();
        assertFalse("By default dcr settings should be disabled",isEnabled);
    }

    @Test
    public void isRedirectUriStrictMatching() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isRedirectUriStrictMatching();
        assertFalse("By default strict matching settings should be disabled",isEnabled);
    }

    @Test
    public void isRedirectUriExpressionLanguageEnabledMatching() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isRedirectUriExpressionLanguageEnabled();
        assertFalse("By default EL param evaluation should be disabled",isEnabled);
    }

    @Test
    public void copyConstructor_deepCopiesNestedSettings() {
        Domain original = new Domain("d1");
        original.setOidc(OIDCSettings.defaultSettings());
        original.getOidc().setCimdSettings(new CIMDSettings());
        original.getOidc().getCimdSettings().setRevokeOnDocumentChange(true);
        original.setLoginSettings(new LoginSettings());
        original.setAccountSettings(new AccountSettings());
        original.setPasswordSettings(new PasswordSettings());
        original.setSaml(new SAMLSettings());
        original.setSecretExpirationSettings(new SecretExpirationSettings());
        original.setCertificateSettings(new CertificateSettings());

        Domain copy = new Domain(original);

        assertNotSame("oidc must be deep-copied", original.getOidc(), copy.getOidc());
        assertNotSame("oidc.cimdSettings must be deep-copied",
                original.getOidc().getCimdSettings(), copy.getOidc().getCimdSettings());
        assertNotSame("oidc.clientRegistrationSettings must be deep-copied",
                original.getOidc().getClientRegistrationSettings(), copy.getOidc().getClientRegistrationSettings());
        assertNotSame("oidc.securityProfileSettings must be deep-copied",
                original.getOidc().getSecurityProfileSettings(), copy.getOidc().getSecurityProfileSettings());
        assertNotSame("oidc.cibaSettings must be deep-copied",
                original.getOidc().getCibaSettings(), copy.getOidc().getCibaSettings());
        assertNotSame("loginSettings must be deep-copied", original.getLoginSettings(), copy.getLoginSettings());
        assertNotSame("accountSettings must be deep-copied", original.getAccountSettings(), copy.getAccountSettings());
        assertNotSame("passwordSettings must be deep-copied", original.getPasswordSettings(), copy.getPasswordSettings());
        assertNotSame("saml must be deep-copied", original.getSaml(), copy.getSaml());
        assertNotSame("secretExpirationSettings must be deep-copied",
                original.getSecretExpirationSettings(), copy.getSecretExpirationSettings());
        assertNotSame("certificateSettings must be deep-copied",
                original.getCertificateSettings(), copy.getCertificateSettings());

        copy.getOidc().getCimdSettings().setRevokeOnDocumentChange(false);
        assertTrue("Mutating the copy's CIMD settings must not affect the original",
                original.getOidc().getCimdSettings().isRevokeOnDocumentChange());
        assertFalse(copy.getOidc().getCimdSettings().isRevokeOnDocumentChange());
    }

    @Test
    public void copyConstructor_preservesNullNestedSettings() {
        Domain original = new Domain("d1");
        // Nested settings deliberately left null.

        Domain copy = new Domain(original);

        assertEquals("d1", copy.getId());
        Assert.assertNull(copy.getOidc());
        Assert.assertNull(copy.getLoginSettings());
        Assert.assertNull(copy.getAccountSettings());
        Assert.assertNull(copy.getPasswordSettings());
        Assert.assertNull(copy.getSaml());
        Assert.assertNull(copy.getSecretExpirationSettings());
        Assert.assertNull(copy.getCertificateSettings());
    }

    @Test
    public void copyConstructor_copiesVhosts() {
        VirtualHost vhost = new VirtualHost();
        vhost.setHost("auth.example.com");
        vhost.setPath("/am");
        List<VirtualHost> vhosts = new ArrayList<>();
        vhosts.add(vhost);

        Domain original = new Domain("d1");
        original.setVhosts(vhosts);

        Domain copy = new Domain(original);

        assertEquals(1, copy.getVhosts().size());
        assertEquals("auth.example.com", copy.getVhosts().get(0).getHost());
        assertNotSame(original.getVhosts(), copy.getVhosts());

        original.getVhosts().add(new VirtualHost());
        assertEquals(1, copy.getVhosts().size());
    }

    @Test
    public void copyConstructor_preservesNullVhosts() {
        Domain original = new Domain("d1");

        Domain copy = new Domain(original);

        Assert.assertNull(copy.getVhosts());
    }
}
