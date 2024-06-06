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

import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_PATTERN;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
class EmailValidatorTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "email@gravitee.io",
                    "firstname.lastname@gravitee.io",
                    "email@subdomain.gravitee.io",
                    "firstname+lastname@gravitee.io",
                    "firstname.lastname+1-test@gravitee.io",
                    "1234567890@gravitee.io",
                    "email@gravitee-io.com",
                    "_______@gravitee.io",
                    "firstname-lastname@gravitee.io",
                    "firstname-lastname@gravitee.io",
                    "firstname-lastname@gravitee.americanexpress"
            }
    )
    void validate(String email) {
        var emailValidator = new EmailValidatorImpl(EMAIL_PATTERN, true);
        assertThat(emailValidator.validate(email))
                .as("%s should be valid", email)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "email",
            "#@%^%#$@#$@#.com",
            "@gravitee.io",
            "Joe Smith <email@gravitee.io>",
            "email@gravitee@gravitee.io",
            ".email@gravitee.io",
            "email.@gravitee.io",
            "email..email@gravitee.io",
            "email@gravitee",
            "email@gravitee",
            "email@gravitee..io",
            "firstname-lastname@gravitee.verylongextension"
    })
    void validate_notValid(String email) {
        var emailValidator = new EmailValidatorImpl(EMAIL_PATTERN, true);
        assertThat(emailValidator.validate(email)).isFalse();

    }

    @ParameterizedTest
    @ValueSource(strings = {
            "émail@gravitee.io",
            "émail@gråvitèê.iø",
            "电子邮件@重力.阿约",
            "めーる@ぐらびてぃー.あよ",
            "メール@グラビティー.アヨ",
            "이메일@중력.아요",
            "почта@гравитация.Айо",
            "ηλεκτρονικόταχυδρομείο@βαρύτητα.ιο"
    })
    void validate_extended(String email) {
        var emailValidator = new EmailValidatorImpl("^[\\p{L}0-9_+-]+(?:\\.[\\p{L}0-9_+-]+)*@(?:[\\p{L}0-9-]+\\.)+[\\p{L}]{2,7}$", true);
        assertThat(emailValidator.validate(email)).isTrue();
    }


    @Test
    void emailRequired_emptyNotValid() {
        var emailValidator = new EmailValidatorImpl(EMAIL_PATTERN, true);
        assertThat(emailValidator.validate("")).isFalse();
    }

    @Test
    void emailRequired_nullIsValid() {
        var emailValidator = new EmailValidatorImpl(EMAIL_PATTERN, true);
        assertThat(emailValidator.validate(null)).isTrue();
    }

    @Test
    void emailOptional_emptyIsValid() {
        var emailValidator = new EmailValidatorImpl(EMAIL_PATTERN, false);
        assertThat(emailValidator.validate("")).isTrue();
    }

    @Test
    void emailOptional_nullIsValid() {
        var emailValidator = new EmailValidatorImpl(EMAIL_PATTERN, false);
        assertThat(emailValidator.validate(null)).isTrue();
    }
}
