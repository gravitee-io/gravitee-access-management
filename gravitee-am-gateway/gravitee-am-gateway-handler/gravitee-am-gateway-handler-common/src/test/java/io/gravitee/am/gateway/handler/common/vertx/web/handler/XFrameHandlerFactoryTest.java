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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.xframe.NoXFrameHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.xframe.XFrameHandlerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.webprotection.WebProtectionSettings;
import io.gravitee.am.model.webprotection.XFrameSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class XFrameHandlerFactoryTest {

    @InjectMocks
    private XFrameHandlerFactory factory = new XFrameHandlerFactory();

    @Mock
    private Domain domain;

    @Mock
    private Environment environment;

    @Test
    public void shouldUseDomainSettingsWhenEnabled() {
        final XFrameSettings xframeSettings = new XFrameSettings();
        xframeSettings.setInherited(false);
        xframeSettings.setEnabled(true);
        xframeSettings.setAction("SAMEORIGIN");

        final WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setXframe(xframeSettings);

        when(domain.getWebProtectionSettings()).thenReturn(webProtectionSettings);

        assertTrue(factory.getObject() instanceof XFrameHandlerImpl);
    }

    @Test
    public void shouldFallbackToEnvironmentWhenInherited() {
        final XFrameSettings xframeSettings = new XFrameSettings();
        xframeSettings.setInherited(true);

        final WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setXframe(xframeSettings);

        when(domain.getWebProtectionSettings()).thenReturn(webProtectionSettings);
        when(environment.getProperty("http.xframe.action", String.class, "DENY")).thenReturn("DENY");

        assertTrue(factory.getObject() instanceof XFrameHandlerImpl);
    }

    @Test
    public void shouldDisableWhenExplicitlyDisabledForDomain() {
        final XFrameSettings xframeSettings = new XFrameSettings();
        xframeSettings.setInherited(false);
        xframeSettings.setEnabled(false);

        final WebProtectionSettings webProtectionSettings = new WebProtectionSettings();
        webProtectionSettings.setXframe(xframeSettings);

        when(domain.getWebProtectionSettings()).thenReturn(webProtectionSettings);

        assertTrue(factory.getObject() instanceof NoXFrameHandler);
    }
}
