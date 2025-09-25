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
package io.gravitee.am.gateway.handler.common.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.core.env.Environment;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class StaticEnvironmentProviderTest {

    static Environment env = mock(Environment.class);
    static Environment originalEnv;

    @BeforeClass
    public static void setup() {
        // Save the original environment before any tests run
        originalEnv = StaticEnvironmentProvider.getEnvironment();
    }

    @Before
    public void setUp() {
        // Reset mock to clear any previous interactions from other test classes
        reset(env);
        // Re-setup mock behavior
        when(env.getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_SANITIZE_PARAMETERS_ENCODING, boolean.class, true))
                .thenReturn(false);
        when(env.getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_INCLUDE_DEFAULT_HOST_PORTS, boolean.class, false))
                .thenReturn(true);
        // Reset cache before each test
        StaticEnvironmentProvider.setEnvironment(env);
    }

    @After
    public void tearDown() {
        // Restore the original environment after each test to prevent interference
        StaticEnvironmentProvider.setEnvironment(originalEnv);
    }

    @Test
    public void sanitizeParametersEncoding_environment_returns_cached_value() {
        assertFalse(StaticEnvironmentProvider.sanitizeParametersEncoding());

        // Call method twice to test cached value is used
        assertFalse(StaticEnvironmentProvider.sanitizeParametersEncoding());
        
        verify(env, times(1)).getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_SANITIZE_PARAMETERS_ENCODING, boolean.class, true);
    }

    @Test
    public void includeDefaultHttpHostHeaderPorts_environment_returns_cached_value() {
        // First call should get value from environment
        assertTrue(StaticEnvironmentProvider.includeDefaultHttpHostHeaderPorts());

        // Second call should use cached value (same result)
        assertTrue(StaticEnvironmentProvider.includeDefaultHttpHostHeaderPorts());
        
        verify(env, times(1)).getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_INCLUDE_DEFAULT_HOST_PORTS, boolean.class, false);
    }

    @Test
    public void includeDefaultHttpHostHeaderPorts_no_environment_returns_default() {
        // Clear environment to test default behavior
        StaticEnvironmentProvider.setEnvironment(null);
        
        assertFalse(StaticEnvironmentProvider.includeDefaultHttpHostHeaderPorts());
    }

    @Test
    public void setEnvironment_resets_cached_values() {
        // Create a new mock environment for this test
        Environment newEnv = mock(Environment.class);
        when(newEnv.getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_INCLUDE_DEFAULT_HOST_PORTS, boolean.class, false))
                .thenReturn(false);
        
        // First call should cache value
        assertTrue(StaticEnvironmentProvider.includeDefaultHttpHostHeaderPorts());
        
        // Set new environment to reset cache
        StaticEnvironmentProvider.setEnvironment(newEnv);
        
        // Next call should use new cached value (false from new mock)
        assertFalse(StaticEnvironmentProvider.includeDefaultHttpHostHeaderPorts());
        verify(env, times(1)).getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_INCLUDE_DEFAULT_HOST_PORTS, boolean.class, false);
    }
}