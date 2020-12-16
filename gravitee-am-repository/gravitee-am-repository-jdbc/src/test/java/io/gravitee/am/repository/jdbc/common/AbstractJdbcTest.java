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

import io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import liquibase.change.AbstractSQLChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractJdbcTest {
    private final Logger LOGGER = LoggerFactory.getLogger(AbstractJdbcTest.class);

    @Autowired
    protected ConnectionFactory cnxFact;

    @Autowired
    protected DatabaseDialectHelper dialect;

    @After
    @Before
    public void truncateTables() throws Exception {
            Set<String> tables = new HashSet<>();
            tables.add("access_tokens");
            tables.add("authorization_codes");
            tables.add("refresh_tokens");
            tables.add("scope_approvals");
            tables.add("request_objects");

            tables.add("users");
            tables.add("user_entitlements");
            tables.add("user_roles");
            tables.add("user_attributes");
            tables.add("user_addresses");
            tables.add("application_factors");
            tables.add("application_identities");
            tables.add("applications");
            tables.add("tags");
            tables.add("scope_claims");
            tables.add("scopes");
            tables.add("role_oauth_scopes");
            tables.add("roles");
            tables.add("uma_resource_scopes");
            tables.add("uma_resource_set");
            tables.add("reporters");
            tables.add("memberships");
            tables.add("login_attempts");
            tables.add("identities");
            tables.add(dialect.toSql(SqlIdentifier.quoted("groups")));
            tables.add("group_members");
            tables.add("group_roles");
            tables.add("forms");
            tables.add("factors");
            tables.add("extension_grants");
            tables.add("events");
            tables.add("entrypoints");
            tables.add("emails");
            tables.add("webauthn_credentials");
            tables.add("certificates");
            tables.add("uma_access_policies");
            tables.add("domains");
            tables.add("domain_identities");
            tables.add("domain_tags");
            tables.add("domain_vhosts");
            tables.add("environments");
            tables.add("environment_domain_restrictions");
            tables.add("organizations");
            tables.add("organization_identities");
            tables.add("organization_domain_restrictions");

            Connection connection = Flowable.fromPublisher(cnxFact.create()).blockingFirst();
            connection.beginTransaction();
            tables.stream().forEach(table -> {
                Flowable.fromPublisher(connection.createStatement("delete from " + table).execute()).subscribeOn(Schedulers.single()).blockingSubscribe();
            });
            connection.commitTransaction();
            connection.close();
    }
}
