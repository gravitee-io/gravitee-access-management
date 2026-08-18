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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.webprotection.CspSettings;
import io.gravitee.am.model.webprotection.WebProtectionSettings;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.gravitee.am.service.validators.domain.DomainValidator;
import io.gravitee.am.service.validators.domain.DomainValidatorImpl;
import io.gravitee.am.service.validators.dynamicparams.ClientRegistrationSettingsValidator;
import io.gravitee.am.service.validators.path.PathValidatorImpl;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidatorImpl;
import io.gravitee.am.service.validators.webprotection.CspSettingsValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainValidatorTest {

    private DomainValidator domainValidator;
    private CspSettingsValidator cspSettingsValidator;

    @Before
    public void before(){
        final PathValidatorImpl pathValidator = new PathValidatorImpl();

        ApplicationService applicationService = Mockito.mock(ApplicationService.class);
        when(applicationService.findByDomain(Mockito.anyString())).thenReturn(Single.just(Set.of()));
        ClientRegistrationSettingsValidator clientRegistrationSettingsValidator = new ClientRegistrationSettingsValidator(applicationService);

        cspSettingsValidator = Mockito.mock(CspSettingsValidator.class);
        domainValidator = new DomainValidatorImpl(pathValidator, new VirtualHostValidatorImpl(pathValidator), clientRegistrationSettingsValidator, cspSettingsValidator);
    }

    @Test
    public void validate() {
        Domain domain = getValidDomain();
        domainValidator.validate(domain, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_invalidEmptyPath() {
        Domain domain = getValidDomain();
        domain.setPath("");

        // Empty path is replaced with '/' but '/' is not allowed in context-path mode.
        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_invalidNullPath() {
        Domain domain = getValidDomain();
        domain.setPath(null);

        // Null path is replaced with '/' but '/' is not allowed in context-path mode.
        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_multipleSlashesPath() {
        Domain domain = getValidDomain();
        domain.setPath("/////test////");

        // Multiple '/' in path should be removed.
        domainValidator.validate(domain, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_notStartingSlashPath() {
        Domain domain = getValidDomain();
        domain.setPath("test");

        // '/' should be automatically append.
        domainValidator.validate(domain, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_invalidName() {
        Domain domain = getValidDomain();
        domain.setName("Invalid/Name");

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_vhostModeRequired() {
        Domain domain = getValidDomain();
        domain.setVhostMode(false);

        domainValidator.validate(domain, singletonList("constraint.gravitee.io")).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_invalidEmptyVhosts() {
        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.setVhosts(emptyList());

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_invalidNullVhosts() {
        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.setVhosts(null);

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_invalidMultipleVhostFlaggedWithOverrideEntrypoint() {
        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        VirtualHost vhost = new VirtualHost();
        vhost.setHost("valid.host.gravitee.io");
        vhost.setPath("/validVhostPath");
        vhost.setOverrideEntrypoint(true);
        domain.getVhosts().add(vhost);

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }


    @Test
    public void validate_invalidNoVhostFlaggedWithOverrideEntrypoint() {
        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.getVhosts().get(0).setOverrideEntrypoint(false);

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_vhosts() {
        Domain domain = getValidDomain();
        domain.setVhostMode(true);

        domainValidator.validate(domain, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_certificateBasedAuthEnabledMissingUrl() {
        Domain domain = getValidDomain();
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setCertificateBasedAuthEnabled(true);
        domain.setLoginSettings(loginSettings);

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_certificateBasedAuthEnabledWithInvalidScheme() {
        Domain domain = getValidDomain();
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setCertificateBasedAuthEnabled(true);
        loginSettings.setCertificateBasedAuthUrl("http://cba.example.com");
        domain.setLoginSettings(loginSettings);

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_certificateBasedAuthEnabledWithHttpsUrl() {
        Domain domain = getValidDomain();
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setCertificateBasedAuthEnabled(true);
        loginSettings.setCertificateBasedAuthUrl("https://cba.example.com");
        domain.setLoginSettings(loginSettings);

        domainValidator.validate(domain, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_noCspSettings_doesNotCallCspValidator() {
        domainValidator.validate(getValidDomain(), emptyList()).test().assertNoErrors();

        verify(cspSettingsValidator, never()).validate(any());
    }

    @Test
    public void validate_nullCspSettings_doesNotCallCspValidator() {
        Domain domain = getValidDomain();
        domain.setWebProtectionSettings(new WebProtectionSettings());

        domainValidator.validate(domain, emptyList()).test().assertNoErrors();

        verify(cspSettingsValidator, never()).validate(any());
    }

    @Test
    public void validate_cspSettings_callsCspValidator() {
        CspSettings cspSettings = new CspSettings();
        Domain domain = domainWithCsp(cspSettings);
        when(cspSettingsValidator.validate(cspSettings)).thenReturn(Completable.complete());

        domainValidator.validate(domain, emptyList()).test().assertNoErrors();

        verify(cspSettingsValidator).validate(cspSettings);
    }

    @Test
    public void validate_cspSettingsError_surfacesValidatorMessage() {
        assertCspErrorPropagates(new InvalidDomainException("duplicate directive"), InvalidDomainException.class, "duplicate directive");
    }

    @Test
    public void validate_cspSettingsUnexpectedError_isNotDisguisedAsAValidationFailure() {
        // An unexpected validator fault is a bug, not bad input, so it must not become a 400.
        assertCspErrorPropagates(new IllegalStateException("validator failed"), IllegalStateException.class, "validator failed");
    }

    private void assertCspErrorPropagates(Throwable validatorError, Class<? extends Throwable> expectedType, String expectedMessage) {
        CspSettings cspSettings = new CspSettings();
        Domain domain = domainWithCsp(cspSettings);
        when(cspSettingsValidator.validate(cspSettings)).thenReturn(Completable.error(validatorError));

        domainValidator.validate(domain, emptyList()).test()
                .assertError(expectedType)
                .assertError(throwable -> expectedMessage.equals(throwable.getMessage()));

        verify(cspSettingsValidator).validate(cspSettings);
    }

    private Domain domainWithCsp(CspSettings cspSettings) {
        Domain domain = getValidDomain();
        WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setCsp(cspSettings);
        domain.setWebProtectionSettings(webProtectionSettings);
        return domain;
    }

    private Domain getValidDomain() {
        Domain domain = new Domain();
        domain.setId("id");
        domain.setName("Domain Test");
        domain.setPath("/validPath");
        domain.setVhostMode(false);

        ArrayList<VirtualHost> vhosts = new ArrayList<>();
        VirtualHost vhost = new VirtualHost();
        vhost.setHost("valid.host.gravitee.io");
        vhost.setPath("/validVhostPath");
        vhost.setOverrideEntrypoint(true);
        vhosts.add(vhost);

        domain.setVhosts(vhosts);

        return domain;
    }
}
