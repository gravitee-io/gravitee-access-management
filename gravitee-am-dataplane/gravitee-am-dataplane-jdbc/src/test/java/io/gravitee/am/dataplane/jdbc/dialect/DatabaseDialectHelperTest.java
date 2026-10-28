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
package io.gravitee.am.dataplane.jdbc.dialect;

import io.gravitee.am.repository.management.api.search.FilterCriteria;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class DatabaseDialectHelperTest {

    private static final String BASE_USERS = " FROM users WHERE reference_id = :refId AND reference_type = :refType AND ";
    private static final String BASE_GROUPS = " FROM groups WHERE reference_id = :refId AND reference_type = :refType AND ";

    private PostgresqlHelper helper() {
        return new PostgresqlHelper(Mockito.mock(R2dbcDialect.class), null);
    }

    private FilterCriteria criteria(String name, String value, String operator) {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName(name);
        criteria.setFilterValue(value);
        criteria.setOperator(operator);
        return criteria;
    }

    @Test
    public void shouldRejectUserFilterAttributeWithSqlTautology() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                helper().prepareScimSearchQuery(new StringBuilder(BASE_USERS), criteria("1=1/**/or/**/1=1", null, "pr"), "id", 0, 10, DatabaseDialectHelper.ScimRepository.USERS));
    }

    @Test
    public void shouldRejectUserFilterAttributeWithQuote() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                helper().prepareScimSearchQuery(new StringBuilder(BASE_USERS), criteria("us'ername", "x", "eq"), "id", 0, 10, DatabaseDialectHelper.ScimRepository.USERS));
    }

    @Test
    public void shouldRejectJsonKeyWithQuote() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                helper().prepareScimSearchQuery(new StringBuilder(BASE_USERS), criteria("additionalInformation.ab'c", "x", "eq"), "id", 0, 10, DatabaseDialectHelper.ScimRepository.USERS));
    }

    @Test
    public void shouldRejectGroupFilterAttributeWithSqlTautology() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                helper().prepareScimSearchQuery(new StringBuilder(BASE_GROUPS), criteria("1=1/**/or/**/1=1", null, "pr"), "id", 0, 10, DatabaseDialectHelper.ScimRepository.GROUPS));
    }

    @Test
    public void shouldAcceptBareColumnFilterAttribute() {
        ScimSearch search = helper().prepareScimSearchQuery(new StringBuilder(BASE_USERS), criteria("username", "alice", "eq"), "id", 0, 10, DatabaseDialectHelper.ScimRepository.USERS);
        assertTrue("query keeps bare column", search.getSelectQuery().startsWith("SELECT * " + BASE_USERS + "username = :username"));
        assertEquals("binding should contain username", "alice", search.getBinding().get("username"));
    }

    @Test
    public void shouldAcceptAdditionalInformationCustomKey() {
        ScimSearch search = helper().prepareScimSearchQuery(new StringBuilder(BASE_USERS), criteria("additionalInformation.custom_key", "v", "eq"), "id", 0, 10, DatabaseDialectHelper.ScimRepository.USERS);
        assertTrue("query maps JSON key", search.getSelectQuery().contains("additional_information ->> 'custom_key'"));
    }

    @Test
    public void shouldBuildWildcardLikePattern() {
        assertEquals("user\\_1%", helper().toWildcardLikePattern("user_1*"));
        assertEquals("\\%%", helper().toWildcardLikePattern("%*"));
        assertEquals("a\\\\b%", helper().toWildcardLikePattern("a\\b**"));
        assertEquals("", helper().getLikeEscapeClause());
    }

    @Test
    public void shouldBuildWildcardLikePatternAndEscapeClause_MsSql() {
        MsSqlHelper helper = new MsSqlHelper(Mockito.mock(R2dbcDialect.class), null);

        assertEquals("\\[a\\]\\%\\_%", helper.toWildcardLikePattern("[a]%_*"));
        assertEquals(6, helper.buildCountUserQuery(true).split("ESCAPE '\\\\'", -1).length - 1);
        assertTrue(helper.buildSearchUserQuery(false, 0, 10).indexOf("ESCAPE") < 0);
    }
}
