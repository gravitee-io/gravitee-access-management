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

import io.gravitee.am.model.oidc.OIDCSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertFalse(domain.isDynamicClientRegistrationEnabled());
    }

    @Test
    public void isDynamicClientRegistrationEnabled_missingOidcSettings() {
        assertFalse(new Domain().isDynamicClientRegistrationEnabled());
    }

    @Test
    public void isOpenDynamicClientRegistrationEnabled_defaultSettings() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        assertFalse(domain.isOpenDynamicClientRegistrationEnabled());
    }

    @Test
    public void isOpenDynamicClientRegistrationEnabled_isOpenButDcrIsDisabled() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());
        domain.getOidc().getClientRegistrationSettings().setOpenDynamicClientRegistrationEnabled(true);

        assertFalse(domain.isOpenDynamicClientRegistrationEnabled());
    }

    @Test
    public void isOpenDynamicClientRegistrationEnabled_ok() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());
        domain.getOidc().getClientRegistrationSettings().setDynamicClientRegistrationEnabled(true);
        domain.getOidc().getClientRegistrationSettings().setOpenDynamicClientRegistrationEnabled(true);

        assertTrue(domain.isOpenDynamicClientRegistrationEnabled());
    }

    @Test
    public void isRedirectUriLocalhostAllowed() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        assertFalse(domain.isRedirectUriLocalhostAllowed());
    }

    @Test
    public void isRedirectUriLocalhostAllowed_missingOidcSettings() {
        assertFalse(new Domain().isRedirectUriLocalhostAllowed());
    }

    @Test
    public void isRedirectUriUnsecuredHttpSchemeAllowed() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        assertFalse(domain.isRedirectUriUnsecuredHttpSchemeAllowed());
    }

    @Test
    public void isRedirectUriUnsecuredHttpSchemeAllowed_missingOidcSettings() {
        Domain domain = new Domain();

        assertFalse(domain.isRedirectUriUnsecuredHttpSchemeAllowed());
    }

    @Test
    public void isRedirectUriWildcardAllowed() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        assertFalse(domain.isRedirectUriWildcardAllowed());
    }

    @Test
    public void isRedirectUriWildcardAllowed_missingOidcSettings() {
        Domain domain = new Domain();

        assertFalse(domain.isRedirectUriWildcardAllowed());
    }

    @Test
    public void isRedirectUriStrictMatching() {
        Domain domain = new Domain();
        domain.setOidc(OIDCSettings.defaultSettings());

        assertFalse(domain.isRedirectUriStrictMatching());
    }
}
