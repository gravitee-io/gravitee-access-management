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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class AutomationResourceResolverTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String OTHER_DOMAIN_ID = "domain-2";
    private static final String RESOURCE_ID = "33333333-4444-5555-6666-777777777777";

    private static final String ENV_ID = "env-1";
    private static final String OTHER_ENV_ID = "env-2";

    private DomainService domainService;
    private IdentityProviderService identityProviderService;
    private CertificateService certificateService;
    private ReporterService reporterService;
    private AutomationResourceResolver resolver;

    private Domain domain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        return domain;
    }

    @BeforeEach
    void setUp() {
        domainService = mock(DomainService.class);
        identityProviderService = mock(IdentityProviderService.class);
        certificateService = mock(CertificateService.class);
        reporterService = mock(ReporterService.class);
        resolver = new AutomationResourceResolver(domainService, identityProviderService, certificateService, reporterService);
    }

    // --- domains (scoped by environment) ------------------------------------

    @Test
    void resolveDomain_byId_returns_brownfield_domain_in_environment() {
        Domain domain = domain();
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.NONE);
        when(domainService.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));

        resolver.resolveDomain(ENV_ID, AutomationRef.parse("id:" + DOMAIN_ID))
                .test()
                .assertValue(d -> d.getId().equals(DOMAIN_ID))
                .assertNoErrors();
    }

    @Test
    void resolveDomain_byId_is_not_found_when_in_another_environment() {
        Domain domain = domain();
        domain.setReferenceId(OTHER_ENV_ID);
        when(domainService.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));

        resolver.resolveDomain(ENV_ID, AutomationRef.parse("id:" + DOMAIN_ID))
                .test()
                .assertError(DomainNotFoundException.class);
    }

    @Test
    void resolveDomain_byKey_requires_automation_managed() {
        Domain brownfield = domain();
        brownfield.setReferenceId(ENV_ID);
        brownfield.setManagedBy(ManagedBy.NONE);
        when(domainService.findById(anyString())).thenReturn(Maybe.just(brownfield));

        resolver.resolveDomain(ENV_ID, AutomationRef.parse("example-domain"))
                .test()
                .assertError(DomainNotFoundException.class);
    }

    // --- identity providers (scoped by parent domain) -----------------------

    @Test
    void resolveIdentityProvider_byId_returns_brownfield_idp_in_scope() {
        IdentityProvider idp = new IdentityProvider();
        idp.setId(RESOURCE_ID);
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId(DOMAIN_ID);
        idp.setManagedBy(ManagedBy.NONE);
        when(identityProviderService.findById(RESOURCE_ID)).thenReturn(Maybe.just(idp));

        resolver.resolveIdentityProvider(domain(), AutomationRef.parse("id:" + RESOURCE_ID))
                .test()
                .assertValue(i -> i.getId().equals(RESOURCE_ID))
                .assertNoErrors();
    }

    @Test
    void resolveIdentityProvider_byId_is_not_found_when_in_another_domain() {
        IdentityProvider idp = new IdentityProvider();
        idp.setId(RESOURCE_ID);
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId(OTHER_DOMAIN_ID);
        when(identityProviderService.findById(RESOURCE_ID)).thenReturn(Maybe.just(idp));

        resolver.resolveIdentityProvider(domain(), AutomationRef.parse("id:" + RESOURCE_ID))
                .test()
                .assertError(IdentityProviderNotFoundException.class);
    }

    @Test
    void resolveIdentityProvider_byKey_requires_automation_managed() {
        IdentityProvider brownfield = new IdentityProvider();
        brownfield.setId(RESOURCE_ID);
        brownfield.setAutomationKey("corporate-ldap");
        brownfield.setManagedBy(ManagedBy.NONE);
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID))).thenReturn(Flowable.just(brownfield));

        resolver.resolveIdentityProvider(domain(), AutomationRef.parse("corporate-ldap"))
                .test()
                .assertError(IdentityProviderNotFoundException.class);
    }

    @Test
    void resolveIdentityProvider_byId_is_not_found_when_absent() {
        when(identityProviderService.findById(RESOURCE_ID)).thenReturn(Maybe.empty());

        resolver.resolveIdentityProvider(domain(), AutomationRef.parse("id:" + RESOURCE_ID))
                .test()
                .assertError(IdentityProviderNotFoundException.class);
    }

    @Test
    void resolveCertificate_byId_returns_brownfield_certificate_in_scope() {
        Certificate certificate = new Certificate();
        certificate.setId(RESOURCE_ID);
        certificate.setDomain(DOMAIN_ID);
        certificate.setManagedBy(ManagedBy.NONE);
        when(certificateService.findById(RESOURCE_ID)).thenReturn(Maybe.just(certificate));

        resolver.resolveCertificate(domain(), AutomationRef.parse("id:" + RESOURCE_ID))
                .test()
                .assertValue(c -> c.getId().equals(RESOURCE_ID))
                .assertNoErrors();
    }

    @Test
    void resolveCertificate_byId_is_not_found_when_in_another_domain() {
        Certificate certificate = new Certificate();
        certificate.setId(RESOURCE_ID);
        certificate.setDomain(OTHER_DOMAIN_ID);
        when(certificateService.findById(RESOURCE_ID)).thenReturn(Maybe.just(certificate));

        resolver.resolveCertificate(domain(), AutomationRef.parse("id:" + RESOURCE_ID))
                .test()
                .assertError(CertificateNotFoundException.class);
    }

    @Test
    void resolveCertificate_byKey_requires_automation_managed() {
        Certificate brownfield = new Certificate();
        brownfield.setId(RESOURCE_ID);
        brownfield.setDomain(DOMAIN_ID);
        brownfield.setAutomationKey("signing-cert");
        brownfield.setManagedBy(ManagedBy.NONE);
        when(certificateService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(brownfield));

        // a key-addressed lookup only sees AUTOMATION_API resources, so the brownfield certificate is hidden
        resolver.resolveCertificate(domain(), AutomationRef.parse("signing-cert"))
                .test()
                .assertError(CertificateNotFoundException.class);
    }

    @Test
    void resolveReporter_byId_is_not_found_when_in_another_domain() {
        Reporter reporter = new Reporter();
        reporter.setId(RESOURCE_ID);
        reporter.setReference(Reference.domain(OTHER_DOMAIN_ID));
        when(reporterService.findById(RESOURCE_ID)).thenReturn(Maybe.just(reporter));

        resolver.resolveReporter(domain(), AutomationRef.parse("id:" + RESOURCE_ID))
                .test()
                .assertError(ReporterNotFoundException.class);
    }

    @Test
    void resolveReporter_byId_returns_brownfield_reporter_in_scope() {
        Reporter reporter = new Reporter();
        reporter.setId(RESOURCE_ID);
        reporter.setReference(Reference.domain(DOMAIN_ID));
        reporter.setManagedBy(ManagedBy.NONE);
        when(reporterService.findById(RESOURCE_ID)).thenReturn(Maybe.just(reporter));

        resolver.resolveReporter(domain(), AutomationRef.parse("id:" + RESOURCE_ID))
                .test()
                .assertValue(r -> r.getId().equals(RESOURCE_ID))
                .assertNoErrors();
    }
}
