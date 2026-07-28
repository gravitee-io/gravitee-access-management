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
package io.gravitee.am.gateway.handler.common.license;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Reference;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.exception.LicenseFeatureRequiredException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DomainPluginLicenseGateTest {

    private static final String DOMAIN_ID = "domain#1";
    private static final String ENVIRONMENT_ID = "env#1";
    private static final String ORGANIZATION_ID = "org#1";

    @Mock
    private Domain domain;

    @Mock
    private PluginLicenseGate pluginLicenseGate;

    @Mock
    private DomainReadinessService domainReadinessService;

    @Mock
    private EnvironmentService environmentService;

    @InjectMocks
    private DomainPluginLicenseGate domainPluginLicenseGate;

    @Before
    public void setUp() {
        // used only by the paths that log / record readiness against the domain
        lenient().when(domain.getId()).thenReturn(DOMAIN_ID);
    }

    private void initWithResolvedOrganization() {
        Environment environment = new Environment();
        environment.setId(ENVIRONMENT_ID);
        environment.setOrganizationId(ORGANIZATION_ID);
        when(domain.getReferenceId()).thenReturn(ENVIRONMENT_ID);
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(environment));
        domainPluginLicenseGate.afterPropertiesSet();
    }

    @Test
    public void shouldAllowLicensedPlugin() {
        initWithResolvedOrganization();
        when(pluginLicenseGate.check(Reference.organization(ORGANIZATION_ID), PluginLicenseGate.TYPE_FACTOR, "otp-sender"))
                .thenReturn(Completable.complete());

        assertTrue(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_FACTOR, "otp-sender", "factor-instance-1"));

        verifyNoInteractions(domainReadinessService);
    }

    @Test
    public void shouldSkipAndRecordUnlicensedPlugin() {
        initWithResolvedOrganization();
        when(pluginLicenseGate.check(Reference.organization(ORGANIZATION_ID), PluginLicenseGate.TYPE_FACTOR, "otp-sender"))
                .thenReturn(Completable.error(new LicenseFeatureRequiredException("am-factor-otp-sender", "otp-sender")));

        assertFalse(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_FACTOR, "otp-sender", "factor-instance-1"));

        verify(domainReadinessService).pluginUnlicensed(eq(DOMAIN_ID), eq("factor-instance-1"), anyString());
    }

    @Test
    public void shouldFailOpenOnUnexpectedErrors() {
        initWithResolvedOrganization();
        when(pluginLicenseGate.check(Reference.organization(ORGANIZATION_ID), PluginLicenseGate.TYPE_FACTOR, "otp-sender"))
                .thenReturn(Completable.error(new IllegalStateException("boom")));

        assertTrue(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_FACTOR, "otp-sender", "factor-instance-1"));

        verifyNoInteractions(domainReadinessService);
    }

    @Test
    public void shouldFailOpenAndNotBlockWhenOrganizationUnresolved() {
        // organization resolution fails at init: the gate must never fall back to a blocking lazy lookup
        when(domain.getReferenceId()).thenReturn(ENVIRONMENT_ID);
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.error(new IllegalStateException("db down")));
        domainPluginLicenseGate.afterPropertiesSet();

        assertTrue(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_FACTOR, "otp-sender", "factor-instance-1"));

        verify(pluginLicenseGate, org.mockito.Mockito.never()).check(any(), anyString(), anyString());
        verifyNoInteractions(domainReadinessService);
    }
}
