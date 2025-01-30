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
package io.gravitee.am.dataplane.api.repository.test;

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.dataplane.jdbc.dialect.DatabaseDialectHelper;
import io.gravitee.am.dataplane.jdbc.spring.AbstractRepositoryConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "io.gravitee.am.dataplane.jdbc.repository.spring")
@ComponentScan({"io.gravitee.am.dataplane.api.repository.test.testcontainers", "io.gravitee.am.dataplane.jdbc.repository"})
public class DataPlaneTestConfigurationLoader extends AbstractTestRepositoryConfiguration {

    @Bean
    public DataPlaneTestInitializer dataPlaneTestInitializer(ConnectionFactory connectionFactory, DatabaseDialectHelper databaseDialectHelper) {
        return new JdbcRepositoriesTestInitializer(connectionFactory, databaseDialectHelper);
    }


}
