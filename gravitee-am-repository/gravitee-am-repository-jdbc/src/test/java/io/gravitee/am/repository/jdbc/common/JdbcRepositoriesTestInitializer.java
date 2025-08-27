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
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JdbcRepositoriesTestInitializer implements RepositoriesTestInitializer {


    private static final int MAX_ATTEMPTS = 5;
    protected final ConnectionFactory connectionFactory;
    protected final DatabaseDialectHelper dialect;

    public void removeAllData() {

        var tables = List.of(
                "access_tokens",
                "authorization_codes",
                "refresh_tokens",
                "scope_approvals",
                "request_objects",

                "users",
                "user_entitlements",
                "user_roles",
                "user_attributes",
                "user_addresses",
                "application_factors",
                "application_identities",
                "application_scope_settings",
                "application_client_secrets",
                "applications",
                "tags",
                "scope_claims",
                "scopes",
                "role_oauth_scopes",
                "roles",
                "uma_resource_scopes",
                "uma_resource_set",
                "reporters",
                "memberships",
                "login_attempts",
                "identities",
                dialect.toSql(SqlIdentifier.quoted("groups")),
                "group_members",
                "group_roles",
                "forms",
                "factors",
                "extension_grants",
                "events",
                "entrypoints",
                "emails",
                "webauthn_credentials",
                "certificates",
                "uma_access_policies",
                "domains",
                "domain_identities",
                "domain_tags",
                "domain_vhosts",
                "environments",
                "environment_domain_restrictions",
                "environment_hrids",
                "organizations",
                "organization_identities",
                "organization_domain_restrictions",
                "organization_hrids",
                "installations",
                "alert_triggers_alert_notifiers",
                "alert_triggers",
                "alert_notifiers",
                "service_resources",
                "node_monitoring",
                "bot_detections",

                "organization_users",
                "organization_user_entitlements",
                "organization_user_roles",
                "organization_user_attributes",
                "organization_user_addresses",

                "pushed_authorization_requests",

                "system_tasks",

                "ciba_auth_requests",
                "authentication_device_notifiers",

                "notification_acknowledgements",
                "user_notifications",

                "i18n_dictionaries",
                "i18n_dictionary_entries",

                "themes",
                "password_histories",
                "rate_limit",
                "ratelimit_api",
                "verify_attempt",

                "password_policies",
                "account_access_tokens",
                "upgraders"
        );

        // in seconds
        var timeoutPerTable = 2;
        var timeoutTechnical = 1;
        var timeoutTotal = 30;

        var retryCounterTotal = new AtomicInteger(0);
        Single.fromPublisher(connectionFactory.create())
                .flatMapCompletable(connection -> Completable.fromPublisher(connection.beginTransaction())
                        .andThen(Flowable.fromIterable(tables)
                                .concatMapCompletable(table -> deleteAll(table, connection, timeoutPerTable))
                                .andThen(Completable.fromPublisher(connection.commitTransaction())
                                        .timeout(timeoutTechnical, TimeUnit.SECONDS, Completable.error(new TimeoutException("timeout: commit [%ds]".formatted(timeoutTechnical)))))
                                .onErrorResumeNext(err -> {
                                    log.error("Got an error while clearing database - rolling back", err);
                                    return Completable.fromPublisher(connection.rollbackTransaction());
                                })
                        )
                        .doFinally(() -> Completable.fromPublisher(connection.close()).subscribe()))
                .timeout(timeoutTotal, TimeUnit.SECONDS,
                        Completable.fromAction(() -> {
                            throw new TimeoutException("[attempt #%d/%d] timeout: clearing database didn't finish within %ds".formatted(retryCounterTotal.incrementAndGet(), MAX_ATTEMPTS, timeoutTotal));
                        }))
                .doOnError(ex -> log.error("Error clearing database", ex))
                .doOnComplete(() -> log.debug("DB cleared on attempt {}", retryCounterTotal.get()))
                .retry(MAX_ATTEMPTS - 1, ex -> ex instanceof TimeoutException)
                .blockingAwait();


    }

    private Completable deleteAll(String table, Connection connection, int timeoutSeconds) {
        AtomicInteger retryCounter = new AtomicInteger(0);
        return Single.fromPublisher(connection.createStatement("delete from " + table)
                        .execute())
                .flatMap(result -> Single.fromPublisher(result.getRowsUpdated()))
                .ignoreElement()
                .timeout(timeoutSeconds, TimeUnit.SECONDS,
                        Completable.fromAction(() -> {
                            throw new TimeoutException("[attempt #%d/%d] timeout: 'delete from %s' didn't finish within %ds".formatted(retryCounter.incrementAndGet(), MAX_ATTEMPTS, table, timeoutSeconds));
                        }))
                .doOnError(ex -> {
                    if (!(ex instanceof TimeoutException)) {
                        log.error("Error clearing table '{}'", table, ex);
                    }
                })
                .doOnComplete(() -> log.debug("Table '{}' cleared on attempt {}", table, retryCounter.get()))
                .retry(MAX_ATTEMPTS - 1, x -> x instanceof TimeoutException);
    }


    @Override
    public void before(Class testClass) {
        removeAllData();
    }

}
