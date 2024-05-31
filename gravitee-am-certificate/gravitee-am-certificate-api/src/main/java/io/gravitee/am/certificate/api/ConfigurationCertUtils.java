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
package io.gravitee.am.certificate.api;

import com.nimbusds.jose.util.JSONObjectUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

/**
 * @author Lukasz GAWEL (lukasz.gawel at graviteesource.com)
 * @author GraviteeSource Team
 */

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigurationCertUtils {

    public static Map<String, Object> configurationStringAsMap(String configuration) {
        if (configuration == null) {
            return Map.of() ;
        }
        try {
            return JSONObjectUtils.parse(configuration);
        } catch (ParseException e) {
            log.warn("Problem at parsing certificate configuration, msg={}", e.getMessage());
            return Map.of();
        }
    }

    public static List<String> extractUsageFromCertConfiguration(String configuration) {
        if (configuration == null) {
            return List.of();
        }
        try {
            Map<String, Object> cfg = JSONObjectUtils.parse(configuration);
            List<String> uses = JSONObjectUtils.getStringList(cfg, "use");
            return uses == null ? List.of() : uses;
        } catch (ParseException e) {
            log.warn("Problem at parsing certificate configuration, msg={}", e.getMessage());
            return List.of();
        }
    }

    public static boolean usageContains(String configuration, String usage) {
        return extractUsageFromCertConfiguration(configuration).contains(usage);
    }
}
