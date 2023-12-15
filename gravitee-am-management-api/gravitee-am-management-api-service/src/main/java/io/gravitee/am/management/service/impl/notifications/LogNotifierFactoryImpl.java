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

import io.gravitee.am.management.service.impl.LogNotificationService;
import io.gravitee.node.api.notifier.NotificationDefinition;
import io.gravitee.node.notifier.plugin.impl.NotifierPluginFactoryImpl;
import io.gravitee.notifier.api.Notifier;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogNotifierFactoryImpl extends NotifierPluginFactoryImpl {

    @Autowired
    private LogNotificationService logNotificationService;

    @Override
    public Optional<Notifier> create(NotificationDefinition notification) {
        if (notification.getType().equals(NotificationDefinitionUtils.TYPE_LOG_NOTIFIER)) {
            return Optional.of(logNotificationService);
        }
        return super.create(notification);
    }
}
