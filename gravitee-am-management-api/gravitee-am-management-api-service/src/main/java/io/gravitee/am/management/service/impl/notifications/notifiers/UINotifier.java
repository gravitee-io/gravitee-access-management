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
package io.gravitee.am.management.service.impl.notifications.notifiers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils;
import io.gravitee.am.management.service.impl.notifications.definition.NotifierSubject;
import io.gravitee.am.management.service.impl.notifications.template.TemplateProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.notification.UserNotification;
import io.gravitee.am.model.notification.UserNotificationStatus;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.repository.management.api.UserNotificationRepository;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.api.Notifier;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UINotifier implements Notifier {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    @Lazy
    private UserNotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    @Qualifier("uiNotificationTemplateProvider")
    private TemplateProvider uiTemplateProvider;

    @Override
    public CompletableFuture<Void> send(Notification notification, Map<String, Object> param) {
        var future = new CompletableFuture<Void>();

        final UserProperties audience = (UserProperties) param.get(NotifierSubject.NOTIFIER_DATA_USER);
        final DomainProperties domain = (DomainProperties) param.get(NotifierSubject.NOTIFIER_DATA_DOMAIN);
        if (audience == null || domain == null) {

            logger.warn("Receive notification to store in database without user or domain, ignore it.");
            future.complete(null);

        } else {

            try {
                UINotifierConfiguration notifierConfiguration = mapper.readValue(notification.getConfiguration(), UINotifierConfiguration.class);

                String content = uiTemplateProvider.getNotificationContent(notifierConfiguration.getTemplate(), param);

                final Date now = new Date();
                final UserNotification userNotif = new UserNotification();
                userNotif.setMessage(content);
                userNotif.setCreatedAt(now);
                userNotif.setUpdatedAt(now);
                userNotif.setStatus(UserNotificationStatus.UNREAD);
                userNotif.setReferenceId(domain.getId());
                userNotif.setReferenceType(ReferenceType.DOMAIN);
                userNotif.setAudienceId(audience.getId());

                logger.debug("Receive notification to store in database for user '{}'", audience.getId());

                notificationRepository.create(userNotif).observeOn(Schedulers.io()).subscribe(createdNotif -> {
                    logger.debug("Notification stored: {}", createdNotif);
                    future.complete(null); // CompletableStage use the Void type. So it requires null to be mapped properly in the NotificationTrigger
                }, future::completeExceptionally);

            } catch (Exception e) {
                logger.warn("Unable to deserialize ManagementUI Notifier configuration : {}", e.getMessage());
                future.completeExceptionally(e);
            }
        }

        return future;
    }

}