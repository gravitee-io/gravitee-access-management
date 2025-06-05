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
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.validators.dynamicparams.ClientRegistrationSettingsValidator.ValidationResult;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClientRegistrationSettingsValidatorTest {

    @Mock
    ApplicationService applicationService;

    @InjectMocks
    ClientRegistrationSettingsValidator validator;


    @Test
    public void simple_domain_test_disabled_dynamic_params() {
        Domain domain = new Domain();
        validator.validate(domain).test().assertValue(result -> result.clientsWithInvalidRedirectUris().isEmpty());
    }

    @Test
    public void simple_domain_test_enabled_dynamic_params_no_apps(){
        Domain domain = new Domain();
        domain.setId("domainId");
        OIDCSettings oidcSettings = new OIDCSettings();
        ClientRegistrationSettings clientRegistrationSettings = new ClientRegistrationSettings();
        oidcSettings.setClientRegistrationSettings(clientRegistrationSettings);
        domain.setOidc(oidcSettings);

        when(applicationService.findByDomain("domainId")).thenReturn(Single.just(Set.of()));

        clientRegistrationSettings.setAllowRedirectUriParamsExpressionLanguage(true);

        validator.validate(domain).test().assertValue(result -> result.clientsWithInvalidRedirectUris().isEmpty());
    }

    @Test
    public void simple_domain_test_enabled_dynamic_params_single_app(){
        Domain domain = new Domain();
        domain.setId("domainId");
        OIDCSettings oidcSettings = new OIDCSettings();
        ClientRegistrationSettings clientRegistrationSettings = new ClientRegistrationSettings();
        oidcSettings.setClientRegistrationSettings(clientRegistrationSettings);
        domain.setOidc(oidcSettings);
        clientRegistrationSettings.setAllowRedirectUriParamsExpressionLanguage(true);

        Application application = new Application();
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings applicationOAuthSettings = new ApplicationOAuthSettings();
        applicationSettings.setOauth(applicationOAuthSettings);
        application.setSettings(applicationSettings);

        applicationOAuthSettings.setRedirectUris(List.of("https://example.com/path"));

        when(applicationService.findByDomain("domainId")).thenReturn(Single.just(Set.of(application)));


        validator.validate(domain).test().assertValue(result -> result.clientsWithInvalidRedirectUris().isEmpty());
    }

    @Test
    public void simple_domain_test_enabled_dynamic_params_single_app_different_redirect_uris(){
        Domain domain = new Domain();
        domain.setId("domainId");
        OIDCSettings oidcSettings = new OIDCSettings();
        ClientRegistrationSettings clientRegistrationSettings = new ClientRegistrationSettings();
        oidcSettings.setClientRegistrationSettings(clientRegistrationSettings);
        domain.setOidc(oidcSettings);
        clientRegistrationSettings.setAllowRedirectUriParamsExpressionLanguage(true);

        Application application = new Application();
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings applicationOAuthSettings = new ApplicationOAuthSettings();
        applicationSettings.setOauth(applicationOAuthSettings);
        application.setSettings(applicationSettings);

        applicationOAuthSettings.setRedirectUris(List.of("https://example.com/path", "https://example2.com/path2"));

        when(applicationService.findByDomain("domainId")).thenReturn(Single.just(Set.of(application)));


        validator.validate(domain).test().assertValue(result -> result.clientsWithInvalidRedirectUris().isEmpty());
    }

    @Test
    public void enabled_dynamic_params_single_app_invalid_redirect_uris(){
        Domain domain = new Domain();
        domain.setId("domainId");
        OIDCSettings oidcSettings = new OIDCSettings();
        ClientRegistrationSettings clientRegistrationSettings = new ClientRegistrationSettings();
        oidcSettings.setClientRegistrationSettings(clientRegistrationSettings);
        domain.setOidc(oidcSettings);
        clientRegistrationSettings.setAllowRedirectUriParamsExpressionLanguage(true);

        Application application = new Application();
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings applicationOAuthSettings = new ApplicationOAuthSettings();
        applicationSettings.setOauth(applicationOAuthSettings);
        application.setSettings(applicationSettings);

        applicationOAuthSettings.setRedirectUris(List.of("https://example.com/path", "https://example.com/path?test=test"));

        when(applicationService.findByDomain("domainId")).thenReturn(Single.just(Set.of(application)));


        validator.validate(domain).test().assertValue(result -> result.clientsWithInvalidRedirectUris().size() == 1);
    }

}