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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.deviceidentifier.api.DeviceIdentifierProvider;
import io.gravitee.am.deviceidentifier.fingerprintjs.FingerprintJsV3Pro;
import io.gravitee.am.deviceidentifier.fingerprintjs.FingerprintJsV3ProConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.nonNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FingerprintJsV3ProProvider implements DeviceIdentifierProvider {

    private static final String FPJS_PRO_BROWSER_TOKEN = "fingerprint_js_v3_pro_browser_token";
    private static final String FPJS_PRO_REGION = "fingerprint_js_v3_pro_region";


    private final Logger LOGGER = LoggerFactory.getLogger(FingerprintJsV3ProProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void addConfigurationVariables(Map<String, Object> variables, String configuration) {
        try {
            LOGGER.debug("fingerprintJsV3ProProvider.addConfigurationVariables");
            if (nonNull(variables) && !isNullOrEmpty(configuration) && !configuration.isBlank()) {
                variables.put(DEVICE_IDENTIFIER_PROVIDER_KEY, FingerprintJsV3Pro.class.getSimpleName());
                var config = objectMapper.readValue(configuration, FingerprintJsV3ProConfiguration.class);
                variables.put(FPJS_PRO_BROWSER_TOKEN, config.getBrowserToken());
                variables.put(FPJS_PRO_REGION, config.getRegion());
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("An unexpected error has occurred while trying to apply FingerprintJsV3Pro configuration", e);
        }
    }
}
