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

package io.gravitee.am.deviceidentifier.cookie.impl;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.deviceidentifier.api.DeviceIdentifierProvider;
import io.gravitee.am.deviceidentifier.cookie.CookieDeviceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static java.util.Objects.nonNull;

/**
 * @author GraviteeSource Team
 */
public class CookieDeviceIdentifierProvider implements DeviceIdentifierProvider {

    private final Logger LOGGER = LoggerFactory.getLogger(CookieDeviceIdentifierProvider.class);

    @Override
    public void addConfigurationVariables(Map<String, Object> variables, String configuration) {
        LOGGER.debug("CookieDeviceIdentifierProvider.addConfigurationVariables");
        if (nonNull(variables)) {
            variables.put(ConstantKeys.DEVICE_IDENTIFIER_PROVIDER_KEY, CookieDeviceIdentifier.class.getSimpleName());
            variables.put("cookieDeviceIdentifier", UUID.randomUUID().toString());
        }
    }

    @Override
    public boolean useCookieToKeepIdentifier() {
        return true;
    }
}
