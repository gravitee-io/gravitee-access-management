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
import io.r2dbc.spi.Option;
import org.testcontainers.containers.MSSQLR2DBCDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MssqlR2DBCContainer implements R2dbcDatabaseContainer {
    private MSSQLServerContainer dbContainer;
    private MSSQLR2DBCDatabaseContainer r2dbcContainer;

    public MssqlR2DBCContainer(MSSQLServerContainer dbContainer) {
        this.dbContainer = dbContainer;
        this.dbContainer.acceptLicense();
        this.r2dbcContainer = new MSSQLR2DBCDatabaseContainer(dbContainer);
    }

    public void start() {
        r2dbcContainer.start();
    }

    public void stop() {
        r2dbcContainer.stop();
    }

    public ConnectionFactoryOptions getOptions() {
        return ConnectionFactoryOptions.builder()
                .option(Option.valueOf("preferCursoredExecution"), false) // due to r2dbc-mssql 1.0.1+
                .from(MSSQLR2DBCDatabaseContainer.getOptions(dbContainer))
                .build();
    }

    @Override
    public String getJdbcUrl() {
        return dbContainer.getJdbcUrl();
    }
}
