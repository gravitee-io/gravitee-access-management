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
package io.gravitee.am.common.env;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
public class RepositoriesEnvironmentTest {

    @Mock
    private Environment environment;

    @InjectMocks
    private RepositoriesEnvironment repositoriesEnvironment;

    @Test
    public void regular_variable_should_be_retrieved_from_regular_environment_provider(){
        Mockito.when(environment.getProperty("key")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("key");
        Assertions.assertEquals("value", value);
    }

    @Test
    public void regular_variable_should_be_retrieved_from_regular_environment_provider_2(){
        Mockito.when(environment.getProperty("key")).thenReturn("value");
        Assertions.assertEquals("value", repositoriesEnvironment.getProperty("key", "default"));
        Assertions.assertEquals("default", repositoriesEnvironment.getProperty("other", "default"));
    }

    @Test
    public void regular_typed_variable_should_be_retrieved_from_regular_environment_provider(){
        Mockito.when(environment.getProperty("key", String.class)).thenReturn("value");
        Assertions.assertEquals("value", repositoriesEnvironment.getProperty("key", String.class));
    }

    @Test
    public void regular_typed_variable_should_be_retrieved_from_regular_environment_provider_2(){
        Mockito.when(environment.getProperty("key", String.class)).thenReturn("value");
        Assertions.assertEquals("value", repositoriesEnvironment.getProperty("key", String.class, "default"));
        Assertions.assertEquals("default", repositoriesEnvironment.getProperty("other", String.class, "default"));
    }

    @Test
    public void repositories_gateway_variable_should_be_retrieved_if_its_present(){
        Mockito.when(environment.getProperty("repositories.gateway.url")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("repositories.gateway.url");
        Assertions.assertEquals("value", value);
    }

    @Test
    public void repositories_oauth2_variable_should_be_retrieved_if_its_present(){
        Mockito.when(environment.getProperty("repositories.oauth2.url")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("repositories.oauth2.url");
        Assertions.assertEquals("value", value);
    }

    @Test
    public void repositories_management_variable_should_be_retrieved_if_its_present(){
        Mockito.when(environment.getProperty("repositories.management.url")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("repositories.management.url");
        Assertions.assertEquals("value", value);
    }

    @Test
    public void repositories_gateway_variable_should_fallback_to_oauth2_if_its_missing(){
        Mockito.when(environment.getProperty("repositories.gateway.url")).thenReturn(null);
        Mockito.when(environment.getProperty("oauth2.url")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("repositories.gateway.url");
        Assertions.assertEquals("value", value);
    }

    @Test
    public void repositories_oauth2_variable_should_fallback_to_oauth2_if_its_missing(){
        Mockito.when(environment.getProperty("repositories.oauth2.url")).thenReturn(null);
        Mockito.when(environment.getProperty("oauth2.url")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("repositories.oauth2.url");
        Assertions.assertEquals("value", value);
    }

    @Test
    public void repositories_management_variable_should_fallback_to_oauth2_if_its_missing(){
        Mockito.when(environment.getProperty("repositories.management.url")).thenReturn(null);
        Mockito.when(environment.getProperty("management.url")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("repositories.management.url");
        Assertions.assertEquals("value", value);
    }

    @Test
    public void repositories_gateway_variable_and_fallback_is_missing_should_return_default(){
        Mockito.when(environment.getProperty("repositories.gateway.url")).thenReturn(null);
        Mockito.when(environment.getProperty("oauth2.url", "default")).thenReturn("default");
        String value = repositoriesEnvironment.getProperty("repositories.gateway.url", "default");
        Assertions.assertEquals("default", value);
    }

    @Test
    public void repositories_management_variable_and_fallback_is_missing_should_return_default(){
        Mockito.when(environment.getProperty("repositories.management.url")).thenReturn(null);
        Mockito.when(environment.getProperty("management.url", "default")).thenReturn("default");
        String value = repositoriesEnvironment.getProperty("repositories.management.url", "default");
        Assertions.assertEquals("default", value);
    }

    @Test
    public void repositories_oauth2_variable_and_fallback_is_missing_should_return_default(){
        Mockito.when(environment.getProperty("repositories.oauth2.url")).thenReturn(null);
        Mockito.when(environment.getProperty("oauth2.url", "default")).thenReturn("default");
        String value = repositoriesEnvironment.getProperty("repositories.oauth2.url", "default");
        Assertions.assertEquals("default", value);
    }

    @Test
    public void repositories_ratelimit_variable_and_fallback_is_missing_should_return_default(){
        Mockito.when(environment.getProperty("repositories.ratelimit.url")).thenReturn(null);
        Mockito.when(environment.getProperty("repositories.gateway.url", "default")).thenReturn("default");
        String value = repositoriesEnvironment.getProperty("repositories.ratelimit.url", "default");
        Assertions.assertEquals("default", value);
    }

    @Test
    public void repositories_ratelimit_variable_should_be_retrieved_if_its_present(){
        Mockito.when(environment.getProperty("repositories.ratelimit.url")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("repositories.ratelimit.url");
        Assertions.assertEquals("value", value);
    }

    @Test
    public void repositories_ratelimit_variable_should_fallback_to_repositories_gateway_if_its_missing(){
        Mockito.when(environment.getProperty("repositories.ratelimit.url")).thenReturn(null);
        Mockito.when(environment.getProperty("repositories.gateway.url")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("repositories.ratelimit.url");
        Assertions.assertEquals("value", value);
    }

    @Test
    public void repositories_ratelimit_variable_should_chain_to_oauth2_if_gateway_is_missing(){
        Mockito.when(environment.getProperty("repositories.ratelimit.url")).thenReturn(null);
        Mockito.when(environment.getProperty("repositories.gateway.url")).thenReturn(null);
        Mockito.when(environment.getProperty("oauth2.url")).thenReturn("value");
        String value = repositoriesEnvironment.getProperty("repositories.ratelimit.url");
        Assertions.assertEquals("value", value);
    }
}