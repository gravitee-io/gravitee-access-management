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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class DatabaseUrlProvider {
    public static final Logger LOGGER = LoggerFactory.getLogger(DatabaseUrlProvider.class);

    public static final String POSTGRESQL_URL = "r2dbc:tc:postgresql:///databasename?TC_IMAGE_TAG=15.1";
    public static final String MSSQL_URL = "r2dbc:tc:sqlserver:///?TC_IMAGE_TAG=2019-latest";
    public static final String MYSQL_URL = "r2dbc:tc:mysql:///databasename?TC_IMAGE_TAG=8.0.27";
    public static final String MARIADB_URL = "r2dbc:tc:mariadb:///databasename?TC_IMAGE_TAG=10.6.5";

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

    public String getR2dbcUrl() {
        final String jdbcType = System.getProperty("jdbcType", "postgresql-tc~15.1");
        // use sys.out to display in to the junit logs which DB is used
        System.out.println("Run Tests with "+ jdbcType + " database container");

        if (jdbcType.startsWith("postgresql-tc")) {
            return POSTGRESQL_URL;
        }

        if (jdbcType.startsWith("mssql-tc")) {
            return MSSQL_URL;
        }

        if (jdbcType.startsWith("mysql-tc")) {
            return MYSQL_URL;
        }

        if (jdbcType.startsWith("mariadb-tc")) {
            return MARIADB_URL;
        }

        return POSTGRESQL_URL;
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
