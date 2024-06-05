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
package io.gravitee.am.service.validators.flow;

import io.gravitee.am.service.model.Flow;
import io.gravitee.am.service.validators.flow.policy.SendEmailPolicyValidator;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class FlowValidatorImpl implements FlowValidator {

    private final SendEmailPolicyValidator emailPolicyValidator;

    public FlowValidatorImpl(SendEmailPolicyValidator emailPolicyValidator) {
        this.emailPolicyValidator = emailPolicyValidator;
    }

    @Override
    public Completable validate(Flow flow) {
        return validateFlow(flow)
                .findFirst()
                .map(Completable::error)
                .orElse(Completable.complete());
    }

    private Stream<Exception> validateFlow(Flow flow) {
        return Stream.concat(flow.getPre().stream(), flow.getPost().stream())
                .map(emailPolicyValidator::validate)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @Override
    public Completable validateAll(List<Flow> flows) {
        return flows.stream().flatMap(this::validateFlow)
                .findFirst()
                .map(Completable::error)
                .orElse(Completable.complete());
    }
}
