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
import io.gravitee.am.management.service.impl.notifications.notifiers.UINotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.node.api.notifier.NotificationDefinition;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_UI_NOTIFIER;

@RequiredArgsConstructor
public class UiNotificationDefinitionFactory<T extends NotifierSubject> implements NotificationDefinitionFactory<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UiNotificationDefinitionFactory.class);

    private final ObjectMapper mapper;
    private final NotifierSettings notifierSettings;

    @Override
    public Maybe<NotificationDefinition> buildNotificationDefinition(T object) {
        try {
            Map<String, Object> data = object.getData();

            final NotificationDefinition definition = new NotificationDefinition();
            definition.setType(TYPE_UI_NOTIFIER);
            definition.setConfiguration(mapper.writeValueAsString(UINotifierConfiguration.of(notifierSettings.template())));
            definition.setResourceId(object.getResourceId());
            definition.setResourceType(object.getResourceType());
            definition.setAudienceId(object.getUser().getId());
            definition.setCron(notifierSettings.cronExpression());
            definition.setData(data);

            return Maybe.just(definition);
        } catch (IOException e) {
            LOGGER.warn("Unable to generate ui notification def for {}", object.getResourceType(), e);
            return Maybe.error(e);
        }
    }
}
