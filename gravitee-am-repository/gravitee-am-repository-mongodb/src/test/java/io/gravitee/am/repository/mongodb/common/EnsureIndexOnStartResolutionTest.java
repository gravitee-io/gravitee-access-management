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
package io.gravitee.am.repository.mongodb.common;

import com.mongodb.MongoNamespace;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.mongodb.gateway.upgrader.MongoGatewayIndexUpgrader;
import io.gravitee.am.repository.mongodb.management.AbstractManagementMongoRepository;
import io.gravitee.am.repository.mongodb.oauth2.upgrader.MongoOAuth2IndexUpgrader;
import io.reactivex.rxjava3.core.Flowable;
import org.bson.Document;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * Pins which configuration keys switch MongoDB index management on and off, per repository scope.
 * <p>
 * The keys are worth pinning because they are not consistent between scopes: each scope resolves a
 * different chain, and one of the combinations asserted below is surprising enough that it has
 * caused production incidents.
 *
 * @author GraviteeSource Team
 */
public class EnsureIndexOnStartResolutionTest {

    private static final String MODERN_MANAGEMENT_KEY = "repositories.management.mongodb.ensureIndexOnStart";
    private static final String LEGACY_MANAGEMENT_KEY = "management.mongodb.ensureIndexOnStart";
    private static final String MODERN_OAUTH2_KEY = "repositories.oauth2.mongodb.ensureIndexOnStart";
    private static final String LEGACY_OAUTH2_KEY = "oauth2.mongodb.ensureIndexOnStart";
    private static final String MODERN_GATEWAY_KEY = "repositories.gateway.mongodb.ensureIndexOnStart";

    // --- management scope -------------------------------------------------------------------

    @Test
    public void managementIndexesAreCreatedByDefault() {
        assertThat(managementCreatesIndexes()).isTrue();
    }

    @Test
    public void managementIndexesAreDisabledByItsModernKey() {
        assertThat(managementCreatesIndexes(MODERN_MANAGEMENT_KEY, "false")).isFalse();
    }

    @Test
    public void managementIndexesAreDisabledByItsLegacyKey() {
        assertThat(managementCreatesIndexes(LEGACY_MANAGEMENT_KEY, "false")).isFalse();
    }

    @Test
    public void managementModernKeyTakesPrecedenceOverItsLegacyKey() {
        assertThat(managementCreatesIndexes(MODERN_MANAGEMENT_KEY, "true", LEGACY_MANAGEMENT_KEY, "false")).isTrue();
    }

    // --- oauth2 scope (the token stores) ----------------------------------------------------

    @Test
    public void tokenStoreIndexesAreCreatedByDefault() {
        assertThat(oauth2CreatesIndexes()).isTrue();
    }

    @Test
    public void tokenStoreIndexesAreDisabledByTheLegacyOauth2Key() {
        assertThat(oauth2CreatesIndexes(LEGACY_OAUTH2_KEY, "false")).isFalse();
    }

    @Test
    public void shouldDisableTokenStoreIndexesByTheModernOauth2Key() {
        assertThat(oauth2CreatesIndexes(MODERN_OAUTH2_KEY, "false")).isFalse();
    }

    @Test
    public void shouldPreferTheModernOauth2KeyOverTheLegacyOne() {
        assertThat(oauth2CreatesIndexes(MODERN_OAUTH2_KEY, "true", LEGACY_OAUTH2_KEY, "false")).isTrue();
    }

    // --- gateway scope ----------------------------------------------------------------------

    @Test
    public void gatewayIndexesAreCreatedByDefault() {
        assertThat(gatewayResolves()).isTrue();
    }

    @Test
    public void gatewayIndexesAreDisabledByItsOwnKey() {
        assertThat(gatewayResolves(MODERN_GATEWAY_KEY, "false")).isFalse();
    }

    /**
     * The gateway scope falls back to the legacy oauth2 key, so a single legacy setting intended for
     * the token stores also switches off index creation for every gateway collection.
     */
    @Test
    public void gatewayIndexesAreAlsoDisabledByTheLegacyOauth2Key() {
        assertThat(gatewayResolves(LEGACY_OAUTH2_KEY, "false")).isFalse();
    }

    @Test
    public void gatewayFallsBackToManagementWhenNoGatewayOrOauth2KeyIsSet() {
        assertThat(gatewayResolves(MODERN_MANAGEMENT_KEY, "false")).isFalse();
        assertThat(gatewayResolves(LEGACY_MANAGEMENT_KEY, "false")).isFalse();
    }

    @Test
    public void gatewayPrefersTheLegacyOauth2KeyOverTheManagementFallback() {
        assertThat(gatewayResolves(LEGACY_OAUTH2_KEY, "true", MODERN_MANAGEMENT_KEY, "false")).isTrue();
    }

    // --- probes -----------------------------------------------------------------------------

    public static class ManagementProbe extends AbstractManagementMongoRepository {
        void createIndexOn(MongoCollection<?> collection) {
            createIndex(collection, SOME_INDEX);
        }
    }

    private static final Map<Document, IndexOptions> SOME_INDEX =
            Map.of(new Document("field", 1), new IndexOptions().name("i1"));

    // --- helpers ----------------------------------------------------------------------------

    private static boolean managementCreatesIndexes(String... properties) {
        MongoCollection<?> collection = mockCollection();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(env(properties));
            context.registerBean(FilterCriteriaParser.class, () -> new FilterCriteriaParser());
            context.registerBean("managementMongoTemplate", MongoDatabase.class, () -> mock(MongoDatabase.class));
            context.register(ManagementProbe.class);
            context.refresh();

            context.getBean(ManagementProbe.class).createIndexOn(collection);
        }
        return mockingDetails(collection).getInvocations().stream()
                .anyMatch(invocation -> "createIndexes".equals(invocation.getMethod().getName()));
    }

    @SuppressWarnings("unchecked")
    private static MongoCollection<?> mockCollection() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(collection.createIndexes(any())).thenReturn(Flowable.empty());
        when(collection.getNamespace()).thenReturn(new MongoNamespace("test", "probe_collection"));
        return collection;
    }

    private static boolean oauth2CreatesIndexes(String... properties) {
        return new MongoOAuth2IndexUpgrader.Changelog(mock(MongoDatabase.class), repositoriesEnvironment(properties)).isEnabled();
    }

    private static boolean gatewayResolves(String... properties) {
        return new MongoGatewayIndexUpgrader.Changelog(mock(MongoDatabase.class), repositoriesEnvironment(properties)).isEnabled();
    }

    private static RepositoriesEnvironment repositoriesEnvironment(String... keyValuePairs) {
        return new RepositoriesEnvironment(env(keyValuePairs));
    }

    private static MockEnvironment env(String... keyValuePairs) {
        MockEnvironment environment = new MockEnvironment();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            environment.setProperty(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return environment;
    }
}
