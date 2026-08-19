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

import com.mongodb.client.model.IndexOptions;
import com.mongodb.MongoNamespace;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.mongodb.gateway.AbstractGatewayMongoRepository;
import io.gravitee.am.repository.mongodb.management.AbstractManagementMongoRepository;
import io.gravitee.am.repository.mongodb.oauth2.AbstractOAuth2MongoRepository;
import io.reactivex.rxjava3.core.Flowable;
import org.bson.Document;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * Pins which configuration keys switch MongoDB index creation on and off, per repository scope.
 * <p>
 * Index creation never fails loudly - {@link MongoUtils#createIndex} subscribes without blocking and
 * only logs - so an operator who disables it by accident gets a healthy-looking node whose token
 * collections silently stop self-cleaning. The keys are worth pinning because they are not
 * consistent between scopes: each scope resolves a different chain, and two of the combinations
 * asserted below are surprising enough that they have caused production incidents.
 * <p>
 * Each scope is exercised through its real resolution path rather than by inspecting the flag.
 * Management and oauth2 read a {@code @Value} field that only Spring populates, so those run in a
 * small application context and assert the observable outcome - whether indexes are actually
 * created on the collection. Gateway resolves through a method, so it is called directly.
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

    /**
     * The token stores read the legacy key only. Setting the modern, scope-prefixed key that every
     * other repository honors leaves index creation switched on - including the TTL indexes that are
     * the only thing deleting expired tokens on MongoDB. An operator who standardizes on the
     * {@code repositories.*} naming will believe they have disabled index creation when they have not.
     */
    @Test
    public void tokenStoreIndexesIgnoreTheModernOauth2Key() {
        // asserted as a contrast so this cannot pass merely because true is also the default
        assertThat(oauth2CreatesIndexes(LEGACY_OAUTH2_KEY, "false"))
                .as("the legacy key must switch token store indexes off")
                .isFalse();
        assertThat(oauth2CreatesIndexes(MODERN_OAUTH2_KEY, "false"))
                .as("the modern scope-prefixed key must be ignored by the token stores")
                .isTrue();
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

    /** Drives the real {@code createIndex} of the management scope. */
    public static class ManagementProbe extends AbstractManagementMongoRepository {
        void createIndexOn(MongoCollection<?> collection) {
            createIndex(collection, SOME_INDEX);
        }
    }

    /** Drives the real {@code createIndex} of the oauth2 scope, whose flag is private. */
    public static class OAuth2Probe extends AbstractOAuth2MongoRepository {
        void createIndexOn(MongoCollection<?> collection) {
            createIndex(collection, SOME_INDEX);
        }
    }

    /** The gateway scope resolves through a method, so the probe just exposes it. */
    public static class GatewayProbe extends AbstractGatewayMongoRepository {
        boolean ensureIndexOnStart() {
            return getEnsureIndexOnStart();
        }
    }

    private static final Map<Document, IndexOptions> SOME_INDEX =
            Map.of(new Document("field", 1), new IndexOptions().name("i1"));

    // --- helpers ----------------------------------------------------------------------------

    private static boolean managementCreatesIndexes(String... properties) {
        return createsIndexes(ManagementProbe.class, "managementMongoTemplate",
                (probe, collection) -> ((ManagementProbe) probe).createIndexOn(collection), properties);
    }

    private static boolean oauth2CreatesIndexes(String... properties) {
        return createsIndexes(OAuth2Probe.class, "oauth2MongoTemplate",
                (probe, collection) -> ((OAuth2Probe) probe).createIndexOn(collection), properties);
    }

    /**
     * Builds a minimal context so Spring resolves the {@code @Value} exactly as it would at runtime,
     * then reports whether the repository actually asked the collection to create its indexes.
     */
    private static boolean createsIndexes(Class<?> probeType, String templateBeanName,
                                          BiConsumer<Object, MongoCollection<?>> driveCreateIndex,
                                          String... properties) {
        MongoCollection<?> collection = mockCollection();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(env(properties));
            context.registerBean(FilterCriteriaParser.class, () -> new FilterCriteriaParser());
            context.registerBean(templateBeanName, MongoDatabase.class, () -> mock(MongoDatabase.class));
            context.register(probeType);
            context.refresh();

            driveCreateIndex.accept(context.getBean(probeType), collection);
        }
        return mockingDetails(collection).getInvocations().stream()
                .anyMatch(invocation -> "createIndexes".equals(invocation.getMethod().getName()));
    }

    @SuppressWarnings("unchecked")
    private static MongoCollection<?> mockCollection() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(collection.createIndexes(any())).thenReturn(Flowable.empty());
        // index creation names its collection in everything it logs and reports, so the mock has to
        // carry a namespace the way a real collection always does
        when(collection.getNamespace()).thenReturn(new MongoNamespace("test", "probe_collection"));
        return collection;
    }

    private static boolean gatewayResolves(String... properties) {
        MockEnvironment environment = env(properties);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.registerBean(FilterCriteriaParser.class, () -> new FilterCriteriaParser());
            context.registerBean("gatewayMongoTemplate", MongoDatabase.class, () -> mock(MongoDatabase.class));
            context.registerBean(RepositoriesEnvironment.class, () -> new RepositoriesEnvironment(environment));
            context.register(GatewayProbe.class);
            context.refresh();

            return context.getBean(GatewayProbe.class).ensureIndexOnStart();
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
