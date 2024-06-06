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

import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.NewEmail;
import io.gravitee.am.service.model.UpdateEmail;
import io.gravitee.am.service.validators.email.EmailDomainValidator;
import io.gravitee.am.service.validators.email.resource.EmailTemplateValidatorImpl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
class EmailResourceValidatorTest {

    @Test
    void must_succeed_validation() {
        var mockDomainValidator = mock(EmailDomainValidator.class);
        when(mockDomainValidator.validate("valid@email.com")).thenReturn(true);

        var validator = new EmailTemplateValidatorImpl(mockDomainValidator);

        var email = new NewEmail();
        email.setFrom("valid@email.com");

        var resultCompletable = validator.validate(email).test();
        resultCompletable.awaitDone(10, TimeUnit.SECONDS);
        resultCompletable.assertComplete();

    }

    @Test
    void must_fail_validation() {
        var emailDomainValidator = mock(EmailDomainValidator.class);
        when(emailDomainValidator.validate("invalid@email.com")).thenReturn(false);

        var validator = new EmailTemplateValidatorImpl(emailDomainValidator);

        var email = new UpdateEmail();
        email.setFrom("invalid@email.com");

        var resultCompletable = validator.validate(email).test();
        resultCompletable.awaitDone(10, TimeUnit.SECONDS);
        resultCompletable.assertError(InvalidParameterException.class);
    }

}
