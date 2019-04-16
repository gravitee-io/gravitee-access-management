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
package io.gravitee.am.plugins.reporter.spring;

import io.gravitee.am.plugins.reporter.core.ReporterConfigurationFactory;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.plugins.reporter.core.impl.ReporterConfigurationFactoryImpl;
import io.gravitee.am.plugins.reporter.core.impl.ReporterPluginManagerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class ReporterConfiguration {

    @Bean
    public ReporterPluginManager reporterPluginManager() {
        return new ReporterPluginManagerImpl();
    }

    @Bean
    public ReporterConfigurationFactory reporterConfigurationFactory() {
        return new ReporterConfigurationFactoryImpl();
    }
}
