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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.xss.NoXSSHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.xss.XSSHandlerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.webprotection.WebProtectionSettings;
import io.gravitee.am.model.webprotection.XssProtectionSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class XSSHandlerFactoryTest {

    @InjectMocks
    private XSSHandlerFactory factory = new XSSHandlerFactory();

    @Mock
    private Domain domain;

    @Mock
    private Environment environment;

    @Test
    public void shouldUseDomainSettingsWhenEnabled() {
        final XssProtectionSettings xssSettings = new XssProtectionSettings();
        xssSettings.setInherited(false);
        xssSettings.setEnabled(true);
        xssSettings.setAction("1; mode=block");

        final WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setXss(xssSettings);

        when(domain.getWebProtectionSettings()).thenReturn(webProtectionSettings);

        assertTrue(factory.getObject() instanceof XSSHandlerImpl);
    }

    @Test
    public void shouldFallbackToEnvironmentWhenInherited() {
        final XssProtectionSettings xssSettings = new XssProtectionSettings();
        xssSettings.setInherited(true);

        final WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setXss(xssSettings);

        when(domain.getWebProtectionSettings()).thenReturn(webProtectionSettings);
        when(environment.getProperty("http.xss.action", String.class, "1; mode=block")).thenReturn("1; mode=block");
        when(environment.getProperty("http.xss.enabled", Boolean.class, true)).thenReturn(true);

        assertTrue(factory.getObject() instanceof XSSHandlerImpl);
    }

    @Test
    public void shouldDisableWhenExplicitlyDisabledForDomain() {
        final XssProtectionSettings xssSettings = new XssProtectionSettings();
        xssSettings.setInherited(false);
        xssSettings.setEnabled(false);

        final WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setXss(xssSettings);

        when(domain.getWebProtectionSettings()).thenReturn(webProtectionSettings);

        assertTrue(factory.getObject() instanceof NoXSSHandler);
    }
}
