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
import io.gravitee.am.service.validators.notifier.NotifierValidator.NotifierHolder;
import io.gravitee.am.service.validators.notifier.email.EmailNotifierFromValidator;
import io.gravitee.am.service.validators.notifier.email.EmailNotifierFromValidatorImpl;
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
public class EmailNotifierFromValidatorTest {

    @Mock
    private EmailDomainValidator emailDomainValidator;
    private EmailNotifierFromValidator emailNotifierFromValidator;

    @BeforeEach
    void setup() {
        emailNotifierFromValidator = new EmailNotifierFromValidatorImpl(new ObjectMapper(), emailDomainValidator);
    }

    @Test
    void must_not_validate_invalid() {
        assertTrue(emailNotifierFromValidator.validate(new NotifierHolder("other-policy", "{}")).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("params_that_must_validate_regarding_configuration")
    void must_validate_invalid_regarding_configuration(String pattern, String configuration, boolean expected) {
        var resourceHolder = new NotifierHolder(EmailNotifierFromValidatorImpl.EMAIL_NOTIFIER, configuration);
        when(emailDomainValidator.validate(eq(pattern))).thenReturn(expected);

        assertEquals(expected, emailNotifierFromValidator.validate(resourceHolder).isEmpty());
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
