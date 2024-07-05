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
package io.gravitee.am.service.validators.resource;

import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.validators.Validator;
import io.gravitee.am.service.validators.resource.http.HttpResourceValidator;
import io.gravitee.am.service.validators.resource.smtp.SmtpResourceValidator;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ResourceValidatorImpl implements ResourceValidator {

    private final List<Validator<ResourceHolder, Optional<InvalidParameterException>>> resourceValidators;

    public ResourceValidatorImpl(SmtpResourceValidator smtpResourceValidator, HttpResourceValidator httpResourceValidator) {
        this.resourceValidators = List.of(smtpResourceValidator, httpResourceValidator);
    }

    @Override
    public Completable validate(ResourceHolder element) {
        return resourceValidators.stream()
                .map(validator -> validator.validate(element))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .map(Completable::error)
                .orElse(Completable.complete());
    }
}
