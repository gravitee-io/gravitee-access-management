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
package io.gravitee.am.service.validators.email;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public
class UserEmailConstraintValidator implements ConstraintValidator<UserEmail, String> {

    private final boolean emailRequired;

    public UserEmailConstraintValidator(Environment environment) {
        this.emailRequired = environment.getProperty(UserEmail.PROPERTY_USER_EMAIL_REQUIRED, boolean.class, true);
        log.debug("Validating user emails as {}", this.emailRequired ? "required" : "optional");
    }
    @Override
    public void initialize(UserEmail constraintAnnotation) {
        // nothing to do
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        if (emailRequired && (value == null || value.isBlank())) {
            context.buildConstraintViolationWithTemplate("must not be blank")
                    .addConstraintViolation();
            return false;
        }
        if (value != null && value.length() > EmailValidatorImpl.EMAIL_MAX_LENGTH) {
            context.buildConstraintViolationWithTemplate("must not be greater than " + EmailValidatorImpl.EMAIL_MAX_LENGTH)
                    .addConstraintViolation();
            return false;

        }
        return true;
    }
}
