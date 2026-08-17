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
package io.gravitee.am.dataplane.mongodb.repository;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.mongodb.common.FilterCriteriaParser;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins which configuration keys switch MongoDB index creation on and off for the data plane.
 * <p>
 * Index creation never fails loudly - {@code MongoUtils.createIndex} subscribes without blocking and
 * only logs - so an operator who disables it by accident gets a healthy-looking node whose data
 * plane collections silently stop self-cleaning. That matters here because the data plane's TTL
 * indexes are the only mechanism removing expired devices, login attempts, user activities, scope
 * approvals and permission tickets on MongoDB: the scheduled purge is enabled for JDBC only.
 * <p>
 * The data plane resolves a different chain from every other scope, which is the reason this is
 * asserted separately rather than assumed to match the gateway.
 *
 * @author GraviteeSource Team
 */
public class DataPlaneEnsureIndexOnStartResolutionTest {

    private static final String MODERN_GATEWAY_KEY = "repositories.gateway.mongodb.ensureIndexOnStart";
    private static final String MODERN_OAUTH2_KEY = "repositories.oauth2.mongodb.ensureIndexOnStart";
    private static final String LEGACY_OAUTH2_KEY = "oauth2.mongodb.ensureIndexOnStart";
    private static final String MODERN_MANAGEMENT_KEY = "repositories.management.mongodb.ensureIndexOnStart";
    private static final String LEGACY_MANAGEMENT_KEY = "management.mongodb.ensureIndexOnStart";

    @Test
    public void dataPlaneIndexesAreCreatedByDefault() {
        assertThat(resolves(env())).isTrue();
    }

    @Test
    public void dataPlaneIndexesAreDisabledByTheGatewayKey() {
        assertThat(resolves(env(MODERN_GATEWAY_KEY, "false"))).isFalse();
    }

    @Test
    public void dataPlaneIndexesAreDisabledByTheLegacyOauth2Key() {
        assertThat(resolves(env(LEGACY_OAUTH2_KEY, "false"))).isFalse();
    }

    @Test
    public void dataPlaneIndexesAreDisabledByTheModernOauth2Key() {
        assertThat(resolves(env(MODERN_OAUTH2_KEY, "false"))).isFalse();
    }

    /**
     * The gateway scope falls back to the management keys when nothing else is set; the data plane
     * falls back to the oauth2 keys instead. An operator who disables index creation through the
     * management key therefore switches it off for the gateway collections but leaves the data plane
     * collections - users, devices, login attempts - still indexing.
     */
    @Test
    public void dataPlaneIndexesAreNotAffectedByTheManagementKeys() {
        assertThat(resolves(env(MODERN_MANAGEMENT_KEY, "false")))
                .as("the data plane must not inherit the management key")
                .isTrue();
        assertThat(resolves(env(LEGACY_MANAGEMENT_KEY, "false")))
                .as("the data plane must not inherit the legacy management key")
                .isTrue();
    }

    @Test
    public void gatewayKeyTakesPrecedenceOverTheOauth2Fallback() {
        assertThat(resolves(env(MODERN_GATEWAY_KEY, "true", MODERN_OAUTH2_KEY, "false"))).isTrue();
    }

    /** Exposes the resolution method so the test does not have to reach for reflection. */
    public static class DataPlaneProbe extends AbstractDataPlaneMongoRepository {
        boolean ensureIndexOnStart() {
            return getEnsureIndexOnStart();
        }
    }

    private static boolean resolves(MockEnvironment environment) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.registerBean(FilterCriteriaParser.class, () -> new FilterCriteriaParser());
            context.registerBean("dataPlaneMongoDatabase", MongoDatabase.class, () -> mock(MongoDatabase.class));
            context.registerBean(RepositoriesEnvironment.class, () -> new RepositoriesEnvironment(environment));
            context.register(DataPlaneProbe.class);
            context.refresh();

            return context.getBean(DataPlaneProbe.class).ensureIndexOnStart();
        }
    }

    private static MockEnvironment env(String... keyValuePairs) {
        MockEnvironment environment = new MockEnvironment();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            environment.setProperty(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return environment;
    }
}
