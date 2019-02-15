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
package io.gravitee.am.service.authentication.crypto.password;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordValidatorTest {

    @Test
    public void testPassword_min_8_characters_at_least_one_letter_one_number() {
        PasswordValidator passwordValidator = new RegexPasswordValidator("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$");

        Assert.assertFalse(passwordValidator.validate("test"));
        Assert.assertFalse(passwordValidator.validate("password"));
        Assert.assertTrue(passwordValidator.validate("password01"));
    }

    @Test
    public void testPassword_min_8_characters_at_least_one_letter_one_number_one_special_character() {
        PasswordValidator passwordValidator = new RegexPasswordValidator("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$");

        Assert.assertFalse(passwordValidator.validate("test"));
        Assert.assertFalse(passwordValidator.validate("password"));
        Assert.assertFalse(passwordValidator.validate("password01"));
        Assert.assertTrue(passwordValidator.validate("password01*"));
    }

    @Test
    public void testPassword_min_8_characters_at_least_one_uppercase_letter_one_lowercase_letter_one_number_one_special_character() {
        PasswordValidator passwordValidator = new RegexPasswordValidator("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");

        Assert.assertFalse(passwordValidator.validate("test"));
        Assert.assertFalse(passwordValidator.validate("password"));
        Assert.assertFalse(passwordValidator.validate("password01"));
        Assert.assertFalse(passwordValidator.validate("password01*"));
        Assert.assertTrue(passwordValidator.validate("Password01*"));
    }

    @Test
    public void testPassword_min_8_characters_max_10_characters_at_least_one_uppercase_letter_one_lowercase_letter_one_number_one_special_character() {
        PasswordValidator passwordValidator = new RegexPasswordValidator("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,10}$");

        Assert.assertFalse(passwordValidator.validate("test"));
        Assert.assertFalse(passwordValidator.validate("password"));
        Assert.assertFalse(passwordValidator.validate("password01"));
        Assert.assertFalse(passwordValidator.validate("password01*"));
        Assert.assertFalse(passwordValidator.validate("Password01*"));
        Assert.assertTrue(passwordValidator.validate("Password0*"));
    }
}
