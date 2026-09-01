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
package io.gravitee.am.plugins.dataplane.core;

import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.NoArgsConstructor;

import static io.gravitee.am.dataplane.api.DataPlaneDescription.DEFAULT_DATA_PLANE_ID;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@CustomLog
public class DataPlaneUtility {
    public static String evaluateDataPlaneId(String dataPlaneId, Class entityClass, String entityId) {
        if (!hasText(dataPlaneId)) {
            log.warn("{} '{}' has empty dataPlaneId, upgrader may have to be executed. Fallback to 'default'.", entityClass.getSimpleName(), entityId);
            dataPlaneId = DEFAULT_DATA_PLANE_ID;
        }
        return dataPlaneId;
    }
}
