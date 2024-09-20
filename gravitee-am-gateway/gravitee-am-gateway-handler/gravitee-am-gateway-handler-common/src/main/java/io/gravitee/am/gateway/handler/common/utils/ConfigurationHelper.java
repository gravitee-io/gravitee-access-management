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

package io.gravitee.am.gateway.handler.common.utils;


import org.springframework.core.env.Environment;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConfigurationHelper {

    public static boolean useInMemoryRoleAndGroupManager(Environment environment) {
        final boolean inMemoryPermission = environment.getProperty("services.sync.permissions", Boolean.class, false);
        final boolean useResilientMode = environment.getProperty("resilience.enabled", Boolean.class, false);
        return inMemoryPermission || useResilientMode;
    }

    public static boolean useUserStore(Environment environment) {
        final boolean sessionCache = environment.getProperty("user.cache.enabled", Boolean.class, false);
        final boolean resilienceMode = environment.getProperty("resilience.enabled", Boolean.class, false);
        return resilienceMode || sessionCache;
    }
}
