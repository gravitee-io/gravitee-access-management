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
package io.gravitee.am.service.validators.email.resource;

import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.AbstractEmail;
import io.gravitee.am.service.validators.email.EmailDomainValidator;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.stereotype.Component;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EmailTemplateValidatorImpl implements EmailTemplateValidator {

    private final EmailDomainValidator emailDomainValidator;

    public EmailTemplateValidatorImpl(EmailDomainValidator emailDomainValidator) {
        this.emailDomainValidator = emailDomainValidator;
    }

    @Override
    public Completable validate(AbstractEmail email) {
        return emailDomainValidator.validate(email.getFrom()) ?
                Completable.complete() :
                Completable.error(new InvalidParameterException(
                        "Email from [" + email.getFrom() + "] is not a valid email"
                ));
    }
}
