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

import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.am.management.service.alerts.risk.GeoVelocityAlert;
import io.gravitee.am.management.service.alerts.risk.IpReputationAlert;
import io.gravitee.am.management.service.alerts.risk.UnknownDeviceAlert;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.notifier.api.Notification;
import org.springframework.core.env.Environment;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class AlertTriggerFactory {

    public static final String AUTHENTICATION_SOURCE = "AUTHENTICATION";

    /**
     * Create an AE {@link Trigger} for the given {@link AlertTrigger} and list of {@link AlertNotifier}.
     *
     * @param alertTrigger the alert trigger to convert into AE trigger.
     * @param alertNotifiers the list of alert notifiers to add to the trigger.
     * @param environment environment properties that can be use to tune the alert trigger.
     * @return the corresponding {@link Trigger}.
     */
    public static List<Trigger> create(AlertTrigger alertTrigger, List<AlertNotifier> alertNotifiers, Environment environment) {
        var triggers = getTrigger(alertTrigger, environment);

        for (Trigger trigger : triggers) {
            if (alertNotifiers != null && !alertNotifiers.isEmpty()) {
                var notifications = alertNotifiers.stream().map(AlertTriggerFactory::convert).collect(toList());
                trigger.setNotifications(notifications);
            }

            trigger.setEnabled(alertTrigger.isEnabled());
        }
        return triggers;
    }

    private static Notification convert(AlertNotifier alertNotifier) {
        final Notification notification = new Notification();
        notification.setType(alertNotifier.getType());
        notification.setConfiguration(alertNotifier.getConfiguration());
        return notification;
    }

    private static List<Trigger> getTrigger(AlertTrigger alertTrigger, Environment environment) {
        switch (alertTrigger.getType()) {
            case TOO_MANY_LOGIN_FAILURES:
                return List.of(new TooManyLoginFailuresAlert(alertTrigger, environment));
            case RISK_ASSESSMENT:
                return List.of(
                        new UnknownDeviceAlert(alertTrigger, environment),
                        new IpReputationAlert(alertTrigger, environment),
                        new GeoVelocityAlert(alertTrigger, environment)
                );
            default:
                throw new TechnicalManagementException(String.format("Unable to create trigger of type %s", alertTrigger.getType()));
        }
    }
}
