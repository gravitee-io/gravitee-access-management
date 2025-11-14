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
package io.gravitee.am.service.secrets.evaluators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import io.gravitee.am.service.secrets.resolver.SecretResolver;
import io.gravitee.el.spel.context.SecuredResolver;
import io.gravitee.secrets.api.core.Secret;
import io.gravitee.secrets.api.core.SecretURL;
import io.gravitee.secrets.api.errors.ServiceSecretsException;
import io.reactivex.rxjava3.core.Single;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class SecretsResolvingPluginConfigurationEvaluatorTest {

    @Mock
    private SecretResolver secretResolver;

    @InjectMocks
    private SecretsResolvingPluginConfigurationEvaluator cut;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEST_CONFIGURATION = """
        {
            "inClearProperty" : "inClearValue",
            "secretProperty" : "{#secrets.get('/testProvider/testSecret', 'testKey')}",
            "embedded" : {
                "inClearProperty" : "inClearValue",
                "secretProperty" : "{#secrets.get('/testProvider/testSecret:testKey')}"
            }
        }
    """;

    private final SecretURL secretUrl = new SecretURL("testProvider", "testSecret", "testKey", ArrayListMultimap.create(), true);

    @BeforeAll
    public static void init() {
        SecuredResolver.initialize(null);
    }

    @Test
    public void shouldEvaluateAnnotatedFields() throws JsonProcessingException {
        when(secretResolver.resolveSecretFromUrl(secretUrl)).thenReturn(Single.just(new Secret("secretValue")));

        TestConfiguration configuration = objectMapper.readValue(TEST_CONFIGURATION, TestConfiguration.class);
        cut.evaluate(configuration);

        assertThat(configuration.inClearProperty).isEqualTo("inClearValue");
        assertThat(configuration.secretProperty).isEqualTo("secretValue");

        assertThat(configuration.embedded.inClearProperty).isEqualTo("inClearValue");
        assertThat(configuration.embedded.secretProperty).isEqualTo("secretValue");
    }

    @Test
    public void shouldKeepExistingValueIfEvaluationFails() throws JsonProcessingException {
        when(secretResolver.resolveSecretFromUrl(secretUrl)).thenReturn(Single.error(new ServiceSecretsException("Simulated error")));

        TestConfiguration configuration = objectMapper.readValue(TEST_CONFIGURATION, TestConfiguration.class);
        cut.evaluate(configuration);

        assertThat(configuration.inClearProperty).isEqualTo("inClearValue");
        assertThat(configuration.secretProperty).isEqualTo("{#secrets.get('/testProvider/testSecret', 'testKey')}");

        assertThat(configuration.embedded.inClearProperty).isEqualTo("inClearValue");
        assertThat(configuration.embedded.secretProperty).isEqualTo("{#secrets.get('/testProvider/testSecret:testKey')}");
    }

    @NoArgsConstructor
    @Data
    private static class TestConfiguration {
        String inClearProperty;
        @io.gravitee.secrets.api.annotation.Secret String secretProperty;
        TestConfiguration embedded;
    }
}
