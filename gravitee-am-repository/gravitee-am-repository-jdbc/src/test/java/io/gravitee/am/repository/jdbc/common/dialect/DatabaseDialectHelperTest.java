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
package io.gravitee.am.repository.jdbc.common.dialect;

import io.gravitee.am.repository.management.api.search.FilterCriteria;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DatabaseDialectHelperTest {

    @Test
    public void shouldPrepareScimSearchUserQuery_SingleStandardField() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        PostgresqlHelper helper = new PostgresqlHelper(dialect, null);

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("emails.value");
        criteria.setFilterValue("test@acme.fr");
        criteria.setOperator("eq");
        final String BASE_CLAUSE = " FROM users WHERE reference_id = :refId AND reference_type = :refType AND ";
        ScimSearch search = helper.prepareScimSearchQuery(new StringBuilder(BASE_CLAUSE),
                criteria, "id", 0, 10, DatabaseDialectHelper.ScimRepository.USERS);

        assertTrue("Query contains email clause", search.getSelectQuery().startsWith("SELECT * " + BASE_CLAUSE + "email = :email"));
        assertEquals("binding size should be 1", 1, search.getBinding().size());
        assertEquals("binding should contains email", "test@acme.fr", search.getBinding().get("email"));
    }

    @Test
    public void shouldPrepareScimSearchUserQuery_SingleStandardField_NotNull() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        PostgresqlHelper helper = new PostgresqlHelper(dialect, null);

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("emails.value");
        criteria.setFilterValue("test@acme.fr");
        criteria.setOperator("pr");
        final String BASE_CLAUSE = " FROM users WHERE reference_id = :refId AND reference_type = :refType AND ";
        ScimSearch search = helper.prepareScimSearchQuery(new StringBuilder(BASE_CLAUSE),
                criteria, "id", 0, 10, DatabaseDialectHelper.ScimRepository.USERS);

        assertTrue("query contains email NOT NULL", search.getSelectQuery().startsWith("SELECT * " + BASE_CLAUSE + "email IS NOT NULL "));
        assertTrue("binding size should be empty", search.getBinding().isEmpty());
    }

    @Test
    public void shouldPrepareScimSearchUserQuery_MultipleCriteria() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        PostgresqlHelper helper = new PostgresqlHelper(dialect, null);

        FilterCriteria emailEq = new FilterCriteria();
        emailEq.setFilterName("emails.value");
        emailEq.setFilterValue("test@acme.fr");
        emailEq.setOperator("eq");

        FilterCriteria createdAfter = new FilterCriteria();
        createdAfter.setFilterName("meta.created");
        createdAfter.setFilterValue("2020-01-01T00:00:00.000Z");
        createdAfter.setOperator("gt");

        FilterCriteria or = new FilterCriteria();
        or.setOperator("or");
        or.setFilterComponents(Arrays.asList(emailEq, createdAfter));

        final String BASE_CLAUSE = " FROM users WHERE reference_id = :refId AND reference_type = :refType AND ";
        ScimSearch search = helper.prepareScimSearchQuery(new StringBuilder(BASE_CLAUSE), or, "id", 0, 10, DatabaseDialectHelper.ScimRepository.USERS);

        assertTrue("Select clause contains OR operator", search.getSelectQuery().startsWith("SELECT * " + BASE_CLAUSE + "( email = :email OR created_at > :created_at )"));
        assertEquals("binding size should be 2", 2, search.getBinding().size());
        assertEquals("binding should contains email", "test@acme.fr", search.getBinding().get("email"));
        assertTrue("binding should contains date", search.getBinding().get("created_at") instanceof LocalDateTime);
    }

    @Test
    public void shouldPrepareScimSearchUserQuery_MultipleCriteria_SameField() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        PostgresqlHelper helper = new PostgresqlHelper(dialect, null);

        FilterCriteria emailEq = new FilterCriteria();
        emailEq.setFilterName("emails.value");
        emailEq.setFilterValue("test@acme.fr");
        emailEq.setOperator("eq");

        FilterCriteria emailEq2 = new FilterCriteria();
        emailEq2.setFilterName("emails.value");
        emailEq2.setFilterValue("test2@acme.fr");
        emailEq2.setOperator("eq");

        FilterCriteria or = new FilterCriteria();
        or.setOperator("or");
        or.setFilterComponents(Arrays.asList(emailEq, emailEq2));

        final String BASE_CLAUSE = " FROM users WHERE reference_id = :refId AND reference_type = :refType AND ";
        ScimSearch search = helper.prepareScimSearchQuery(new StringBuilder(BASE_CLAUSE), or, "id", 0, 10, DatabaseDialectHelper.ScimRepository.USERS);

        assertTrue("Select clause contains OR operator", search.getSelectQuery().startsWith("SELECT * " + BASE_CLAUSE + "( email = :email OR email = :email_c0 )"));
        assertEquals("binding size should be 2", 2, search.getBinding().size());
        assertEquals("binding should contains email", "test@acme.fr", search.getBinding().get("email"));
        assertEquals("binding should contains email2", "test2@acme.fr", search.getBinding().get("email_c0"));
    }

    @Test
    public void shouldPrepareScimSearchUserQuery_dateField() {
        final R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        PostgresqlHelper helper = new PostgresqlHelper(dialect, null);
        final FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("meta.loggedAt");
        criteria.setFilterValue("2023-09-05T10:36:11.571Z");
        criteria.setOperator("eq");
        String BASE_CLAUSE = " FROM users WHERE reference_id = :refId AND reference_type = :refType AND ";

        ScimSearch search = helper.prepareScimSearchQuery(new StringBuilder(BASE_CLAUSE), criteria, "id", 0, 1, DatabaseDialectHelper.ScimRepository.USERS);

        Assert.assertTrue("Query contains logged_at clause", search.getSelectQuery().startsWith("SELECT *  FROM users WHERE reference_id = :refId AND reference_type = :refType AND logged_at = :logged_at"));
        Assert.assertEquals("binding size should be 1", 1L, (long)search.getBinding().size());
        Assert.assertEquals("binding should contains date value", "2023-09-05T10:36:11.571", search.getBinding().get("logged_at").toString());
    }

    @Test
    public void shouldPrepareScimSearchUserQuery_dateField_presentOperator(){
        final R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        PostgresqlHelper helper = new PostgresqlHelper(dialect, null);
        final FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("meta.loggedAt");
        criteria.setOperator("pr");
        String BASE_CLAUSE = " FROM users WHERE reference_id = :refId AND reference_type = :refType AND ";

        ScimSearch search = helper.prepareScimSearchQuery(new StringBuilder(BASE_CLAUSE), criteria, "id", 0, 1, DatabaseDialectHelper.ScimRepository.USERS);

        Assert.assertTrue("Query contains logged_at clause", search.getSelectQuery().startsWith("SELECT *  FROM users WHERE reference_id = :refId AND reference_type = :refType AND logged_at IS NOT NULL"));
        Assert.assertEquals("binding size should be 0", 0L, (long)search.getBinding().size());

    }

    @Test
    public void shouldEscapeLikePatternValue_MsSql_Brackets() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        MsSqlHelper helper = new MsSqlHelper(dialect, null);

        // Test escaping of square brackets using backslash (requires ESCAPE '\' clause)
        assertEquals("app\\[test\\]", helper.escapeLikePatternValue("app[test]"));
        assertEquals("\\[", helper.escapeLikePatternValue("["));
        assertEquals("\\]", helper.escapeLikePatternValue("]"));
        assertEquals("app\\[test\\]%name", helper.escapeLikePatternValue("app[test]%name"));
        // Test that underscore is also escaped
        assertEquals("app\\_test", helper.escapeLikePatternValue("app_test"));
        // Test backslash escaping
        assertEquals("app\\\\test", helper.escapeLikePatternValue("app\\test"));
    }

    @Test
    public void shouldEscapeLikePatternValue_MsSql_NoSpecialChars() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        MsSqlHelper helper = new MsSqlHelper(dialect, null);

        // Test that values without special characters are unchanged
        assertEquals("apptest", helper.escapeLikePatternValue("apptest"));
        assertEquals("%test%", helper.escapeLikePatternValue("%test%"));
        assertEquals("app{test}", helper.escapeLikePatternValue("app{test}"));
    }

    @Test
    public void shouldEscapeLikePatternValue_MsSql_Null() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        MsSqlHelper helper = new MsSqlHelper(dialect, null);

        // Test null handling
        Assert.assertNull(helper.escapeLikePatternValue(null));
    }

    @Test
    public void shouldEscapeLikePatternValue_Postgresql_NoEscaping() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        PostgresqlHelper helper = new PostgresqlHelper(dialect, null);

        // PostgreSQL does not need escaping for brackets (default implementation)
        assertEquals("app[test]", helper.escapeLikePatternValue("app[test]"));
    }

    @Test
    public void shouldGetLikeEscapeClause_MsSql() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        MsSqlHelper helper = new MsSqlHelper(dialect, null);

        // SQL Server needs ESCAPE clause for backslash-escaped patterns
        assertEquals(" ESCAPE '\\' ", helper.getLikeEscapeClause());
    }

    @Test
    public void shouldGetLikeEscapeClause_Postgresql_Empty() {
        R2dbcDialect dialect = Mockito.mock(R2dbcDialect.class);
        PostgresqlHelper helper = new PostgresqlHelper(dialect, null);

        // PostgreSQL doesn't need ESCAPE clause (default implementation)
        assertEquals("", helper.getLikeEscapeClause());
    }
}
