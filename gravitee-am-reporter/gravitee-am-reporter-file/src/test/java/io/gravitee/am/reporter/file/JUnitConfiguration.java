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
package io.gravitee.am.reporter.file;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.reporter.file.formatter.elasticsearch.freemarker.FreeMarkerComponent;
import io.gravitee.node.api.Node;
import io.vertx.core.Vertx;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan("io.gravitee.am.reporter.file")
public class JUnitConfiguration {

    @Bean
    public FileReporterConfiguration getTestConfiguration() {
        FileReporterConfiguration fileReporterConfiguration = new FileReporterConfiguration();
        fileReporterConfiguration.setFilename("FileAuditReporterTest");
        return fileReporterConfiguration;
    }

    @Bean
    public GraviteeContext graviteeContext() {
        return GraviteeContext.defaultContext("domain");
    }

    @Bean
    public FreeMarkerComponent getFreeMarkerComponent() {
        return new FreeMarkerComponent();
    }

    @Bean
    public Vertx vertx() {
        return Vertx.vertx();
    }

    @Bean
    public Node getNode() {
        return Mockito.mock(Node.class);
    }
}
