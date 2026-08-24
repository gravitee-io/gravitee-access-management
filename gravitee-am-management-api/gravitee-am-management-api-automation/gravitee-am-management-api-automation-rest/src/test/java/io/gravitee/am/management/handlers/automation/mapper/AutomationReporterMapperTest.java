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
package io.gravitee.am.management.handlers.automation.mapper;

import io.gravitee.am.management.handlers.automation.model.AutomationReporter;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.ReporterAttributeMapping;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class AutomationReporterMapperTest {

    private static final List<ReporterAttributeMapping> MAPPINGS = List.of(
            new ReporterAttributeMapping("{#context.attributes['user'].additionalInformation['sub']}", "user_sub"),
            new ReporterAttributeMapping("{#context.attributes['client'].clientId}", "client_id"));

    private static AutomationReporter definition(List<ReporterAttributeMapping> attributeMappings) {
        AutomationReporter definition = new AutomationReporter();
        definition.setAutomationKey("audit-kafka");
        definition.setName("Audit events to Kafka");
        definition.setType("reporter-am-kafka");
        definition.setConfiguration("{}");
        definition.setEnabled(true);
        definition.setAttributeMappings(attributeMappings);
        return definition;
    }

    @Test
    void exposesAttributeMappingsOnTheAutomationProjection() {
        var reporter = Reporter.builder()
                .automationKey("audit-kafka")
                .name("Audit events to Kafka")
                .type("reporter-am-kafka")
                .configuration("{}")
                .enabled(true)
                .attributeMappings(MAPPINGS)
                .build();

        assertThat(AutomationReporterMapper.toAutomationReporter(reporter).getAttributeMappings())
                .isEqualTo(MAPPINGS);
    }

    @Test
    void carriesAttributeMappingsOntoTheCreatePayload() {
        assertThat(AutomationReporterMapper.toNewReporter(definition(MAPPINGS)).getAttributeMappings())
                .isEqualTo(MAPPINGS);
    }

    @Test
    void carriesAttributeMappingsOntoTheUpdatePayload() {
        assertThat(AutomationReporterMapper.toUpdateReporter(definition(MAPPINGS)).getAttributeMappings())
                .isEqualTo(MAPPINGS);
    }

    @Test
    void roundTripsAReporterThatDeclaresNoMappings() {
        var reporter = Reporter.builder().name("r").type("reporter-am-kafka").configuration("{}").build();

        assertThat(AutomationReporterMapper.toAutomationReporter(reporter).getAttributeMappings()).isNull();
        assertThat(AutomationReporterMapper.toNewReporter(definition(null)).getAttributeMappings()).isNull();
        assertThat(AutomationReporterMapper.toUpdateReporter(definition(null)).getAttributeMappings()).isNull();
    }
}
