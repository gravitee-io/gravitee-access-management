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
package io.gravitee.am.plugins.deviceidentifier.spring;

import io.gravitee.am.plugins.deviceidentifier.core.DeviceIdentifierConfigurationFactory;
import io.gravitee.am.plugins.deviceidentifier.core.DeviceIdentifierPluginManager;
import io.gravitee.am.plugins.deviceidentifier.core.impl.DeviceIdentifierConfigurationFactoryImpl;
import io.gravitee.am.plugins.deviceidentifier.core.impl.DeviceIdentifierPluginManagerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Rémi Sultan  (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class DeviceIdentifierConfiguration {

    @Bean
    public DeviceIdentifierPluginManager deviceIdentifierPluginManager() {
        return new DeviceIdentifierPluginManagerImpl();
    }

    @Bean
    public DeviceIdentifierConfigurationFactory deviceIdentifierConfigurationFactory() {
        return new DeviceIdentifierConfigurationFactoryImpl();
    }
}
