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

import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.safe.CertificateProperties;
import io.gravitee.node.api.notifier.NotificationDefinition;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateNotificationConditionTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectEmptyList(){
        new CertificateNotificationCondition(Collections.emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullArg(){
        new CertificateNotificationCondition(null);
    }

    @Test
    public void shouldReturnTrue_FirstThresholdReached() {
        NotificationDefinition def = new NotificationDefinition();
        final Certificate certificate = new Certificate();
        certificate.setExpiresAt(new Date(Instant.now().plus(10, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES).toEpochMilli()));
        def.setData(Map.of(NotificationDefinitionUtils.NOTIFIER_DATA_CERTIFICATE, new CertificateProperties(certificate)));
        Assert.assertTrue(new CertificateNotificationCondition(List.of(10,2)).test(def));
    }

    @Test
    public void shouldReturnFalse_FirstThresholdNotReached() {
        NotificationDefinition def = new NotificationDefinition();
        final Certificate certificate = new Certificate();
        certificate.setExpiresAt(new Date(Instant.now().plus(11, ChronoUnit.DAYS).toEpochMilli()));
        def.setData(Map.of(NotificationDefinitionUtils.NOTIFIER_DATA_CERTIFICATE, new CertificateProperties(certificate)));
        Assert.assertFalse(new CertificateNotificationCondition(List.of(10,2)).test(def));
    }
}
