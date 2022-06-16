/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.management.service.alerts;

import io.gravitee.alert.api.condition.StringCondition;
import io.gravitee.alert.api.trigger.Dampening;
import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.am.model.alert.AlertTrigger;
import org.springframework.core.env.Environment;

import static io.gravitee.am.common.event.AlertEventKeys.PROPERTY_DOMAIN;
import static io.gravitee.am.management.service.alerts.AlertTriggerFactory.AUTHENTICATION_SOURCE;
import static java.util.Collections.singletonList;

/**
 * Risk assessment alert.
 *
 * @author GraviteeSource Team
 */
public class RiskAssessmentAlert extends Trigger {

    private static final int DEFAULT_DAMPENING = 1;
    private static final String DEFAULT_NAME = "Risk assessment";
    private static final String DEFAULT_DESCRIPTION = "A risk assessment alert has been triggered";
    private static final Severity DEFAULT_SEVERITY = Severity.INFO;

    protected RiskAssessmentAlert(AlertTrigger alertTrigger, Environment environment) {
        super(alertTrigger.getId(), DEFAULT_NAME, DEFAULT_SEVERITY, AUTHENTICATION_SOURCE, alertTrigger.isEnabled());
        final String severity = environment.getProperty("alerts.risk_assessment.severity", DEFAULT_SEVERITY.name());
        this.setName(DEFAULT_NAME);
        this.setDescription(DEFAULT_DESCRIPTION);
        this.setSeverity(Severity.valueOf(severity));
        final StringCondition domainFilter = StringCondition.equals(PROPERTY_DOMAIN, alertTrigger.getReferenceId()).build();
        this.setFilters(singletonList(domainFilter));
        this.setDampening(Dampening.strictCount(DEFAULT_DAMPENING));
    }
}
