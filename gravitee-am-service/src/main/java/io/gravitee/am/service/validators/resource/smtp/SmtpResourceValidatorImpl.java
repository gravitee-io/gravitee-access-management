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
package io.gravitee.am.service.validators.resource.smtp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.validators.EmailFromAware;
import io.gravitee.am.service.validators.email.EmailDomainValidator;
import io.gravitee.am.service.validators.resource.ResourceValidator.ResourceHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class SmtpResourceValidatorImpl extends EmailFromAware implements SmtpResourceValidator {

    public static final String UNEXPECTED_MESSAGE = "An unexpected error has occurred while trying to validate resource [%s]";
    public static final String INVALID_FROM = "Emaail from [%s] is invalid";
    public static final String SMTP_AM_RESOURCE = "smtp-am-resource";

    public SmtpResourceValidatorImpl(ObjectMapper objectMapper, EmailDomainValidator emailDomainValidator) {
        super(objectMapper, emailDomainValidator);
    }

    @Override
    public Optional<InvalidParameterException> validate(ResourceHolder resource) {
        if (SMTP_AM_RESOURCE.equalsIgnoreCase(resource.getName())) {
            try {
                var from = getFrom(resource.getConfiguration());
                return isValid(from) ? Optional.empty() : Optional.of(new InvalidParameterException(String.format(INVALID_FROM, from)));
            } catch (Exception e) {
                return Optional.of(new InvalidParameterException(String.format(UNEXPECTED_MESSAGE, resource.getName())));
            }
        }
        return Optional.empty();
    }
}
