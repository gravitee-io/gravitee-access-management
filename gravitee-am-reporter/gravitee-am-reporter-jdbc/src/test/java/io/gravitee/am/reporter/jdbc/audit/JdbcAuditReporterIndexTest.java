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

import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.jdbc.tool.DatabaseUrlProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Asserts that the JDBC audit reporter's schema actually produces the indexes it declares, on every
 * supported database.
 * <p>
 * The reporter builds its own schema from hand-maintained per-vendor SQL under
 * {@code src/main/resources/database/}, one file per database, each written differently. Nothing
 * checks that a given index made it into every vendor's file, so an index added to three of the four
 * goes unnoticed.
 * <p>
 * Only the <em>leading</em> column of each index is asserted, for two reasons. First,
 * {@code reporter_audits} already carries composite indexes that include {@code timestamp} as a
 * trailing column, so "timestamp is indexed somewhere" would hold even with the dedicated timestamp
 * index missing - and that index is what the audit purge relies on to find expired rows. Second, the
 * index name is not portable: PostgreSQL, MariaDB and SQL Server declare it explicitly as
 * {@code idx_audits_timestamp___}, whereas MySQL declares it inline and unnamed, so MySQL
 * auto-generates the name {@code timestamp}. The leading column is the one property consistent
 * across all four.
 *
 * @author GraviteeSource Team
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {DatabaseUrlProvider.class, JdbcReporterJUnitConfiguration.class},
        loader = AnnotationConfigContextLoader.class)
public class JdbcAuditReporterIndexTest {

    /** Matches the tableSuffix configured by {@link JdbcReporterJUnitConfiguration}. */
    private static final String AUDITS_TABLE = "reporter_audits_junit";

    /** {@code indkey[0]} is the first column of the index. */
    private static final String POSTGRES_LEADING_COLUMNS = """
            SELECT a.attname AS first_column
            FROM pg_class t
            JOIN pg_index ix ON t.oid = ix.indrelid
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ix.indkey[0]
            WHERE t.relname = :table
            """;

    private static final String MYSQL_LEADING_COLUMNS = """
            SELECT COLUMN_NAME AS first_column
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :table AND SEQ_IN_INDEX = 1
            """;

    private static final String SQLSERVER_LEADING_COLUMNS = """
            SELECT c.name AS first_column
            FROM sys.indexes i
            JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE OBJECT_NAME(i.object_id) = :table AND ic.key_ordinal = 1
            """;

    @Autowired
    private DatabaseUrlProvider provider;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DatabaseClient databaseClient;

    @Before
    public void createReporterSchema() throws Exception {
        AuditReporter reporter = new JdbcAuditReporter();
        context.getAutowireCapableBeanFactory().autowireBean(reporter);
        ((InitializingBean) reporter).afterPropertiesSet();

        // schema creation is asynchronous
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(leadingIndexColumns()).isNotEmpty());
    }

    @Test
    public void auditsTableShouldCarryAnIndexLeadingOnTimestamp() {
        assertThat(leadingIndexColumns())
                .as("the audit purge scans by timestamp - without an index leading on it, purging a "
                        + "large audit table degrades to a full scan")
                .contains("timestamp");
    }

    /**
     * Guards the assertion above by proving the query reports the leading column rather than every
     * indexed column. {@code reference_type} and {@code type} appear in the composite indexes, but
     * never in first position - if they showed up here the query would be reporting membership, and
     * the timestamp assertion above would pass whether or not the dedicated index existed.
     */
    @Test
    public void onlyLeadingIndexColumnsShouldBeReported() {
        assertThat(leadingIndexColumns())
                .contains("reference_id")
                .doesNotContain("reference_type", "type");
    }

    private List<String> leadingIndexColumns() {
        List<Map<String, Object>> rows = databaseClient.sql(leadingColumnsQuery())
                .bind("table", AUDITS_TABLE)
                .fetch()
                .all()
                .collectList()
                .block();

        return rows == null ? List.of() : rows.stream()
                .map(row -> String.valueOf(row.get("first_column")))
                .toList();
    }

    /** Index introspection has no portable form, so the query - and only the query - varies. */
    private String leadingColumnsQuery() {
        return switch (provider.getDatabaseType()) {
            case "postgresql" -> POSTGRES_LEADING_COLUMNS;
            case "mysql", "mariadb" -> MYSQL_LEADING_COLUMNS;
            case "sqlserver" -> SQLSERVER_LEADING_COLUMNS;
            default -> throw new IllegalStateException(
                    "no index introspection query for database type " + provider.getDatabaseType());
        };
    }
}
