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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.csp.CspHandlerImpl;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.csp.NoOpCspHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.webprotection.CspSettings;
import io.gravitee.am.model.webprotection.WebProtectionSettings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CSPHandlerFactoryTest {

    @InjectMocks
    private CSPHandlerFactory factory = new CSPHandlerFactory();

    @Mock
    private Domain domain;

    @Mock
    private Environment environment;

    @Before
    public void setUp() {
        when(environment.getProperty("http.csp.enabled", Boolean.class, true)).thenReturn(true);
        when(environment.getProperty("http.csp.script-inline-nonce", Boolean.class, true)).thenReturn(true);
    }

    @Test
    public void shouldUseDomainSettingsWhenEnabled() {
        final CspSettings cspSettings = new CspSettings();
        cspSettings.setInherited(false);
        cspSettings.setEnabled(true);
        cspSettings.setDirectives(List.of("default-src 'self';"));

        final WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setCsp(cspSettings);

        when(domain.getWebProtectionSettings()).thenReturn(webProtectionSettings);

        assertTrue(factory.getObject() instanceof CspHandlerImpl);
    }

    @Test
    public void shouldFallbackToEnvironmentWhenInherited() {
        final CspSettings cspSettings = new CspSettings();
        cspSettings.setInherited(true);

        final WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setCsp(cspSettings);

        when(domain.getWebProtectionSettings()).thenReturn(webProtectionSettings);
        when(environment.getProperty("http.csp.reportOnly", Boolean.class)).thenReturn(false);

        assertTrue(factory.getObject() instanceof CspHandlerImpl);
    }

    @Test
    public void shouldDisableWhenExplicitlyDisabledForDomain() {
        final CspSettings cspSettings = new CspSettings();
        cspSettings.setInherited(false);
        cspSettings.setEnabled(false);

        final WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setCsp(cspSettings);

        when(domain.getWebProtectionSettings()).thenReturn(webProtectionSettings);

        assertTrue(factory.getObject() instanceof NoOpCspHandler);
    }

    @Test
    public void shouldFallbackToEnvironmentWhenDomainSettingsMissing() {
        when(domain.getWebProtectionSettings()).thenReturn(null);
        when(environment.getProperty("http.csp.reportOnly", Boolean.class)).thenReturn(false);

        assertTrue(factory.getObject() instanceof CspHandlerImpl);
    }
}
