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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.LicenseService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.LicenseFeatureRequiredException;
import io.gravitee.node.api.license.License;
import io.gravitee.node.api.license.LicenseFactory;
import io.gravitee.node.api.license.LicenseManager;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginManifest;
import io.gravitee.plugin.core.api.PluginRegistry;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class PluginLicenseGateImplTest {

    private static final String ORG_ID = "org-1";
    private static final String ENV_ID = "env-1";
    private static final String DOMAIN_ID = "domain-1";
    private static final String PLUGIN_TYPE = PluginLicenseGate.TYPE_IDENTITY_PROVIDER;
    private static final String PLUGIN_ID = "http-am-idp";
    private static final String FEATURE = "am-idp-http";

    @Mock
    private PluginRegistry pluginRegistry;

    @Mock
    private LicenseManager licenseManager;

    @Mock
    private LicenseService licenseService;

    @Mock
    private LicenseFactory licenseFactory;

    @Mock
    private DomainReadService domainReadService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private Plugin plugin;

    @Mock
    private PluginManifest manifest;

    @Mock
    private License license;

    private PluginLicenseGateImpl gate;

    @BeforeEach
    void setUp() {
        gate = cloudGate();
        lenient().when(plugin.manifest()).thenReturn(manifest);
        lenient().when(pluginRegistry.get(PLUGIN_TYPE, PLUGIN_ID)).thenReturn(plugin);
        lenient().when(licenseManager.getOrganizationLicense(ORG_ID)).thenReturn(license);
    }

    private PluginLicenseGateImpl cloudGate() {
        return new PluginLicenseGateImpl(pluginRegistry, licenseManager, licenseService, licenseFactory, domainReadService, environmentService,
                new MockEnvironment().withProperty("cloud.enabled", "true").withProperty("installation.type", "managed"));
    }

    @Test
    void shouldAllowEverythingWhenCloudIsDisabled() {
        final PluginLicenseGateImpl selfHostedGate = new PluginLicenseGateImpl(
                pluginRegistry, licenseManager, licenseService, licenseFactory, domainReadService, environmentService, new MockEnvironment());

        selfHostedGate.check(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertComplete();
        verifyNoInteractions(pluginRegistry, licenseManager);
    }

    @Test
    void shouldAllowPluginWithoutFeature() {
        when(manifest.feature()).thenReturn(null);

        gate.check(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertComplete();
        verify(licenseManager, never()).getOrganizationLicense(ORG_ID);
    }

    @Test
    void shouldAllowUnknownPlugin() {
        when(pluginRegistry.get(PLUGIN_TYPE, "unknown-plugin")).thenReturn(null);

        gate.check(Reference.organization(ORG_ID), PLUGIN_TYPE, "unknown-plugin")
                .test()
                .assertComplete();
    }

    @Test
    void shouldAllowFeatureGrantedByOrganizationLicense() {
        when(manifest.feature()).thenReturn(FEATURE);
        when(license.isFeatureEnabled(FEATURE)).thenReturn(true);

        gate.check(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertComplete();
    }

    @Test
    void shouldBlockWhenFeatureNotGranted() {
        when(manifest.feature()).thenReturn(FEATURE);
        when(license.isFeatureEnabled(FEATURE)).thenReturn(false);

        gate.check(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertError(throwable -> throwable instanceof LicenseFeatureRequiredException lfre
                        && FEATURE.equals(lfre.getFeature())
                        && lfre.getHttpStatusCode() == 403);
    }

    @Test
    void shouldBlockEEPluginWhenOrganizationHasNoLicense() {
        when(manifest.feature()).thenReturn(FEATURE);
        when(licenseManager.getOrganizationLicense(ORG_ID)).thenReturn(null);

        gate.check(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertError(LicenseFeatureRequiredException.class);
    }

    @Test
    void shouldResolveOrganizationFromDomainAndCacheIt() {
        when(manifest.feature()).thenReturn(FEATURE);
        when(license.isFeatureEnabled(FEATURE)).thenReturn(true);
        when(domainReadService.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain()));
        when(environmentService.findById(ENV_ID)).thenReturn(Single.just(environment()));

        gate.check(Reference.domain(DOMAIN_ID), PLUGIN_TYPE, PLUGIN_ID).test().assertComplete();
        gate.check(Reference.domain(DOMAIN_ID), PLUGIN_TYPE, PLUGIN_ID).test().assertComplete();

        // second call is served from the domain -> organization cache
        verify(domainReadService, times(1)).findById(DOMAIN_ID);
        verify(environmentService, times(1)).findById(ENV_ID);
    }

    @Test
    void shouldResolveOrganizationFromEnvironment() {
        when(manifest.feature()).thenReturn(FEATURE);
        when(license.isFeatureEnabled(FEATURE)).thenReturn(true);
        when(environmentService.findById(ENV_ID)).thenReturn(Single.just(environment()));

        gate.check(Reference.environment(ENV_ID), PLUGIN_TYPE, PLUGIN_ID).test().assertComplete();
    }

    @Test
    void shouldFailWhenDomainNotFound() {
        when(manifest.feature()).thenReturn(FEATURE);
        when(domainReadService.findById(DOMAIN_ID)).thenReturn(Maybe.empty());

        gate.check(Reference.domain(DOMAIN_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertError(DomainNotFoundException.class);
    }

    private static Domain domain() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENV_ID);
        return domain;
    }

    private static Environment environment() {
        final Environment environment = new Environment();
        environment.setId(ENV_ID);
        environment.setOrganizationId(ORG_ID);
        return environment;
    }

    @Test
    void checkPersisted_allowsEverythingWhenCloudIsDisabled() {
        final PluginLicenseGateImpl selfHostedGate = new PluginLicenseGateImpl(
                pluginRegistry, licenseManager, licenseService, licenseFactory, domainReadService, environmentService, new MockEnvironment());

        selfHostedGate.checkPersisted(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertComplete();
        verifyNoInteractions(licenseService, licenseFactory);
    }

    @Test
    void checkPersisted_allowsPluginWithoutFeature() {
        when(manifest.feature()).thenReturn(null);

        gate.checkPersisted(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertComplete();
        verifyNoInteractions(licenseService);
    }

    @Test
    void checkPersisted_allowsFeatureGrantedByPersistedLicense() throws Exception {
        when(manifest.feature()).thenReturn(FEATURE);
        when(licenseService.findByReference(ReferenceType.ORGANIZATION, ORG_ID)).thenReturn(Maybe.just(persistedLicense()));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORG_ID, "raw-license")).thenReturn(license);
        when(license.isFeatureEnabled(FEATURE)).thenReturn(true);

        gate.checkPersisted(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertComplete();
    }

    @Test
    void checkPersisted_blocksFeatureMissingFromPersistedLicense() throws Exception {
        when(manifest.feature()).thenReturn(FEATURE);
        when(licenseService.findByReference(ReferenceType.ORGANIZATION, ORG_ID)).thenReturn(Maybe.just(persistedLicense()));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORG_ID, "raw-license")).thenReturn(license);
        when(license.isFeatureEnabled(FEATURE)).thenReturn(false);

        gate.checkPersisted(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertError(LicenseFeatureRequiredException.class);
    }

    @Test
    void checkPersisted_blocksEEPluginWhenOrganizationHasNoPersistedLicense() {
        when(manifest.feature()).thenReturn(FEATURE);
        when(licenseService.findByReference(ReferenceType.ORGANIZATION, ORG_ID)).thenReturn(Maybe.empty());

        gate.checkPersisted(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertError(LicenseFeatureRequiredException.class);
    }

    @Test
    void checkPersisted_blocksEEPluginWhenPersistedLicenseCannotBeRead() throws Exception {
        when(manifest.feature()).thenReturn(FEATURE);
        when(licenseService.findByReference(ReferenceType.ORGANIZATION, ORG_ID)).thenReturn(Maybe.just(persistedLicense()));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORG_ID, "raw-license")).thenThrow(new RuntimeException("corrupted"));

        gate.checkPersisted(Reference.organization(ORG_ID), PLUGIN_TYPE, PLUGIN_ID)
                .test()
                .assertError(LicenseFeatureRequiredException.class);
    }

    private static io.gravitee.am.model.License persistedLicense() {
        final io.gravitee.am.model.License license = new io.gravitee.am.model.License();
        license.setReferenceType(ReferenceType.ORGANIZATION);
        license.setReferenceId(ORG_ID);
        license.setLicense("raw-license");
        return license;
    }

}
