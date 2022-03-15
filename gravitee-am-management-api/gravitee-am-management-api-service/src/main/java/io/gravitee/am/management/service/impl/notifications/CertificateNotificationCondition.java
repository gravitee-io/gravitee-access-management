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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.getCertificateExpirationDate;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateNotificationCondition implements NotificationCondition {

    private List<Integer> certificateExpiryThresholds;

    public CertificateNotificationCondition(List<Integer> certificateExpiryThresholds) {
        if (Objects.isNull(certificateExpiryThresholds) || certificateExpiryThresholds.isEmpty()) {
            throw new IllegalArgumentException("certificateExpiryThresholds requires at least one entry");
        }
        this.certificateExpiryThresholds = certificateExpiryThresholds;
    }

    @Override
    public boolean test(NotificationDefinition def) {
        final Date certificateExpirationDate = getCertificateExpirationDate(def);
        if (certificateExpirationDate != null) {
            // for the first condition we only check the highest expiry value
            return certificateExpirationDate.getTime() < Instant.now().plus(certificateExpiryThresholds.get(0), ChronoUnit.DAYS).toEpochMilli();
        }
        return false;
    }
}
