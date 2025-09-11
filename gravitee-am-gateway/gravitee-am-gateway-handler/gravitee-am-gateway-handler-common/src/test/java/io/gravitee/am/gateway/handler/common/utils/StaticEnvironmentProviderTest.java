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

import org.junit.BeforeClass;
import org.springframework.core.env.Environment;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

public class StaticEnvironmentProviderTest {

    static Environment env = mock(Environment.class);

    @BeforeClass
    public static void setup() {
        when(env.getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_SANITIZE_PARAMETERS_ENCODING, boolean.class, true))
                .thenReturn(false)
                .thenReturn(true);
        StaticEnvironmentProvider.setEnvironment(env);
    }

    @Test
    public void sanitizeParametersEncoding_environment_returns_cached_value() {
        assertFalse(StaticEnvironmentProvider.sanitizeParametersEncoding());

        // Call method twice to test cached value is used
        assertFalse(StaticEnvironmentProvider.sanitizeParametersEncoding());
        verify(env).getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_SANITIZE_PARAMETERS_ENCODING, boolean.class, true);
    }
}