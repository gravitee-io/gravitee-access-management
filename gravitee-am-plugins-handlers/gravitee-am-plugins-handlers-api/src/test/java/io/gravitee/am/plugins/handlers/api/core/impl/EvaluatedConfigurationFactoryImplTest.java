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
package io.gravitee.am.plugins.handlers.api.core.impl;

import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationEvaluator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class EvaluatedConfigurationFactoryImplTest {

    private final PluginConfigurationEvaluator evaluator1 = new TokenReplacingEvaluator("#TOKEN1", "evaluatedToken1");
    private final PluginConfigurationEvaluator evaluator2 = new TokenReplacingEvaluator("#TOKEN2", "evaluatedToken2");

    private final ConfigurationFactory<TestConfiguration> cut = new EvaluatedConfigurationFactoryImpl<>(
        List.of(evaluator1, evaluator2)
    );

    @Test
    public void shouldEvaluateOriginalConfig() {
        String originalPayload = """
            {
                "property" : "#TOKEN1, #TOKEN2"
            }
        """;

        TestConfiguration evaluatedConfig = cut.create(TestConfiguration.class, originalPayload);
        assertThat(evaluatedConfig).isNotNull();
        assertThat(evaluatedConfig.property).isEqualTo("evaluatedToken1, evaluatedToken2");
    }

    @Data
    private static class TestConfiguration {
        private String property;
    }

    @RequiredArgsConstructor
    private static class TokenReplacingEvaluator implements PluginConfigurationEvaluator {
        private final String token;
        private final String value;

        @Override
        public <T> void evaluate(T configuration) {
            switch (configuration) {
                case TestConfiguration config -> config.setProperty(config.property.replace(token, value));
                default -> throw new IllegalStateException("Unexpected config type: " + configuration.getClass());
            }
        }
    }
}
