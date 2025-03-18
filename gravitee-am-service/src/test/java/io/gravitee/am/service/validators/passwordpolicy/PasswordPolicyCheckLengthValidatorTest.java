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
package io.gravitee.am.service.validators.passwordpolicy;

import io.gravitee.am.service.model.NewPasswordPolicy;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordPolicyCheckLengthValidatorTest {

    private PasswordPolicyCheckLengthValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyCheckLengthValidator();
    }

    @Test
    void testValidLengthRange() {
        NewPasswordPolicy validObject = new NewPasswordPolicy();
        validObject.setMinLength(5);
        validObject.setMaxLength(10);

        boolean result = validator.isValid(validObject, Mockito.mock(ConstraintValidatorContext.class));

        assertTrue(result);
    }

    @Test
    void testInvalidLengthRange_maxLength_smaller_than_minLength() {
        NewPasswordPolicy validObject = new NewPasswordPolicy();
        validObject.setMinLength(10);
        validObject.setMaxLength(5);

        boolean result = validator.isValid(validObject, Mockito.mock(ConstraintValidatorContext.class));

        assertFalse(result);
    }
    

}