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
        ScimUserSearch search = helper.prepareScimSearchUserQuery(new StringBuilder(BASE_CLAUSE),
                criteria, 0, 10);

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
        ScimUserSearch search = helper.prepareScimSearchUserQuery(new StringBuilder(BASE_CLAUSE),
                criteria, 0, 10);

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
        ScimUserSearch search = helper.prepareScimSearchUserQuery(new StringBuilder(BASE_CLAUSE), or, 0, 10);

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
        ScimUserSearch search = helper.prepareScimSearchUserQuery(new StringBuilder(BASE_CLAUSE), or, 0, 10);

        assertTrue("Select clause contains OR operator", search.getSelectQuery().startsWith("SELECT * " + BASE_CLAUSE + "( email = :email OR email = :email_c0 )"));
        assertEquals("binding size should be 2", 2, search.getBinding().size());
        assertEquals("binding should contains email", "test@acme.fr", search.getBinding().get("email"));
        assertEquals("binding should contains email2", "test2@acme.fr", search.getBinding().get("email_c0"));
    }
}
