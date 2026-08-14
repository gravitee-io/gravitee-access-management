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

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.mongodb.gateway.AbstractGatewayMongoRepository;
import io.gravitee.am.repository.mongodb.management.AbstractManagementMongoRepository;
import io.gravitee.am.repository.mongodb.oauth2.AbstractOAuth2MongoRepository;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which configuration keys switch MongoDB index creation on and off, per repository scope.
 * <p>
 * Index creation never fails loudly - {@code MongoUtils.createIndex} subscribes without blocking and
 * only logs - so an operator who disables it by accident gets a healthy-looking node whose token
 * collections silently stop self-cleaning. The keys are worth pinning because they are not
 * consistent between scopes: each scope resolves a different chain, and two of the combinations
 * asserted below are surprising enough that they have caused production incidents.
 * <p>
 * The {@code @Value} expressions are read off the production fields by reflection rather than
 * copied, so this test cannot drift away from the annotations it describes.
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
        assertThat(managementResolves(env())).isTrue();
    }

    @Test
    public void managementIndexesAreDisabledByItsModernKey() {
        assertThat(managementResolves(env(MODERN_MANAGEMENT_KEY, "false"))).isFalse();
    }

    @Test
    public void managementIndexesAreDisabledByItsLegacyKey() {
        assertThat(managementResolves(env(LEGACY_MANAGEMENT_KEY, "false"))).isFalse();
    }

    @Test
    public void managementModernKeyTakesPrecedenceOverItsLegacyKey() {
        assertThat(managementResolves(env(MODERN_MANAGEMENT_KEY, "true", LEGACY_MANAGEMENT_KEY, "false"))).isTrue();
    }

    // --- oauth2 scope (the token stores) ----------------------------------------------------

    @Test
    public void tokenStoreIndexesAreCreatedByDefault() {
        assertThat(oauth2Resolves(env())).isTrue();
    }

    @Test
    public void tokenStoreIndexesAreDisabledByTheLegacyOauth2Key() {
        assertThat(oauth2Resolves(env(LEGACY_OAUTH2_KEY, "false"))).isFalse();
    }

    /**
     * The token stores read the legacy key only. Setting the modern, scope-prefixed key that every
     * other repository honours leaves index creation switched on - including the TTL indexes that
     * are the only thing deleting expired tokens on MongoDB. An operator who standardises on the
     * {@code repositories.*} naming will believe they have disabled index creation when they have not.
     */
    @Test
    public void tokenStoreIndexesIgnoreTheModernOauth2Key() {
        // asserted as a contrast so this cannot pass merely because true is also the default
        assertThat(oauth2Resolves(env(LEGACY_OAUTH2_KEY, "false")))
                .as("the legacy key must switch token store indexes off")
                .isFalse();
        assertThat(oauth2Resolves(env(MODERN_OAUTH2_KEY, "false")))
                .as("the modern scope-prefixed key must be ignored by the token stores")
                .isTrue();
    }

    // --- gateway scope ----------------------------------------------------------------------

    @Test
    public void gatewayIndexesAreCreatedByDefault() {
        assertThat(gatewayResolves(env())).isTrue();
    }

    @Test
    public void gatewayIndexesAreDisabledByItsOwnKey() {
        assertThat(gatewayResolves(env(MODERN_GATEWAY_KEY, "false"))).isFalse();
    }

    /**
     * The gateway scope falls back to the legacy oauth2 key, so a single legacy setting intended for
     * the token stores also switches off index creation for every gateway collection.
     */
    @Test
    public void gatewayIndexesAreAlsoDisabledByTheLegacyOauth2Key() {
        assertThat(gatewayResolves(env(LEGACY_OAUTH2_KEY, "false"))).isFalse();
    }

    @Test
    public void gatewayFallsBackToManagementWhenNoGatewayOrOauth2KeyIsSet() {
        assertThat(gatewayResolves(env(MODERN_MANAGEMENT_KEY, "false"))).isFalse();
        assertThat(gatewayResolves(env(LEGACY_MANAGEMENT_KEY, "false"))).isFalse();
    }

    @Test
    public void gatewayPrefersTheLegacyOauth2KeyOverTheManagementFallback() {
        assertThat(gatewayResolves(env(LEGACY_OAUTH2_KEY, "true", MODERN_MANAGEMENT_KEY, "false"))).isTrue();
    }

    // --- helpers ----------------------------------------------------------------------------

    private static boolean managementResolves(MockEnvironment environment) {
        return resolveValueAnnotation(AbstractManagementMongoRepository.class, "ensureIndexOnStart", environment);
    }

    private static boolean oauth2Resolves(MockEnvironment environment) {
        return resolveValueAnnotation(AbstractOAuth2MongoRepository.class, "ensureIndexOnStart", environment);
    }

    private static boolean gatewayResolves(MockEnvironment environment) {
        AbstractGatewayMongoRepository repository = new AbstractGatewayMongoRepository() {
        };
        ReflectionTestUtils.setField(repository, "environment", new RepositoriesEnvironment(environment));
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(repository, "getEnsureIndexOnStart"));
    }

    /**
     * Resolves the {@code @Value} expression declared on the production field, exactly as Spring
     * would when building the bean.
     */
    private static boolean resolveValueAnnotation(Class<?> type, String fieldName, MockEnvironment environment) {
        Field field = ReflectionUtils.findField(type, fieldName);
        assertThat(field).as("field '%s' no longer exists on %s", fieldName, type.getSimpleName()).isNotNull();

        Value value = field.getAnnotation(Value.class);
        assertThat(value).as("field '%s' of %s is no longer configured via @Value", fieldName, type.getSimpleName())
                .isNotNull();

        return Boolean.parseBoolean(environment.resolvePlaceholders(value.value()));
    }

    private static MockEnvironment env(String... keyValuePairs) {
        MockEnvironment environment = new MockEnvironment();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            environment.setProperty(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return environment;
    }
}
