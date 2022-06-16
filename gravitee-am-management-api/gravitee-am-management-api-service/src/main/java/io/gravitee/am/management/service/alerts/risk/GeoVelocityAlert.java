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
package io.gravitee.am.management.service.alerts.risk;

import io.gravitee.alert.api.condition.Condition;
import io.gravitee.alert.api.condition.StringCondition;
import io.gravitee.alert.api.trigger.Dampening;
import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.am.common.event.AlertEventKeys;
import io.gravitee.am.model.alert.AlertTrigger;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.core.env.Environment;

import static io.gravitee.alert.api.condition.StringCondition.matches;
import static io.gravitee.am.common.event.AlertEventKeys.*;
import static io.gravitee.am.management.service.alerts.AlertTriggerFactory.AUTHENTICATION_SOURCE;
import static io.gravitee.risk.assessment.api.assessment.Assessment.LOW;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * @author Michael CARTER (michael.carter at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GeoVelocityAlert extends RiskAssessmentAlert {

    private static final int DEFAULT_DAMPENING = 1;
    private static final String DEFAULT_NAME = "Geo velocity alert";
    private static final String DEFAULT_DESCRIPTION = "A geo velocity risk-based alert has been triggered";
    private static final Severity DEFAULT_SEVERITY = Severity.WARNING;

    private static final String ALERT_NAME_KEY = PROPERTY_ALERTS + PROPERTY_RISK_ASSESSMENT + "." + PROPERTY_GEO_VELOCITY + NAME_SUFFIX;
    private static final String ALERT_DESCRIPTION_KEY = PROPERTY_ALERTS + PROPERTY_RISK_ASSESSMENT + "." + PROPERTY_GEO_VELOCITY + DESCRIPTION_SUFFIX;
    private static final String ALERT_SEVERITY_KEY = PROPERTY_ALERTS + PROPERTY_RISK_ASSESSMENT + "." + PROPERTY_GEO_VELOCITY + SEVERITY_SUFFIX;

    public GeoVelocityAlert(AlertTrigger alertTrigger, Environment environment) {
        super(alertTrigger.getId(), DEFAULT_NAME, DEFAULT_SEVERITY, AUTHENTICATION_SOURCE, alertTrigger.isEnabled());

        final String name = environment.getProperty(ALERT_NAME_KEY, DEFAULT_NAME);
        final String description = environment.getProperty(ALERT_DESCRIPTION_KEY, DEFAULT_DESCRIPTION);

        this.setId(alertTrigger.getId() + "-" + this.getClass().getSimpleName());
        this.setName(name);
        this.setDescription(description);
        this.setSeverity(environment.getProperty(ALERT_SEVERITY_KEY, Severity.class, DEFAULT_SEVERITY));

        this.setConditions(singletonList(getCondition(environment, PROPERTY_GEO_VELOCITY, LOW.name())));

        final StringCondition domainFilter = StringCondition.equals(PROPERTY_DOMAIN, alertTrigger.getReferenceId()).build();
        this.setFilters(singletonList(domainFilter));

        this.setDampening(Dampening.strictCount(DEFAULT_DAMPENING));
    }
}
