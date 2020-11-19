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
package io.gravitee.am.reporter.jdbc.audit;

import io.gravitee.am.reporter.jdbc.JdbcReporterConfiguration;
import io.gravitee.am.reporter.jdbc.spring.JdbcReporterSpringConfiguration;
import io.gravitee.am.reporter.jdbc.tool.R2dbcDatabaseContainer;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class JdbcReporterJUnitConfiguration extends JdbcReporterSpringConfiguration {

    @Autowired
    protected R2dbcDatabaseContainer dbContainer;

    @Override
    public ConnectionFactory buildConnectionFactory() {
        ConnectionFactoryOptions options = dbContainer.getOptions();
        options = ConnectionFactoryOptions.builder()
                .from(options)
                .option(DRIVER, "pool")
                .option(PROTOCOL, options.getValue(DRIVER))
                .build();
        return ConnectionFactories.get(options);
    }

    @Bean
    public JdbcReporterConfiguration reporterConfiguration(R2dbcDatabaseContainer dbContainer) {
        ConnectionFactoryOptions options = dbContainer.getOptions();
        JdbcReporterConfiguration config = new JdbcReporterConfiguration();
        config.setDriver(options.getValue(DRIVER));
        config.setDatabase(options.getValue(DATABASE));
        config.setHost(options.getValue(HOST));
        config.setPort(options.getValue(PORT));
        config.setUsername(options.getValue(USER));
        CharSequence value = options.getValue(PASSWORD);
        config.setPassword(String.join("", value));

        config.setTableSuffix("junit");
        config.setFlushInterval(1);
        return config;
    }
}
