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
package io.gravitee.am.reporter.jdbc.tool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class DatabaseUrlProvider {

    public String getDatabaseType() {
        final String jdbcType = System.getProperty("jdbcType", "postgresql-tc~14.1");
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
        final String jdbcType = System.getProperty("jdbcType", "postgresql-tc~14.1");
        // use sys.out to display in to the junit logs which DB is used
        System.out.println("Run Tests with "+ jdbcType + " database container");
        R2dbcDatabaseContainer dbContainer = null;
        if (jdbcType.startsWith("mssql-tc")) {
            if (jdbcType.contains("~")) {
                dbContainer = new MssqlR2DBCContainer(new MSSQLServerContainer(MSSQLServerContainer.IMAGE + ":" + jdbcType.split("~")[1]));
            } else {
                dbContainer = new MssqlR2DBCContainer(new MSSQLServerContainer());
            }
        }

        if (jdbcType.startsWith("mysql-tc")) {
            if (jdbcType.contains("~")) {
                dbContainer = new MysqlR2DBCContainer(new MySQLContainer(MySQLContainer.NAME + ":" + jdbcType.split("~")[1]));
            } else {
                dbContainer = new MysqlR2DBCContainer(new MySQLContainer());
            }
        }

        if (jdbcType.startsWith("mariadb-tc")) {
            if (jdbcType.contains("~")) {
                dbContainer = new MariaR2DBCContainer(new MariaDBContainer(MariaDBContainer.NAME + ":" + jdbcType.split("~")[1]));
            } else {
                dbContainer = new MariaR2DBCContainer(new MariaDBContainer());
            }
        }

        if (jdbcType.startsWith("postgresql-tc")) {
            if (jdbcType.contains("~")) {
                dbContainer = new PostgresR2DBCContainer(new PostgreSQLContainer(PostgreSQLContainer.IMAGE + ":" + jdbcType.split("~")[1]));
            } else {
                dbContainer = new PostgresR2DBCContainer(new PostgreSQLContainer());
            }
        }

        dbContainer.start();
        return dbContainer;
    }
}
