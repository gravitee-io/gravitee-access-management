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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.service.validators.email.EmailDomainValidator;
import io.gravitee.am.service.validators.flow.policy.SendEmailPolicyValidator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class SendEmailPolicyValidatorTest {

    @Mock
    private EmailDomainValidator emailDomainValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SendEmailPolicyValidator sendEmailPolicyValidator;

    @BeforeEach
    public void setup() {
        sendEmailPolicyValidator = new SendEmailPolicyValidator(
                objectMapper,
                emailDomainValidator
        );
    }

    @Test
    public void must_not_validate_invalid() {
        assertTrue(sendEmailPolicyValidator.validate(getStep("other-policy", "{}")).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("params_that_must_validate_regarding_configuration")
    public void must_validate_invalid_regarding_configuration(String pattern, String configuration, boolean expected) {
        Step step = getStep(SendEmailPolicyValidator.POLICY_AM_SEND_EMAIL, configuration);
        when(emailDomainValidator.validate(eq(pattern))).thenReturn(expected);

        assertEquals(expected, sendEmailPolicyValidator.validate(step).isEmpty());
    }

    public static Stream<Arguments> params_that_must_validate_regarding_configuration() {
        return Stream.of(
                Arguments.of("valid@example.com", "{\"from\":\"valid@example.com\"}", true),
                Arguments.of("invalid@example.com", "{\"from\":\"invalid@example.com\"}", false),
                Arguments.of("", "{\"from\":\"\"}", false),
                Arguments.of(null, "{}", false)
        );
    }


    @Test
    public void must_not_validate_IO_exception() {
        Step step = getStep(SendEmailPolicyValidator.POLICY_AM_SEND_EMAIL, "{ \"from\":\"valid@example.com\" ");
        assertTrue(sendEmailPolicyValidator.validate(step).isPresent());
    }

    private static Step getStep(String policy, String configuration) {
        Step step = new Step();
        step.setPolicy(policy);
        step.setConfiguration(configuration);
        return step;
    }
}