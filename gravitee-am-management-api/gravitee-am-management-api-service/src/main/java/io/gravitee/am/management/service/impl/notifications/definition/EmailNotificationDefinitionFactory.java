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
package io.gravitee.am.management.service.impl.notifications.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.impl.notifications.EmailNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.node.api.notifier.NotificationDefinition;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_EMAIL_NOTIFIER;

@Slf4j
@RequiredArgsConstructor
public class EmailNotificationDefinitionFactory<T extends NotifierSubject> implements NotificationDefinitionFactory<T> {

    private final EmailService emailService;
    private final EmailNotifierConfiguration emailConfiguration;
    private final ObjectMapper mapper;
    private final NotifierSettings notifierSettings;

    @Override
    public Maybe<NotificationDefinition> buildNotificationDefinition(T object) {
        if(object.getUser().getEmail() == null) {
            log.warn("Email recipient doesn't have an email address");
            return Maybe.empty();
        }
        Map<String, Object> data = object.getData();

        return emailService.getFinalEmail(object.getDomain(), null, notifierSettings.template(), object.getUser(), data)
                .map(email -> {
                    EmailNotifierConfiguration notifierConfig = new EmailNotifierConfiguration(this.emailConfiguration);
                    notifierConfig.setSubject(email.getSubject());
                    notifierConfig.setBody(email.getContent());
                    notifierConfig.setTo(object.getUser().getEmail());

                    final NotificationDefinition definition = new NotificationDefinition();
                    definition.setType(TYPE_EMAIL_NOTIFIER);
                    definition.setConfiguration(mapper.writeValueAsString(notifierConfig));
                    definition.setResourceId(object.getResourceId());
                    definition.setResourceType(object.getResourceType());
                    definition.setAudienceId(object.getUser().getId());
                    definition.setCron(notifierSettings.cronExpression());
                    definition.setData(data);

                    return definition;
                });
    }

}
