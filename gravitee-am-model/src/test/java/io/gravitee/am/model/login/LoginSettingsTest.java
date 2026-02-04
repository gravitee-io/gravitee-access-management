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

package io.gravitee.am.model.login;

import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginSettingsTest {

    @Test
    public void mustInstantiateLoginSettingsWithoutHideFormWithoutSecondSteps() {
        var loginSettings = new LoginSettings();
        loginSettings.setHideForm(false);
        loginSettings.setIdentifierFirstEnabled(false);

        assertResult(loginSettings, false, false);
    }

    @Test
    public void mustInstantiateLoginSettingsWithHideFormWithoutSecondSteps() {
        var loginSettings = new LoginSettings();
        loginSettings.setHideForm(true);
        loginSettings.setIdentifierFirstEnabled(false);

        assertResult(loginSettings, true, false);
    }

    @Test
    public void mustInstantiateLoginSettingsWithoutHideFormWithSecondSteps() {
        var loginSettings = new LoginSettings();
        loginSettings.setHideForm(false);
        loginSettings.setIdentifierFirstEnabled(true);

        assertResult(loginSettings, false, true);
    }

    @Test
    public void mustInstantiateLoginSettingsWithHideFormWithSecondSteps() {
        var loginSettings = new LoginSettings();
        loginSettings.setHideForm(true);
        loginSettings.setIdentifierFirstEnabled(true);
        // We cannot have both set to true
        assertResult(loginSettings, false, true);

    }

    private void assertResult(LoginSettings loginSettings, boolean isHideForm, boolean isIdentifierFirstEnabled) {
        var expectedSetting = new LoginSettings(loginSettings);
        assertEquals(expectedSetting.isHideForm(), isHideForm);
        assertEquals(expectedSetting.isIdentifierFirstEnabled(), isIdentifierFirstEnabled);
    }

    @Test
    public void testCertificateBasedAuthEnabledGetterSetter() {
        LoginSettings settings = new LoginSettings();
        settings.setCertificateBasedAuthEnabled(true);
        assertTrue(settings.isCertificateBasedAuthEnabled());

        settings.setCertificateBasedAuthEnabled(false);
        assertFalse(settings.isCertificateBasedAuthEnabled());
    }

    @Test
    public void testCopyConstructorIncludesCertificateBasedAuthEnabled() {
        LoginSettings original = new LoginSettings();
        original.setCertificateBasedAuthEnabled(true);

        LoginSettings copy = new LoginSettings(original);
        assertTrue(copy.isCertificateBasedAuthEnabled());
    }

    @Test
    public void testCopyConstructorIncludesCertificateBasedAuthUrl() {
        LoginSettings settings = new LoginSettings();
        settings.setCertificateBasedAuthUrl("https://cba.example.com");

        LoginSettings copy = new LoginSettings(settings);        
        assertEquals("https://cba.example.com", copy.getCertificateBasedAuthUrl());
    }

    @Test
    public void testMagicLinkEnabledGetterSetter() {
        LoginSettings settings = new LoginSettings();
        settings.setMagicLinkAuthEnabled(true);
        assertTrue(settings.isMagicLinkAuthEnabled());

        settings.setMagicLinkAuthEnabled(false);
        assertFalse(settings.isMagicLinkAuthEnabled());
    }

    @Test
    public void testCopyConstructorIncludesMagicLinkEnabled() {
        LoginSettings original = new LoginSettings();
        original.setMagicLinkAuthEnabled(true);

        LoginSettings copy = new LoginSettings(original);
        assertTrue(copy.isMagicLinkAuthEnabled());
    }
}
