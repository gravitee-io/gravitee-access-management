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
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Resolves an {@link AutomationRef} to a concrete resource.
 *
 * @author GraviteeSource Team
 */
public class AutomationResourceResolver {

    private final DomainService domainService;
    private final IdentityProviderService identityProviderService;
    private final CertificateService certificateService;
    private final ReporterService reporterService;

    public AutomationResourceResolver(DomainService domainService,
            IdentityProviderService identityProviderService,
            CertificateService certificateService,
            ReporterService reporterService) {
        this.domainService = domainService;
        this.identityProviderService = identityProviderService;
        this.certificateService = certificateService;
        this.reporterService = reporterService;
    }

    // --- domains ------------------------------------------------------------

    public Single<Domain> resolveDomain(String environmentId, AutomationRef ref) {
        return resolveDomainMaybe(environmentId, ref)
                .switchIfEmpty(Single.error(() -> new DomainNotFoundException(ref.raw())));
    }

    public Maybe<Domain> resolveDomainMaybe(String environmentId, AutomationRef ref) {
        return switch (ref) {
            case AutomationRef.IdRef(String id) -> domainService.findById(id)
                    .filter(domain -> environmentId != null && environmentId.equals(domain.getReferenceId()));
            case AutomationRef.KeyRef(String key) -> domainService.findById(AutomationIds.domainId(environmentId, key))
                    .filter(domain -> domain.isManagedBy(ManagedBy.AUTOMATION_API));
        };
    }

    // --- identity providers -------------------------------------------------

    public Single<IdentityProvider> resolveIdentityProvider(Domain domain, AutomationRef ref) {
        return resolveIdentityProviderMaybe(domain, ref)
                .switchIfEmpty(Maybe.error(() -> new IdentityProviderNotFoundException(ref.raw())))
                .toSingle();
    }

    public Maybe<IdentityProvider> resolveIdentityProviderMaybe(Domain domain, AutomationRef ref) {
        return switch (ref) {
            case AutomationRef.IdRef(String id) -> identityProviderService.findById(id)
                    .filter(idp -> ReferenceType.DOMAIN.equals(idp.getReferenceType())
                            && domain.getId().equals(idp.getReferenceId()));
            case AutomationRef.KeyRef(String key) -> identityProviderService.findAll(ReferenceType.DOMAIN, domain.getId())
                    .filter(idp -> idp.isManagedBy(ManagedBy.AUTOMATION_API) && key.equals(idp.getAutomationKey()))
                    .firstElement();
        };
    }

    // --- certificates -------------------------------------------------------

    public Single<Certificate> resolveCertificate(Domain domain, AutomationRef ref) {
        return resolveCertificateMaybe(domain, ref)
                .switchIfEmpty(Maybe.error(() -> new CertificateNotFoundException(ref.raw())))
                .toSingle();
    }

    public Maybe<Certificate> resolveCertificateMaybe(Domain domain, AutomationRef ref) {
        return switch (ref) {
            case AutomationRef.IdRef(String id) -> certificateService.findById(id)
                    .filter(certificate -> domain.getId().equals(certificate.getDomain()));
            case AutomationRef.KeyRef(String key) -> certificateService.findByDomain(domain.getId())
                    .filter(certificate -> certificate.isManagedBy(ManagedBy.AUTOMATION_API)
                            && key.equals(certificate.getAutomationKey()))
                    .firstElement();
        };
    }

    // --- reporters ----------------------------------------------------------

    public Single<Reporter> resolveReporter(Domain domain, AutomationRef ref) {
        return resolveReporterMaybe(domain, ref)
                .switchIfEmpty(Maybe.error(() -> new ReporterNotFoundException(ref.raw())))
                .toSingle();
    }

    public Maybe<Reporter> resolveReporterMaybe(Domain domain, AutomationRef ref) {
        return switch (ref) {
            case AutomationRef.IdRef(String id) -> reporterService.findById(id)
                    .filter(reporter -> Reference.domain(domain.getId()).equals(reporter.getReference()));
            case AutomationRef.KeyRef(String key) -> reporterService.findByReference(Reference.domain(domain.getId()))
                    .filter(reporter -> reporter.isManagedBy(ManagedBy.AUTOMATION_API)
                            && key.equals(reporter.getAutomationKey()))
                    .firstElement();
        };
    }
}
