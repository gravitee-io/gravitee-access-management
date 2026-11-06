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
package io.gravitee.am.gateway.handler.common.ruleengine;

import io.gravitee.am.gateway.handler.context.EvaluableExecutionContext;
import io.gravitee.risk.assessment.api.assessment.Assessment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpELRuleEngineTest {

    private static final String PROBE_PROPERTY = "am.spel.rce.probe";

    private final SpELRuleEngine ruleEngine = new SpELRuleEngine();

    @Test
    void should_not_execute_java_code_embedded_in_a_rule() {
        // Reproduces the RCE: a rule must not be able to reach arbitrary Java types.
        var rule = "T(java.lang.System).setProperty('" + PROBE_PROPERTY + "', 'executed') != null";
        try {
            var result = ruleEngine.evaluate(rule, Map.of(), Boolean.class, false);

            assertFalse(result);
            assertNull(System.getProperty(PROBE_PROPERTY), "the rule executed arbitrary Java code");
        } finally {
            System.clearProperty(PROBE_PROPERTY);
        }
    }

    @Test
    void should_evaluate_rules_on_context_variables() {
        var parameters = Map.<String, Object>of(
                "context", new EvaluableExecutionContext(Map.of("geoip", Map.of("country_iso_code", "FR"))));

        assertTrue(ruleEngine.evaluate("#context.attributes['geoip']['country_iso_code'] == 'FR'", parameters, Boolean.class, false));
        assertFalse(ruleEngine.evaluate("#context.attributes['geoip']['country_iso_code'] == 'US'", parameters, Boolean.class, false));
    }

    @Test
    void should_evaluate_riskbased_rules_on_context_variables() {
        var parameters = Map.<String, Object>of(
                "context", new EvaluableExecutionContext(Map.of("risk_assessment", Map.of("type", Assessment.SAFE))));

        assertTrue(ruleEngine.evaluate("{#context.attributes['risk_assessment']['type'].name() == 'SAFE'}", parameters, Boolean.class, false));
    }
}
