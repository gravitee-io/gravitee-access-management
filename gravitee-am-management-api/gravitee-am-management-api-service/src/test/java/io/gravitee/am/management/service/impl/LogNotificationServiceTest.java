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
package io.gravitee.am.management.service.impl;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.safe.CertificateProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.notifier.api.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class LogNotificationServiceTest {

    @Mock
    Certificate certificate;

    @Mock
    DomainProperties domainProperties;

    private LogNotificationService service;
    private ListAppender<ILoggingEvent> listAppender;
    private Notification notification;
    private Map<String, Object> map;

    @BeforeEach
    public void setUp() {
        when(certificate.getId()).thenReturn("any-id");
        when(certificate.getName()).thenReturn("TestCertificate");
        when(certificate.getType()).thenReturn("Domain");
        when(domainProperties.getName()).thenReturn("TestDomain");

        Logger logger = (Logger) LoggerFactory.getLogger(LogNotificationService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger .addAppender(listAppender);

        notification = new Notification();
        map = new HashMap<>();
        map.put("domain", domainProperties);

        service = new LogNotificationService();
    }

    @Test
    void log_expired_certificate() {
        when(certificate.getExpiresAt()).thenReturn(new Date());
        final CertificateProperties certificateProperties = new CertificateProperties(certificate);
        map.put("certificate", certificateProperties);

        service.send(notification, map);

        final ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertEquals("Certificate '{}' of domain '{}' expired on '{}'.", event.getMessage());
        assertEquals(3, event.getArgumentArray().length);
    }

    @Test
    void log_expiring_certificate() {
        final Instant instant = Instant.now().plus(1, ChronoUnit.DAYS);
        Date future = new Date(instant.toEpochMilli());
        when(certificate.getExpiresAt()).thenReturn(future);
        CertificateProperties certificateProperties = new CertificateProperties(certificate);
        map.put("certificate", certificateProperties);

        service.send(notification, map);

        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertEquals("Certificate '{}' of domain '{}' is expiring on '{}'.", event.getMessage());
        assertEquals(3, event.getArgumentArray().length);
    }
}
