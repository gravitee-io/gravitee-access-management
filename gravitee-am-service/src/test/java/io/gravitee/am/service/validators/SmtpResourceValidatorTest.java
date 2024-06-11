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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.validators.email.EmailDomainValidator;
import io.gravitee.am.service.validators.resource.ResourceValidator.ResourceHolder;
import io.gravitee.am.service.validators.resource.smtp.SmtpResourceValidator;
import io.gravitee.am.service.validators.resource.smtp.SmtpResourceValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class SmtpResourceValidatorTest {

    @Mock
    private EmailDomainValidator emailDomainValidator;
    private SmtpResourceValidator smtpResourceValidator;

    @BeforeEach
    void setup() {
        smtpResourceValidator = new SmtpResourceValidatorImpl(new ObjectMapper(), emailDomainValidator);
    }

    @Test
    void must_not_validate_invalid() {
        assertTrue(smtpResourceValidator.validate(new ResourceHolder("other-policy", "{}")).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("params_that_must_validate_regarding_configuration")
    void must_validate_invalid_regarding_configuration(String pattern, String configuration, boolean expected) {
        var resourceHolder = new ResourceHolder(SmtpResourceValidatorImpl.SMTP_AM_RESOURCE, configuration);
        when(emailDomainValidator.validate(eq(pattern))).thenReturn(expected);

        assertEquals(expected, smtpResourceValidator.validate(resourceHolder).isEmpty());
    }

    public static Stream<Arguments> params_that_must_validate_regarding_configuration() {
        return Stream.of(
                Arguments.of("valid@example.com", "{\"from\":\"valid@example.com\"}", true),
                Arguments.of("invalid@example.com", "{\"from\":\"invalid@example.com\"}", false),
                Arguments.of("", "{\"from\":\"\"}", false),
                Arguments.of(null, "{}", false)
        );
    }

}
