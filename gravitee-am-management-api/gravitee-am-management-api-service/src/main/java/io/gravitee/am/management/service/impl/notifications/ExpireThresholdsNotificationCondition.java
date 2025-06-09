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
package io.gravitee.am.management.service.impl.notifications;

import io.gravitee.node.api.notifier.NotificationCondition;
import io.gravitee.node.api.notifier.NotificationDefinition;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.getExpirationDate;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExpireThresholdsNotificationCondition implements NotificationCondition {

    private final List<Integer> expiryThresholds;

    public ExpireThresholdsNotificationCondition(List<Integer> expiryThresholds) {
        if (Objects.isNull(expiryThresholds) || expiryThresholds.isEmpty()) {
            throw new IllegalArgumentException("expiryThresholds requires at least one entry");
        }
        this.expiryThresholds = expiryThresholds;
    }

    @Override
    public boolean test(NotificationDefinition def) {
        return getExpirationDate(def)
                .map(expDate ->  expDate.getTime() < Instant.now().plus(expiryThresholds.get(0), ChronoUnit.DAYS).toEpochMilli())
                .orElse(false);
    }
}
