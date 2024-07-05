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
import io.gravitee.am.service.validators.resource.ResourceValidator;
import io.gravitee.am.service.validators.resource.ResourceValidator.ResourceHolder;
import io.gravitee.am.service.validators.resource.ResourceValidatorImpl;
import io.gravitee.am.service.validators.resource.http.HttpResourceValidator;
import io.gravitee.am.service.validators.resource.smtp.SmtpResourceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class ResourceValidatorTest {

    @Mock
    private SmtpResourceValidator smtpResourceValidator;
    @Mock
    private HttpResourceValidator httpResourceValidator;
    private ResourceValidator resourceValidator;

    @BeforeEach
    void setup() {
        resourceValidator = new ResourceValidatorImpl(smtpResourceValidator, httpResourceValidator);
    }

    @Test
    void must_not_validate() {
        when(smtpResourceValidator.validate(any())).thenReturn(Optional.empty());
        var observer = resourceValidator.validate(new ResourceHolder("any-name", "any-config")).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors();
    }

    @Test
    void must_validate() {
        when(smtpResourceValidator.validate(any())).thenReturn(Optional.of(new InvalidParameterException("Invalid parameter")));
        var observer = resourceValidator.validate(new ResourceHolder("any-policy", "any-config")).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidParameterException.class);
    }
}
