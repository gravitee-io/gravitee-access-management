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

import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.oidc.command.Command;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.management.service.CommandManagementService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.command.CommandRequest;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.CommandAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class CommandManagementServiceImpl implements CommandManagementService {

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Completable sendCommand(Domain domain, Command command, String userId, io.gravitee.am.identityprovider.api.User principal) {
        final var commandRequest = CommandRequest.builder()
                .id(RandomString.generate())
                .command(command.value())
                .userId(userId)
                .domainId(domain.getId())
                .principalId(principal == null ? null : principal.getId())
                .principalUsername(principal == null ? null : principal.getUsername())
                .build();
        final var event = new Event(Type.COMMAND, Payload.from(commandRequest));
        return eventService.create(event, domain).ignoreElement()
                .doOnError(err -> auditService.report(AuditBuilder.builder(CommandAuditBuilder.class).scheduled(commandRequest).throwable(err)))
                .doOnComplete(() -> auditService.report(AuditBuilder.builder(CommandAuditBuilder.class).scheduled(commandRequest)));
    }
}
