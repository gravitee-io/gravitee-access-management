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

package io.gravitee.am.deviceidentifier.fingerprintjs.impl;

import io.gravitee.am.deviceidentifier.api.DeviceIdentifierProvider;
import io.gravitee.am.deviceidentifier.fingerprintjs.FingerprintJsV3Community;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_IDENTIFIER_PROVIDER_KEY;
import static java.util.Objects.nonNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FingerprintJsV3CommunityProvider implements DeviceIdentifierProvider {

    private final Logger LOGGER = LoggerFactory.getLogger(FingerprintJsV3CommunityProvider.class);

    @Override
    public void addConfigurationVariables(Map<String, Object> variables, String configuration) {
        LOGGER.debug("fingerprintJsV3CommunityProvider.addConfigurationVariables");
        if (nonNull(variables)) {
            variables.put(DEVICE_IDENTIFIER_PROVIDER_KEY, FingerprintJsV3Community.class.getSimpleName());
        }
    }
}
