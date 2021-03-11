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
package io.gravitee.am.management.service.alerts.handlers;

import io.gravitee.alert.api.trigger.TriggerProvider;
import io.gravitee.alert.api.trigger.command.AlertNotificationCommand;
import io.gravitee.alert.api.trigger.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AlertNotificationCommandHandler implements TriggerProvider.OnCommandListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertNotificationCommandHandler.class);

    @Override
    public void doOnCommand(Command command) {
        if (command instanceof AlertNotificationCommand) {
            LOGGER.info("Received a notification from alert engine: {}", ((AlertNotificationCommand) command).getMessage());
        } else {
            LOGGER.warn("Unknown alert command: {}", command);
        }
    }
}
