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
package io.gravitee.am.service.validators.flow.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.validators.EmailFromAware;
import io.gravitee.am.service.validators.email.EmailDomainValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class SendEmailPolicyValidator extends EmailFromAware implements PolicyValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendEmailPolicyValidator.class);
    public static final String POLICY_AM_SEND_EMAIL = "policy-am-send-email";
    public static final String UNEXPECTED_MESSAGE = "An unexpected error has occurred while trying to validate step [%s], [%s]";
    public static final String INVALID_FROM = "Step email from [%s] from step [%s] - [%s] is invalid";

    public SendEmailPolicyValidator(ObjectMapper objectMapper, EmailDomainValidator emailDomainValidator) {
        super(objectMapper, emailDomainValidator);
    }

    @Override
    public Optional<Exception> validate(Step element) {
        if (POLICY_AM_SEND_EMAIL.equalsIgnoreCase(element.getPolicy())) {
            try {
                var from = getFrom(element.getConfiguration());
                return isValid(from) ? Optional.empty() : Optional.of(
                        new InvalidParameterException(String.format(INVALID_FROM, from, element.getPolicy(), element.getName()))
                );
            } catch (Exception e) {
                LOGGER.warn("An unexpected error has occurred", e);
                return Optional.of(
                        new InvalidParameterException(String.format(UNEXPECTED_MESSAGE, element.getPolicy(), element.getName()))
                );
            }
        }
        return Optional.empty();
    }
}
