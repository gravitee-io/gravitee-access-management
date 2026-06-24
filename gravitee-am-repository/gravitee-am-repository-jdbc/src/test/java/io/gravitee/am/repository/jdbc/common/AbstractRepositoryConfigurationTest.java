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

import io.gravitee.am.common.env.RepositoriesEnvironment;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import static io.gravitee.am.repository.jdbc.common.AbstractRepositoryConfiguration.isLiquibaseEnabled;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class AbstractRepositoryConfigurationTest {

    private static final String PREFIX = "repositories.oauth2.jdbc.";

    private static RepositoriesEnvironment environment(String... pairs) {
        MockEnvironment env = new MockEnvironment();
        for (int i = 0; i < pairs.length; i += 2) {
            env.setProperty(pairs[i], pairs[i + 1]);
        }
        return new RepositoriesEnvironment(env);
    }

    @Test
    public void should_default_to_enabled_when_nothing_is_set() {
        assertTrue(isLiquibaseEnabled(environment(), PREFIX));
    }

    @Test
    public void should_follow_global_flag_when_no_per_scope_override() {
        assertTrue(isLiquibaseEnabled(environment("liquibase.enabled", "true"), PREFIX));
        assertFalse(isLiquibaseEnabled(environment("liquibase.enabled", "false"), PREFIX));
    }

    @Test
    public void per_scope_true_should_override_global_false() {
        // the gateway upgrader case: global stays off, but the gateway-owned scope is migrated
        assertTrue(isLiquibaseEnabled(
                environment("liquibase.enabled", "false", PREFIX + "liquibase.enabled", "true"), PREFIX));
    }

    @Test
    public void per_scope_false_should_override_global_true() {
        assertFalse(isLiquibaseEnabled(
                environment("liquibase.enabled", "true", PREFIX + "liquibase.enabled", "false"), PREFIX));
    }

    @Test
    public void per_scope_override_should_be_isolated_to_its_own_scope() {
        // enabling oauth2 must not enable a different scope that has no override and an off global default
        RepositoriesEnvironment env = environment(
                "liquibase.enabled", "false",
                PREFIX + "liquibase.enabled", "true");
        assertTrue(isLiquibaseEnabled(env, PREFIX));
        assertFalse(isLiquibaseEnabled(env, "repositories.management.jdbc."));
    }
}
