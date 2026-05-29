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
import io.gravitee.am.service.model.AutomationNewReporter;
import io.gravitee.am.service.model.UpdateReporter;

/**
 * Maps between the shared {@link Reporter} model and the {@link AutomationReporter} projection.
 *
 * @author GraviteeSource Team
 */
public final class AutomationReporterMapper {

    private AutomationReporterMapper() {
    }

    public static AutomationReporter toAutomationReporter(Reporter reporter) {
        AutomationReporter out = new AutomationReporter();
        out.setAutomationKey(reporter.getAutomationKey());
        out.setName(reporter.getName());
        out.setType(reporter.getType());
        out.setConfiguration(reporter.getConfiguration());
        out.setEnabled(reporter.isEnabled());
        out.setSystem(reporter.isSystem());
        out.setDataType(reporter.getDataType());
        out.setCreatedAt(reporter.getCreatedAt());
        out.setUpdatedAt(reporter.getUpdatedAt());
        return out;
    }

    /**
     * Build the create payload for a declared reporter. The deterministic id is set by the caller
     * (the upsert path) before the reporter is persisted.
     */
    public static AutomationNewReporter toNewReporter(AutomationReporter definition) {
        AutomationNewReporter newReporter = new AutomationNewReporter();
        newReporter.setAutomationKey(definition.getAutomationKey());
        newReporter.setName(definition.getName());
        newReporter.setType(definition.getType());
        newReporter.setConfiguration(definition.getConfiguration());
        newReporter.setEnabled(definition.isEnabled());
        return newReporter;
    }

    public static UpdateReporter toUpdateReporter(AutomationReporter definition) {
        UpdateReporter updateReporter = new UpdateReporter();
        updateReporter.setName(definition.getName());
        updateReporter.setType(definition.getType());
        updateReporter.setConfiguration(definition.getConfiguration());
        updateReporter.setEnabled(definition.isEnabled());
        return updateReporter;
    }
}
