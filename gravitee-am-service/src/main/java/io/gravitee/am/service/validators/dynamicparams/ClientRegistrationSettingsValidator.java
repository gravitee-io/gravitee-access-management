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
package io.gravitee.am.service.validators.dynamicparams;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.validators.Validator;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


@Component
public class ClientRegistrationSettingsValidator implements Validator<Domain, Single<ClientRegistrationSettingsValidator.ValidationResult>> {
    private final ApplicationService applicationService;

    @Autowired
    public ClientRegistrationSettingsValidator(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    private final ClientRedirectUrisValidator registrationSettingsValidator = new ClientRedirectUrisValidator();

    public record ValidationResult(List<String> clientsWithInvalidRedirectUris) {}

    @Override
    public Single<ValidationResult> validate(Domain domain) {
        if (domain.isRedirectUriExpressionLanguageEnabled()) {
            return applicationService.findByDomain(domain.getId())
                    .map(this::validateApplications)
                    .map(ValidationResult::new);
        } else {
            return Single.just(new ValidationResult(List.of()));
        }
    }

    private List<String> validateApplications(Set<Application> apps) {
        return apps.stream()
                .flatMap(app -> {
                    if(!validateApplications(app)){
                        return Stream.of(app.getName());
                    } else {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private boolean validateApplications(Application app) {
        return Optional.ofNullable(app.getSettings())
                .map(ApplicationSettings::getOauth)
                .map(ApplicationOAuthSettings::getRedirectUris)
                .map(registrationSettingsValidator::validateRedirectUris)
                .orElse(true);
    }

}
