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

    public static final String POSTGRESQL_URL = "r2dbc:tc:postgresql:///databasename?TC_IMAGE_TAG=9.6.12";
    public static final String MSSQL_URL = "r2dbc:tc:sqlserver:///?TC_IMAGE_TAG=2017-CU12";
    public static final String MYSQL_URL = "r2dbc:tc:mysql:///databasename?TC_IMAGE_TAG=5.7.32";
    public static final String MARIADB_URL = "r2dbc:tc:mariadb:///databasename?TC_IMAGE_TAG=10.3.6";

    public String getDatabaseType() {
        final String jdbcType = System.getProperty("jdbcType", "postgresql-tc");
        if ("postgresql-tc".equals(jdbcType)){
            return "postgresql";
        }

        if ("mssql-tc".equals(jdbcType)) {
            return "mssql";
        }

        if ("mysql-tc".equals(jdbcType)) {
            return "mysql";
        }

        if ("mariadb-tc".equals(jdbcType)) {
            return "mariadb";
        }

        return "postgresql";
    }

    public String getR2dbcUrl() {
        final String jdbcType = System.getProperty("jdbcType", "postgresql-tc");
        // use sys.out to display in to the junit logs which DB is used
        System.out.println("Run Tests with "+ jdbcType + " database container");

        if ("postgresql-tc".equals(jdbcType)) {
            return POSTGRESQL_URL;
        }

        if ("mssql-tc".equals(jdbcType)) {
            return MSSQL_URL;
        }

        if ("mysql-tc".equals(jdbcType)) {
            return MYSQL_URL;
        }

        if ("mariadb-tc".equals(jdbcType)) {
            return MARIADB_URL;
        }

        return POSTGRESQL_URL;
    }

    @Bean(destroyMethod = "stop")
    public R2dbcDatabaseContainer getDatabaseContainer() {
        final String jdbcType = System.getProperty("jdbcType", "postgresql-tc");
        // use sys.out to display in to the junit logs which DB is used
        System.out.println("Run Tests with "+ jdbcType + " database container");
        R2dbcDatabaseContainer dbContainer = new PostgresR2DBCContainer(new PostgreSQLContainer<>());
        if ("mssql-tc".equals(jdbcType)) {
            dbContainer = new MssqlR2DBCContainer(new MSSQLServerContainer());
        }

        if ("mysql-tc".equals(jdbcType)) {
            dbContainer = new MysqlR2DBCContainer(new MySQLContainer());
        }

        if ("mariadb-tc".equals(jdbcType)) {
            dbContainer = new MariaR2DBCContainer(new MariaDBContainer());
        }

        dbContainer.start();

        // flag db as not initialize to force schema creation
        AbstractJdbcTest.initialized = false;
        return dbContainer;
    }
}
