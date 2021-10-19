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

package io.gravitee.am.deviceidentifier.fingerprintjs;

import io.gravitee.am.deviceidentifier.api.DeviceIdentifierProvider;
import io.gravitee.am.deviceidentifier.fingerprintjs.impl.FingerprintJsV3CommunityProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.deviceidentifier.api.DeviceIdentifierProvider.DEVICE_IDENTIFIER_PROVIDER_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class FingerprintJsV3CommunityProviderTest {

    public DeviceIdentifierProvider provider = new FingerprintJsV3CommunityProvider();

    @Test
    public void mustNotAddAnythingToConfiguration_nullMap() {
        final Map<String, Object> variables = null;
        provider.addConfigurationVariables(variables, null);
        assertNull(variables);
    }

    @Test
    public void mustNotAddAnythingToConfiguration_nullConfiguration() {
        final Map<String, Object> variables = new HashMap<>();
        provider.addConfigurationVariables(variables, null);
        assertEquals(variables.get(DEVICE_IDENTIFIER_PROVIDER_KEY), FingerprintJsV3Community.class.getSimpleName());
    }

    @Test
    public void mustNotAddAnythingToConfiguration_nullEmpty() {
        final Map<String, Object> variables = new HashMap<>();
        provider.addConfigurationVariables(variables, "{}");
        assertEquals(variables.get(DEVICE_IDENTIFIER_PROVIDER_KEY), FingerprintJsV3Community.class.getSimpleName());
    }

    @Test
    public void mustAddConfiguration_onlyBrowserToken() {
        final Map<String, Object> variables = new HashMap<>();
        provider.addConfigurationVariables(variables, "{\"browserToken\" : \"myToken\"}");
        assertEquals(variables.get(DEVICE_IDENTIFIER_PROVIDER_KEY), FingerprintJsV3Community.class.getSimpleName());
    }

    @Test
    public void mustNotAddAnythingToConfiguration_fullConfig() {
        final Map<String, Object> variables = new HashMap<>();
        provider.addConfigurationVariables(variables, "{\"browserToken\" : \"myToken\", \"region\" : \"eu\"}");
        assertEquals(variables.get(DEVICE_IDENTIFIER_PROVIDER_KEY), FingerprintJsV3Community.class.getSimpleName());
    }
}