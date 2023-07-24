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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.management.service.FactorPluginService;
import io.gravitee.am.management.service.impl.plugins.FactorPluginServiceImpl;
import io.gravitee.am.plugins.factor.core.FactorPluginManager;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class FactorPluginServiceTest {

    @Mock
    private FactorPluginManager factorPluginManager;
    private FactorPluginService factorPluginService;

    @BeforeEach
    public void setUp() {
        factorPluginService = new FactorPluginServiceImpl(factorPluginManager);
    }

    @Test
    public void must_accept_deployed_plugins() throws IOException {
        String pluginId = "pluginId";
        when(factorPluginManager.isPluginDeployed(pluginId)).thenReturn(true);

        var observer = factorPluginService.checkPluginDeployment(pluginId).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    public void must_reject_not_deployed_plugins() throws IOException {
        String pluginId = "pluginId";

        when(factorPluginManager.isPluginDeployed(pluginId)).thenReturn(false);

        var observer = factorPluginService.checkPluginDeployment(pluginId).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(PluginNotDeployedException.class);
    }
}