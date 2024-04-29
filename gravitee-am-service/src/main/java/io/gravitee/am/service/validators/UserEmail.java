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
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = UserEmail.UserEmailValidator.class)
public @interface UserEmail {
    String message() default "invalid email";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    String PROPERTY_USER_EMAIL_REQUIRED = "user.email.required";

    @Component
    @Slf4j
    class UserEmailValidator implements ConstraintValidator<UserEmail, String> {

        private final boolean emailRequired;

        public UserEmailValidator(Environment environment) {
            this.emailRequired = environment.getProperty(PROPERTY_USER_EMAIL_REQUIRED, boolean.class, true);
            log.debug("Validating user emails as {}", this.emailRequired ? "required" : "optional" );
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
                    context.buildConstraintViolationWithTemplate( "must be shorter than " + EmailValidatorImpl.EMAIL_MAX_LENGTH + " characters")
                            .addConstraintViolation();
                    return false;

            }
            return true;
        }
    }
}
