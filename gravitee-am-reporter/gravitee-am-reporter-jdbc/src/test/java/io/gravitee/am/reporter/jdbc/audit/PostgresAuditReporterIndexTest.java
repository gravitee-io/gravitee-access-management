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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assume.assumeTrue;

/**
 * Asserts that the JDBC audit reporter's schema actually produces the indexes it declares.
 * <p>
 * The reporter builds its own schema from hand-maintained per-vendor SQL under
 * {@code src/main/resources/database/}, one file per database, each written differently. Nothing
 * checks that a given index made it into every vendor's file, so an index added to three of the
 * four goes unnoticed.
 * <p>
 * The leading column is what is asserted, not merely that a column is indexed somewhere:
 * {@code reporter_audits} already carries composite indexes that include {@code timestamp} as a
 * trailing column, so "timestamp appears in some index" would hold even with the dedicated
 * timestamp index missing - which is the index the audit purge relies on to find expired rows.
 * <p>
 * PostgreSQL only. Index introspection has no portable form - MySQL and MariaDB need
 * {@code information_schema.STATISTICS.SEQ_IN_INDEX}, SQL Server needs
 * {@code sys.index_columns.key_ordinal} - and those variants are not covered here. The test skips
 * loudly rather than silently passing when another vendor profile is active.
 *
 * @author GraviteeSource Team
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {DatabaseUrlProvider.class, JdbcReporterJUnitConfiguration.class},
        loader = AnnotationConfigContextLoader.class)
public class PostgresAuditReporterIndexTest {

    /** Matches the tableSuffix configured by {@link JdbcReporterJUnitConfiguration}. */
    private static final String AUDITS_TABLE = "reporter_audits_junit";
    private static final String TIMESTAMP_INDEX = "idx_audits_timestamp_junit";

    /**
     * Each index of a table paired with the column it leads on.
     * {@code indkey[0]} is the first column of the index.
     */
    private static final String LEADING_INDEX_COLUMNS = """
            SELECT i.relname AS index_name, a.attname AS first_column
            FROM pg_class t
            JOIN pg_index ix ON t.oid = ix.indrelid
            JOIN pg_class i ON i.oid = ix.indexrelid
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ix.indkey[0]
            WHERE t.relname = :table
            """;

    @Autowired
    private DatabaseUrlProvider provider;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DatabaseClient databaseClient;

    @Before
    public void createReporterSchema() throws Exception {
        assumeTrue("PostgreSQL-only test, skipped for " + provider.getDatabaseType(),
                "postgresql".equals(provider.getDatabaseType()));

        AuditReporter reporter = new JdbcAuditReporter();
        context.getAutowireCapableBeanFactory().autowireBean(reporter);
        ((InitializingBean) reporter).afterPropertiesSet();

        // schema creation is asynchronous
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(leadingIndexColumns()).isNotEmpty());
    }

    @Test
    public void auditsTableShouldCarryAnIndexLeadingOnTimestamp() {
        Map<String, String> indexes = leadingIndexColumns();

        assertThat(indexes)
                .as("the audit purge scans by timestamp - without an index leading on it, purging "
                        + "a large audit table degrades to a full scan")
                .containsEntry(TIMESTAMP_INDEX, "timestamp");
    }

    /**
     * Guards the assertion above: these composite indexes also cover {@code timestamp}, but only as
     * a trailing column, so they must not be mistaken for the dedicated timestamp index.
     */
    @Test
    public void compositeAuditIndexesShouldStillLeadOnReferenceId() {
        Map<String, String> indexes = leadingIndexColumns();

        assertThat(indexes).containsEntry("idx_audits_ref_junit", "reference_id");
        assertThat(indexes).containsEntry("idx_audits_evttype_junit", "reference_id");
    }

    private Map<String, String> leadingIndexColumns() {
        List<Map<String, Object>> rows = databaseClient.sql(LEADING_INDEX_COLUMNS)
                .bind("table", AUDITS_TABLE)
                .fetch()
                .all()
                .collectList()
                .block();

        return rows == null ? Map.of() : rows.stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get("index_name")),
                row -> String.valueOf(row.get("first_column"))));
    }
}
