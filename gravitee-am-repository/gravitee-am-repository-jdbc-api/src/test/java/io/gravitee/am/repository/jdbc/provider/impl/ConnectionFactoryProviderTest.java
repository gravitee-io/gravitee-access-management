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
package io.gravitee.am.repository.jdbc.provider.impl;

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.r2dbc.pool.PoolingConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import io.r2dbc.spi.ValidationDepth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConnectionFactoryProviderTest {

    private static final String PREFIX = "repositories.gateway";
    private static final String JDBC_PREFIX = PREFIX + ".jdbc.";

    @Mock
    private RepositoriesEnvironment environment;

    private ConnectionFactoryProvider cut;

    @BeforeEach
    void setUp() {
        // simulate Spring Environment#getProperty(key, default) returning the supplied default
        // for every property that isn't explicitly stubbed by a test
        when(environment.getProperty(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(environment.getProperty(JDBC_PREFIX + "driver")).thenReturn("postgresql");
        when(environment.getProperty(JDBC_PREFIX + "host")).thenReturn("localhost");
        when(environment.getProperty(JDBC_PREFIX + "username")).thenReturn("am-user");
        when(environment.getProperty(JDBC_PREFIX + "database")).thenReturn("am-db");

        cut = new ConnectionFactoryProvider(environment, PREFIX);
    }

    @Test
    void should_apply_recovery_defaults_when_nothing_is_overridden() {
        ConnectionFactoryOptions options = cut.buildConnectionFactoryOptions();

        assertThat(options.getValue(PoolingConnectionFactoryProvider.MAX_LIFE_TIME))
                .as("maxLifeTime must default to a finite value so dead connections eventually get recycled")
                .isEqualTo(Duration.ofMillis(ConnectionFactoryProvider.DEFAULT_SETTINGS_MAX_LIFE_TIME));

        assertThat((Boolean) options.getValue(Option.valueOf(ConnectionFactoryProvider.TAG_TCP_KEEP_ALIVE)))
                .as("tcpKeepAlive must default to true")
                .isTrue();

        assertThat(options.getValue(PoolingConnectionFactoryProvider.VALIDATION_DEPTH))
                .isEqualTo(ValidationDepth.LOCAL);
        assertThat(options.hasOption(PoolingConnectionFactoryProvider.MAX_VALIDATION_TIME))
                .as("maxValidationTime is only relevant when a validationQuery is configured")
                .isFalse();
    }

    @Test
    void should_apply_max_validation_time_when_validation_query_is_set() {
        when(environment.getProperty(JDBC_PREFIX + "validationQuery")).thenReturn("SELECT 1");

        ConnectionFactoryOptions options = cut.buildConnectionFactoryOptions();

        assertThat(options.getValue(PoolingConnectionFactoryProvider.VALIDATION_DEPTH)).isEqualTo(ValidationDepth.REMOTE);
        assertThat(options.getValue(PoolingConnectionFactoryProvider.MAX_VALIDATION_TIME))
                .as("a hanging validation query must be bounded, otherwise it can loop on one dead connection")
                .isEqualTo(Duration.ofMillis(ConnectionFactoryProvider.DEFAULT_SETTINGS_MAX_VALIDATION_TIME));
    }

    @Test
    void should_use_configured_max_validation_time_when_provided() {
        when(environment.getProperty(JDBC_PREFIX + "validationQuery")).thenReturn("SELECT 1");
        when(environment.getProperty(JDBC_PREFIX + "maxValidationTime", "" + ConnectionFactoryProvider.DEFAULT_SETTINGS_MAX_VALIDATION_TIME))
                .thenReturn("9000");

        ConnectionFactoryOptions options = cut.buildConnectionFactoryOptions();

        assertThat(options.getValue(PoolingConnectionFactoryProvider.MAX_VALIDATION_TIME)).isEqualTo(Duration.ofMillis(9000));
    }

    @Test
    void should_disable_tcp_keep_alive_when_configured() {
        when(environment.getProperty(JDBC_PREFIX + "tcpKeepAlive", "" + ConnectionFactoryProvider.DEFAULT_SETTINGS_TCP_KEEP_ALIVE))
                .thenReturn("false");

        ConnectionFactoryOptions options = cut.buildConnectionFactoryOptions();

        assertThat((Boolean) options.getValue(Option.valueOf(ConnectionFactoryProvider.TAG_TCP_KEEP_ALIVE))).isFalse();
    }

    @Test
    void should_use_configured_max_life_time_when_provided() {
        when(environment.getProperty(JDBC_PREFIX + "maxLifeTime", "" + ConnectionFactoryProvider.DEFAULT_SETTINGS_MAX_LIFE_TIME))
                .thenReturn("60000");

        ConnectionFactoryOptions options = cut.buildConnectionFactoryOptions();

        assertThat(options.getValue(PoolingConnectionFactoryProvider.MAX_LIFE_TIME)).isEqualTo(Duration.ofMillis(60000));
    }
}
