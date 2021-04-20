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

import static org.junit.Assert.assertFalse;

import io.gravitee.am.model.oidc.OIDCSettings;
import org.junit.Assert;
import org.junit.Test;

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
        assertFalse("By default dcr settings should be disabled", isEnabled);
    }

    @Test
    public void isDynamicClientRegistrationEnabled_missingOidcSettings() {
        Domain domain = new Domain();

        boolean isEnabled = domain.isDynamicClientRegistrationEnabled();
        assertFalse("By default dcr settings should be disabled", isEnabled);
    }

    @Test
    public void isOpenDynamicClientRegistrationEnabled_defaultSettings() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isOpenDynamicClientRegistrationEnabled();
        assertFalse("By default should be disabled", isEnabled);
    }

    @Test
    public void isOpenDynamicClientRegistrationEnabled_isOpenButDcrIsDisabled() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());
        domain.getOidc().getClientRegistrationSettings().setOpenDynamicClientRegistrationEnabled(true);

        boolean isEnabled = domain.isOpenDynamicClientRegistrationEnabled();
        assertFalse("Should be disabled if dcr is not enabled...", isEnabled);
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
        assertFalse("By default dcr settings should be disabled", isEnabled);
    }

    @Test
    public void isRedirectUriLocalhostAllowed_missingOidcSettings() {
        Domain domain = new Domain();

        boolean isEnabled = domain.isRedirectUriLocalhostAllowed();
        assertFalse("By default dcr settings should be disabled", isEnabled);
    }

    @Test
    public void isRedirectUriUnsecuredHttpSchemeAllowed() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isRedirectUriUnsecuredHttpSchemeAllowed();
        assertFalse("By default dcr settings should be disabled", isEnabled);
    }

    @Test
    public void isRedirectUriUnsecuredHttpSchemeAllowed_missingOidcSettings() {
        Domain domain = new Domain();

        boolean isEnabled = domain.isRedirectUriUnsecuredHttpSchemeAllowed();
        assertFalse("By default dcr settings should be disabled", isEnabled);
    }

    @Test
    public void isRedirectUriWildcardAllowed() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isRedirectUriWildcardAllowed();
        assertFalse("By default dcr settings should be disabled", isEnabled);
    }

    @Test
    public void isRedirectUriWildcardAllowed_missingOidcSettings() {
        Domain domain = new Domain();

        boolean isEnabled = domain.isRedirectUriWildcardAllowed();
        assertFalse("By default dcr settings should be disabled", isEnabled);
    }

    @Test
    public void isRedirectUriStrictMatching() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        boolean isEnabled = domain.isRedirectUriStrictMatching();
        assertFalse("By default strict matching settings should be disabled", isEnabled);
    }
}
