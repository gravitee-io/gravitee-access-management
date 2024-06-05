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
package io.gravitee.am.service.validators.notifier.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.validators.EmailFromAware;
import io.gravitee.am.service.validators.email.EmailDomainValidator;
import io.gravitee.am.service.validators.notifier.NotifierValidator.NotifierHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EmailNotifierFromValidatorImpl extends EmailFromAware implements EmailNotifierFromValidator{

    public static final String UNEXPECTED_MESSAGE = "An unexpected error has occurred while trying to validate notifier [%s]";
    public static final String INVALID_FROM = "Email from [%s] is invalid";
    public static final String EMAIL_NOTIFIER = "email-notifier";
    public EmailNotifierFromValidatorImpl(ObjectMapper objectMapper, EmailDomainValidator emailDomainValidator) {
        super(objectMapper, emailDomainValidator);
    }

    @Override
    public Optional<InvalidParameterException> validate(NotifierHolder notifier) {
        if (EMAIL_NOTIFIER.equalsIgnoreCase(notifier.getType())) {
            try {
                var from = getFrom(notifier.getConfiguration());
                return isValid(from) ? Optional.empty() : Optional.of(new InvalidParameterException(String.format(INVALID_FROM, from)));
            } catch (Exception e) {
                return Optional.of(new InvalidParameterException(String.format(UNEXPECTED_MESSAGE, notifier.getType())));
            }
        }
        return Optional.empty();
    }
}
