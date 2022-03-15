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
import io.gravitee.node.api.notifier.NotificationAcknowledge;
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
public class CertificateResendNotificationConditionTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectEmptyList(){
        new CertificateResendNotificationCondition(Collections.emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullArg(){
        new CertificateResendNotificationCondition(null);
    }

    @Test
    public void shouldReturnFalse_FirstThresholdReached_ButNotTheSecondOne() {
        NotificationDefinition def = new NotificationDefinition();
        final Certificate certificate = new Certificate();
        final Instant now = Instant.now();
        certificate.setExpiresAt(new Date(now.plus(13, ChronoUnit.DAYS).toEpochMilli()));
        def.setData(Map.of(NotificationDefinitionUtils.NOTIFIER_DATA_CERTIFICATE, new CertificateProperties(certificate)));

        final NotificationAcknowledge notificationAcknowledge = new NotificationAcknowledge();
        notificationAcknowledge.setUpdatedAt(new Date(now.minus(12, ChronoUnit.DAYS).toEpochMilli()));

        Assert.assertFalse(new CertificateResendNotificationCondition(List.of(25,10,2)).apply(def, notificationAcknowledge));
    }

    @Test
    public void shouldReturnTrue_FirstAndSecondThresholdReached_ThirdThresholdWillExpire() {
        NotificationDefinition def = new NotificationDefinition();
        final Certificate certificate = new Certificate();
        final Instant now = Instant.now();
        certificate.setExpiresAt(new Date(now.plus(2, ChronoUnit.DAYS).toEpochMilli()));
        def.setData(Map.of(NotificationDefinitionUtils.NOTIFIER_DATA_CERTIFICATE, new CertificateProperties(certificate)));

        final NotificationAcknowledge notificationAcknowledge = new NotificationAcknowledge();
        notificationAcknowledge.setUpdatedAt(new Date(now.minus(8, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        final CertificateResendNotificationCondition resendCondition = new CertificateResendNotificationCondition(List.of(25, 10, 2));
        Assert.assertTrue(resendCondition.apply(def, notificationAcknowledge));
    }

    @Test
    public void shouldReturnTrue_FirstAndSecondThresholdReached_ThirdThresholdNotExpired() {
        NotificationDefinition def = new NotificationDefinition();
        final Certificate certificate = new Certificate();
        final Instant now = Instant.now();
        certificate.setExpiresAt(new Date(now.plus(4, ChronoUnit.DAYS).toEpochMilli()));
        def.setData(Map.of(NotificationDefinitionUtils.NOTIFIER_DATA_CERTIFICATE, new CertificateProperties(certificate)));

        final NotificationAcknowledge notificationAcknowledge = new NotificationAcknowledge();
        notificationAcknowledge.setUpdatedAt(new Date(now.minus(6, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        final CertificateResendNotificationCondition resendCondition = new CertificateResendNotificationCondition(List.of(25, 10, 2));
        Assert.assertFalse(resendCondition.apply(def, notificationAcknowledge));
    }

    @Test
    public void shouldReturnTrue_ThirdThresholdReached() {
        NotificationDefinition def = new NotificationDefinition();
        final Certificate certificate = new Certificate();
        final Instant now = Instant.now();
        certificate.setExpiresAt(new Date(now.plus(1, ChronoUnit.DAYS).toEpochMilli()));
        def.setData(Map.of(NotificationDefinitionUtils.NOTIFIER_DATA_CERTIFICATE, new CertificateProperties(certificate)));

        final NotificationAcknowledge notificationAcknowledge = new NotificationAcknowledge();
        notificationAcknowledge.setUpdatedAt(new Date(now.minus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.MINUTES).toEpochMilli()));

        final CertificateResendNotificationCondition resendCondition = new CertificateResendNotificationCondition(List.of(25, 10, 2));
        Assert.assertFalse(resendCondition.apply(def, notificationAcknowledge));
    }
}
