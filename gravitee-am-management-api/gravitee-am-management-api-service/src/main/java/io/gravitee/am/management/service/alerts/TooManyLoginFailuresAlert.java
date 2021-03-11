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
package io.gravitee.am.management.service.alerts;

import io.gravitee.alert.api.condition.RateCondition;
import io.gravitee.alert.api.condition.StringCondition;
import io.gravitee.alert.api.trigger.Dampening;
import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.am.model.alert.AlertTrigger;
import org.springframework.core.env.Environment;

import java.util.concurrent.TimeUnit;

import static io.gravitee.am.management.service.alerts.AlertTriggerFactory.AUTHENTICATION_SOURCE;
import static java.util.Collections.singletonList;
import static io.gravitee.am.common.event.AlertEventKeys.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TooManyLoginFailuresAlert extends Trigger {

    private static final double DEFAULT_THRESHOLD = 10d;
    private static final int DEFAULT_SAMPLE_SIZE = 1000;
    private static final int DEFAULT_WINDOW_SECONDS = 600;
    private static final int DEFAULT_DAMPENING = 1;
    private static final String DEFAULT_NAME = "Too many login failures detected";
    private static final String DEFAULT_DESCRIPTION = "More than {threshold}% of logins are in failure over the last {window} second(s)";
    private static final String STATUS_FAILURE = "FAILURE";
    private static final Severity DEFAULT_SEVERITY = Severity.WARNING;

    protected TooManyLoginFailuresAlert(AlertTrigger alertTrigger, Environment environment) {

        super(alertTrigger.getId(), "Too many login failures detected", DEFAULT_SEVERITY, AUTHENTICATION_SOURCE, alertTrigger.isEnabled());
        final double threshold = environment.getProperty("alerts.too_many_login_failures.threshold", Double.class, DEFAULT_THRESHOLD);
        final int sampleSize = environment.getProperty("alerts.too_many_login_failures.sampleSize", Integer.class, DEFAULT_SAMPLE_SIZE);
        final int windowSeconds = environment.getProperty("alerts.too_many_login_failures.window", Integer.class, DEFAULT_WINDOW_SECONDS);
        final String name = environment.getProperty("alerts.too_many_login_failures.name", DEFAULT_NAME);
        final String description = environment.getProperty("alerts.too_many_login_failures.description", DEFAULT_DESCRIPTION).replace("{threshold}", "" + threshold).replace("{window}", "" + windowSeconds);
        final String severity = environment.getProperty("alerts.too_many_login_failures.severity", DEFAULT_SEVERITY.name());

        this.setName(name);
        this.setDescription(description);
        this.setSeverity(Severity.valueOf(severity));
        this.setConditions(singletonList(RateCondition
                .of(StringCondition.equals(PROPERTY_AUTHENTICATION_STATUS, STATUS_FAILURE).build())
                .duration(windowSeconds, TimeUnit.SECONDS)
                .greaterThanOrEquals(threshold)
                .sampleSize(sampleSize)
                .build()));

        // For now we only support alert at domain level.
        final StringCondition domainFilter = StringCondition.equals(PROPERTY_DOMAIN, alertTrigger.getReferenceId()).build();
        this.setFilters(singletonList(domainFilter));
        this.setDampening(Dampening.strictCount(DEFAULT_DAMPENING));
    }
}
