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

package io.gravitee.am.service.validators;

import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.service.validators.accountsettings.AccountSettingsValidator;
import io.gravitee.am.service.validators.accountsettings.AccountSettingsValidatorImpl;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountSettingsValidatorTest {

    private final AccountSettingsValidator accountSettingsValidator = new AccountSettingsValidatorImpl();

    @Test
    public void validSettings_null() {
        assertTrue(accountSettingsValidator.validate(null));
    }

    @Test
    public void validSettings_notResetPassword() {
        final AccountSettings element = new AccountSettings();
        element.setResetPasswordCustomForm(false);
        assertTrue(accountSettingsValidator.validate(element));
    }

    @Test
    public void validSettings_hasNoneOfTheFields() {
        final AccountSettings element = new AccountSettings();
        element.setResetPasswordCustomForm(true);
        assertTrue(accountSettingsValidator.validate(element));
    }

    @Test
    public void validSettings_hasNoneOfTheFields_2() {
        final AccountSettings element = new AccountSettings();
        element.setResetPasswordCustomForm(true);
        element.setResetPasswordCustomFormFields(List.of());
        assertTrue(accountSettingsValidator.validate(element));
    }

    @Test
    public void validSettings_containsEmail() {
        final AccountSettings element = new AccountSettings();
        element.setResetPasswordCustomForm(true);
        final FormField email = new FormField();
        email.setKey("email");
        element.setResetPasswordCustomFormFields(List.of(email));
        assertTrue(accountSettingsValidator.validate(element));
    }

    @Test
    public void validSettings_containsUsername() {
        final AccountSettings element = new AccountSettings();
        element.setResetPasswordCustomForm(true);
        final FormField username = new FormField();
        username.setKey("username");
        element.setResetPasswordCustomFormFields(List.of(username));
        assertTrue(accountSettingsValidator.validate(element));
    }

    @Test
    public void validSettings_containsEmailAndUsername() {
        final AccountSettings element = new AccountSettings();
        element.setResetPasswordCustomForm(true);
        final FormField email = new FormField();
        email.setKey("email");
        final FormField username = new FormField();
        username.setKey("username");
        element.setResetPasswordCustomFormFields(List.of(email, username));
        assertTrue(accountSettingsValidator.validate(element));
    }

    @Test
    public void validSettings_containsCustomAttribute() {
        final AccountSettings element = new AccountSettings();
        element.setResetPasswordCustomForm(true);
        final FormField employeeId = new FormField();
        employeeId.setKey("employeeId");
        element.setResetPasswordCustomFormFields(List.of(employeeId));
        assertTrue(accountSettingsValidator.validate(element));
    }

    @Test
    public void invalidSettings_invalidFieldKey() {
        final AccountSettings element = new AccountSettings();
        element.setResetPasswordCustomForm(true);
        final FormField email = new FormField();
        email.setKey("email");
        final FormField invalidField = new FormField();
        invalidField.setKey("invalid key");
        element.setResetPasswordCustomFormFields(List.of(email, invalidField));
        assertFalse(accountSettingsValidator.validate(element));
    }

    @Test
    public void validSettings_customAttributeOnly() {
        final AccountSettings element = new AccountSettings();
        element.setResetPasswordCustomForm(true);
        final FormField employeeId = new FormField();
        employeeId.setKey("employeeId");
        element.setResetPasswordCustomFormFields(List.of(employeeId));
        assertTrue(accountSettingsValidator.validate(element));
    }
}
