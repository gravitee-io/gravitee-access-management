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
package io.gravitee.am.dataplane.api.repository.test.testcontainers;

import io.gravitee.am.dataplane.api.repository.test.testcontainers.MariaR2DBCContainer;
import io.gravitee.am.dataplane.api.repository.test.testcontainers.MssqlR2DBCContainer;
import io.gravitee.am.dataplane.api.repository.test.testcontainers.MysqlR2DBCContainer;
import io.gravitee.am.dataplane.api.repository.test.testcontainers.PostgresR2DBCContainer;
import io.gravitee.am.dataplane.api.repository.test.testcontainers.R2dbcDatabaseContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class DatabaseUrlProvider {
    private static final String DEFAULT_MSSQL_IMAGE = "mcr.microsoft.com/mssql/server:2019-latest";
    private static final String DEFAULT_MYSQL_IMAGE = "mysql:8.0.27";
    private static final String DEFAULT_MARIADB_IMAGE = "mariadb:10.6.5";
    private static final String DEFAULT_POSTGRES_IMAGE = "postgres:15.1";

    public String getDatabaseType() {
        final String jdbcType = System.getProperty("jdbcType", "postgresql-tc~15.1");

        if (jdbcType.startsWith("postgresql-tc")) {
            return "postgresql";
        }

        if (jdbcType.startsWith("mssql-tc")) {
            return "sqlserver";
        }

        if (jdbcType.startsWith("mysql-tc")) {
            return "mysql";
        }

        if (jdbcType.startsWith("mariadb-tc")) {
            return "mariadb";
        }

        return "postgresql";
    }

    @Bean(destroyMethod = "stop")
    public R2dbcDatabaseContainer getDatabaseContainer() {
        final String jdbcType = System.getProperty("jdbcType", "postgresql-tc~15.1");
        // use sys.out to display in to the junit logs which DB is used
        System.out.println("Run Tests with "+ jdbcType + " database container");
        R2dbcDatabaseContainer dbContainer = null;
        if (jdbcType.startsWith("mssql-tc")) {
            if (jdbcType.contains("~")) {
                dbContainer = new MssqlR2DBCContainer(new MSSQLServerContainer(MSSQLServerContainer.IMAGE + ":" + jdbcType.split("~")[1]));
            } else {
                dbContainer = new MssqlR2DBCContainer(new MSSQLServerContainer(DEFAULT_MSSQL_IMAGE));
            }
        }

        if (jdbcType.startsWith("mysql-tc")) {
            if (jdbcType.contains("~")) {
                dbContainer = new MysqlR2DBCContainer(new MySQLContainer(MySQLContainer.NAME + ":" + jdbcType.split("~")[1]));
            } else {
                dbContainer = new MysqlR2DBCContainer(new MySQLContainer(DEFAULT_MYSQL_IMAGE));
            }
        }

        if (jdbcType.startsWith("mariadb-tc")) {
            if (jdbcType.contains("~")) {
                dbContainer = new MariaR2DBCContainer(new MariaDBContainer(MariaDBContainer.NAME + ":" + jdbcType.split("~")[1]));
            } else {
                dbContainer = new MariaR2DBCContainer(new MariaDBContainer(DEFAULT_MARIADB_IMAGE));
            }
        }

        if (jdbcType.startsWith("postgresql-tc")) {
            if (jdbcType.contains("~")) {
                dbContainer = new PostgresR2DBCContainer(new PostgreSQLContainer(PostgreSQLContainer.IMAGE + ":" + jdbcType.split("~")[1]));
            } else {
                dbContainer = new PostgresR2DBCContainer(new PostgreSQLContainer(DEFAULT_POSTGRES_IMAGE));
            }
        }

        dbContainer.start();
        return dbContainer;
    }
}
