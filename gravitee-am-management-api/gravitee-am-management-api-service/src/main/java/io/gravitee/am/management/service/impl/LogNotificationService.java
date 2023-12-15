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

import io.gravitee.am.model.safe.CertificateProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.api.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class LogNotificationService implements Notifier {
    public static final String CERTIFICATE = "certificate";
    public static final String DOMAIN = "domain";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public CompletableFuture<Void> send(Notification notification, Map<String, Object> map) {
        final CertificateProperties certificate = (CertificateProperties) map.get(CERTIFICATE);
        final String domainName = ((DomainProperties) map.get(DOMAIN)).getName();
        final Date expiredDate = certificate.getExpiresAt();
        final Instant now = Instant.now();

        if (now.isAfter(expiredDate.toInstant())) {
            logger.warn("Certificate '{}' of domain '{}' expired on '{}'.", certificate.getName(), domainName, expiredDate);
        } else {
            logger.warn("Certificate '{}' of domain '{}' is expiring on '{}'.", certificate.getName(), domainName, expiredDate);
        }

        return CompletableFuture.completedFuture(null);
    }
}
