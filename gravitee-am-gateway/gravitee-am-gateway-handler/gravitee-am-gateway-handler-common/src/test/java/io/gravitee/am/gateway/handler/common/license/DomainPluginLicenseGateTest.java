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
import io.gravitee.am.model.Reference;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.exception.LicenseFeatureRequiredException;
import io.reactivex.rxjava3.core.Completable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DomainPluginLicenseGateTest {

    private static final String DOMAIN_ID = "domain#1";

    @Mock
    private Domain domain;

    @Mock
    private PluginLicenseGate pluginLicenseGate;

    @Mock
    private DomainReadinessService domainReadinessService;

    @InjectMocks
    private DomainPluginLicenseGate domainPluginLicenseGate;

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
    }

    @Test
    public void shouldAllowLicensedPlugin() {
        when(pluginLicenseGate.check(Reference.domain(DOMAIN_ID), PluginLicenseGate.TYPE_FACTOR, "otp-sender"))
                .thenReturn(Completable.complete());

        assertTrue(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_FACTOR, "otp-sender", "factor-instance-1"));

        verifyNoInteractions(domainReadinessService);
    }

    @Test
    public void shouldSkipAndRecordUnlicensedPlugin() {
        when(pluginLicenseGate.check(Reference.domain(DOMAIN_ID), PluginLicenseGate.TYPE_FACTOR, "otp-sender"))
                .thenReturn(Completable.error(new LicenseFeatureRequiredException("am-factor-otp-sender", "otp-sender")));

        assertFalse(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_FACTOR, "otp-sender", "factor-instance-1"));

        verify(domainReadinessService).pluginUnlicensed(eq(DOMAIN_ID), eq("factor-instance-1"), anyString());
    }

    @Test
    public void shouldFailOpenOnUnexpectedErrors() {
        when(pluginLicenseGate.check(Reference.domain(DOMAIN_ID), PluginLicenseGate.TYPE_FACTOR, "otp-sender"))
                .thenReturn(Completable.error(new IllegalStateException("cannot resolve organization")));

        assertTrue(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_FACTOR, "otp-sender", "factor-instance-1"));

        verifyNoInteractions(domainReadinessService);
    }
}
