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

package io.gravitee.am.gateway.handler.common.service.impl;

import io.gravitee.am.common.event.CommandEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.gateway.handler.common.command.CommandStagingService;
import io.gravitee.am.gateway.handler.common.service.CommandGatewayService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.common.event.Event;
import io.gravitee.common.service.AbstractService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author GraviteeSource Team
 */
@Slf4j
public class CommandGatewayServiceImpl extends AbstractService implements CommandGatewayService {

    @Autowired
    private CommandStagingService commandStagingService;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private Domain domain;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Register event listener for Command events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, CommandEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Unregister event listener for Command events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, CommandEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<CommandEvent, Payload> event) {
        if (event.type() == CommandEvent.EXECUTE
                && event.content().getReferenceType() == ReferenceType.DOMAIN
                && domain.getId().equals(event.content().getReferenceId())) {
            final var commandRequest = event.content().getCommandRequest();
            log.debug("Received COMMAND event id={} command={} userId={} domain={}",
                    commandRequest.getId(), commandRequest.getCommand(), commandRequest.getUserId(), domain.getName());
            commandStagingService.stage(commandRequest)
                    .doOnComplete(() -> log.debug("Command {} staged for domain {}", commandRequest.getId(), domain.getName()))
                    .doOnError(error -> log.error("Unable to stage command {} for domain {}", commandRequest.getId(), domain.getName(), error))
                    .onErrorComplete()
                    .subscribe();
        }
    }
}
