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

import io.gravitee.am.repository.RepositoriesTestInitializer;
import io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper;
import io.r2dbc.spi.ConnectionFactory;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Service
public class JdbcRepositoriesTestInitializer implements RepositoriesTestInitializer {

    @Autowired
    protected ConnectionFactory connectionFactory;

    @Autowired
    protected DatabaseDialectHelper dialect;

    public JdbcRepositoriesTestInitializer(ConnectionFactory connectionFactory, DatabaseDialectHelper dialect) {
        this.connectionFactory = connectionFactory;
        this.dialect = dialect;
    }

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
        tables.add("application_scope_settings");
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
        tables.add("environment_hrids");
        tables.add("organizations");
        tables.add("organization_identities");
        tables.add("organization_domain_restrictions");
        tables.add("organization_hrids");
        tables.add("installations");
        tables.add("alert_triggers_alert_notifiers");
        tables.add("alert_triggers");
        tables.add("alert_notifiers");
        tables.add("service_resources");
        tables.add("node_monitoring");
        tables.add("bot_detections");

        tables.add("organization_users");
        tables.add("organization_user_entitlements");
        tables.add("organization_user_roles");
        tables.add("organization_user_attributes");
        tables.add("organization_user_addresses");

        tables.add("pushed_authorization_requests");

        tables.add("system_tasks");

        tables.add("ciba_auth_requests");
        tables.add("authentication_device_notifiers");

        tables.add("notification_acknowledgements");
        tables.add("user_notifications");

        tables.add("i18n_dictionaries");
        tables.add("i18n_dictionary_entries");

        io.r2dbc.spi.Connection connection = Flowable.fromPublisher(connectionFactory.create()).blockingFirst();
        connection.beginTransaction();
        tables.stream().forEach(table -> {
            Flowable.fromPublisher(connection.createStatement("delete from " + table).execute()).subscribeOn(Schedulers.single()).blockingSubscribe();
        });
        connection.commitTransaction();
        connection.close();
    }

    @Override
    public void before(Class testClass) throws Exception {
        truncateTables();
    }

    @Override
    public void after(Class testClass) throws Exception {
        truncateTables();
    }
}
