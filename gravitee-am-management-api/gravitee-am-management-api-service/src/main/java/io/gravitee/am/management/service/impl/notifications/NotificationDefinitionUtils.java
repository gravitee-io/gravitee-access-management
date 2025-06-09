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

import io.gravitee.am.management.service.impl.notifications.definition.NotifierSubject;
import io.gravitee.am.model.safe.CertificateProperties;
import io.gravitee.am.model.safe.ClientSecretProperties;
import io.gravitee.node.api.notifier.NotificationDefinition;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationDefinitionUtils {
    public static final String TYPE_UI_NOTIFIER = "ui-notifier";
    public static final String TYPE_EMAIL_NOTIFIER = "email-notifier";
    public static final String TYPE_KAFKA_NOTIFIER = "kafka-notifier";
    public static final String TYPE_LOGGER_NOTIFIER = "log-notifier";


    public static Optional<Date> getExpirationDate(NotificationDefinition definition) {
        Date expiry = null;
        final Map<String, Object> data = definition.getData();
        if (data != null && data.containsKey(NotifierSubject.NOTIFIER_DATA_CERTIFICATE)) {
            expiry = ((CertificateProperties) data.get(NotifierSubject.NOTIFIER_DATA_CERTIFICATE)).getExpiresAt();
        }
        else if (data != null && data.containsKey(NotifierSubject.NOTIFIER_DATA_CLIENT_SECRET)) {
            expiry = ((ClientSecretProperties) data.get(NotifierSubject.NOTIFIER_DATA_CLIENT_SECRET)).getExpiresAt();
        }
        return Optional.ofNullable(expiry);
    }

}
