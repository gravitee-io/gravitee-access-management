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

package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.login.LoginSettings;
import org.junit.Test;

import static io.gravitee.am.repository.mongodb.management.internal.model.LoginSettingsMongo.convert;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginSettingsMongoTest {

    @Test
    public void mustInstantiateLoginSettingsWithoutHideFormWithoutSecondSteps() {
        var loginSettings = new LoginSettingsMongo();
        loginSettings.setHideForm(false);
        loginSettings.setIdentifierFirstLoginEnabled(false);

        assertResult(loginSettings.convert(), false, false);
    }

    @Test
    public void mustInstantiateLoginSettingsMongoWithoutHideFormWithoutSecondSteps() {
        var loginSettings = new LoginSettings();
        loginSettings.setHideForm(false);
        loginSettings.setIdentifierFirstEnabled(false);

        assertMongoResult(convert(loginSettings), false, false);
    }

    @Test
    public void mustInstantiateLoginSettingsWithHideFormWithoutSecondSteps() {
        var loginSettings = new LoginSettingsMongo();
        loginSettings.setHideForm(true);
        loginSettings.setIdentifierFirstLoginEnabled(false);

        assertResult(loginSettings.convert(), true, false);
    }

    @Test
    public void mustInstantiateLoginMongoSettingsWithHideFormWithoutSecondSteps() {
        var loginSettings = new LoginSettings();
        loginSettings.setHideForm(true);
        loginSettings.setIdentifierFirstEnabled(false);

        assertMongoResult(convert(loginSettings), true, false);
    }

    @Test
    public void mustInstantiateLoginSettingsWithoutHideFormWithSecondSteps() {
        var loginSettings = new LoginSettingsMongo();
        loginSettings.setHideForm(false);
        loginSettings.setIdentifierFirstLoginEnabled(true);

        assertResult(loginSettings.convert(), false, true);
    }

    @Test
    public void mustInstantiateLoginMongoSettingsWithoutHideFormWithSecondSteps() {
        var loginSettings = new LoginSettings();
        loginSettings.setHideForm(false);
        loginSettings.setIdentifierFirstEnabled(true);

        assertMongoResult(convert(loginSettings), false, true);
    }

    @Test
    public void mustInstantiateLoginSettingsWithHideFormWithSecondSteps() {
        var loginSettings = new LoginSettingsMongo();
        loginSettings.setHideForm(true);
        loginSettings.setIdentifierFirstLoginEnabled(true);
        // We cannot have both set to true
        assertResult(loginSettings.convert(), false, true);

    }

    @Test
    public void mustInstantiateLoginMongoSettingsWithHideFormWithSecondSteps() {
        var loginSettings = new LoginSettings();
        loginSettings.setHideForm(true);
        loginSettings.setIdentifierFirstEnabled(true);

        assertMongoResult(convert(loginSettings), false, true);
    }

    @Test
    public void mustReturnNullWithNullLocalSettings() {
        assertNull(convert(null));
    }

    private void assertResult(LoginSettings expectedSetting, boolean isHideForm, boolean isIdentifierFirstLoginEnabled) {
        assertEquals(expectedSetting.isHideForm(), isHideForm);
        assertEquals(expectedSetting.isIdentifierFirstEnabled(), isIdentifierFirstLoginEnabled);
    }

    private void assertMongoResult(LoginSettingsMongo expectedSetting, boolean isHideForm, boolean isIdentifierFirstLoginEnabled) {
        assertEquals(expectedSetting.isHideForm(), isHideForm);
        assertEquals(expectedSetting.isIdentifierFirstLoginEnabled(), isIdentifierFirstLoginEnabled);
    }

    @Test
    public void testConvertIncludesCertificateBasedAuthValues() {
        LoginSettings settings = new LoginSettings();
        settings.setCertificateBasedAuthEnabled(true);
        settings.setCertificateBasedAuthUrl("https://cba.example.com/login");

        LoginSettingsMongo mongo = convert(settings);
        assertTrue(mongo.isCertificateBasedAuthEnabled());
        assertEquals("https://cba.example.com/login", mongo.getCertificateBasedAuthUrl());

        LoginSettings converted = mongo.convert();
        assertTrue(converted.isCertificateBasedAuthEnabled());
        assertEquals("https://cba.example.com/login", converted.getCertificateBasedAuthUrl());
    }
}
