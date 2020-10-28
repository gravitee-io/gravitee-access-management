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
package io.gravitee.am.repository.jdbc.common;

import io.r2dbc.spi.ConnectionFactoryOptions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.PostgreSQLR2DBCDatabaseContainer;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PostgresR2DBCContainer implements R2dbcDatabaseContainer {
    private PostgreSQLContainer dbContainer;
    private PostgreSQLR2DBCDatabaseContainer r2dbcContainer;

    public PostgresR2DBCContainer(PostgreSQLContainer dbContainer) {
        this.dbContainer = dbContainer;
        //this.dbContainer.withCommand("postgres -c max_connections=200");
        this.r2dbcContainer = new PostgreSQLR2DBCDatabaseContainer(dbContainer);
    }

    public void start() {
        System.out.println("Start Postgres Container");
        r2dbcContainer.start();
    }

    public void stop() {
        System.out.println("Stop Postgres Container");
        r2dbcContainer.stop();
    }

    public ConnectionFactoryOptions getOptions() {
        return PostgreSQLR2DBCDatabaseContainer.getOptions(dbContainer);
    }
}
