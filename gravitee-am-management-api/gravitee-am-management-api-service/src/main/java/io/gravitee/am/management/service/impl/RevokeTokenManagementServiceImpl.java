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
import io.gravitee.am.management.service.RevokeTokenManagementService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class RevokeTokenManagementServiceImpl implements RevokeTokenManagementService {

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Completable deleteByUser(Domain domain, User user) {
        log.debug("Delete tokens by user : {}", user.getId());
        var userId = user.getId();
        final var event = new Event(Type.REVOKE_TOKEN, Payload.from(RevokeToken.byUser(domain.getId(), UserId.internal(userId))));
        return eventService.create(event, domain).ignoreElement()
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to request tokens deletion for user {}", userId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to request tokens deletion for user: %s", userId), ex));
                })
                .doOnEvent(error -> auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                        .reference(new Reference(user.getReferenceType(), user.getReferenceId()))
                        .tokenActor(user)
                        .revoked("RevokeToken action requested for all tokens linked to user: " + userId)
                        .throwable(error)));
    }

    @Override
    public Completable deleteByApplication(Domain domain, Application application) {
        log.debug("Delete tokens by application : {}", application);
        var clientId = Optional.ofNullable(application.getSettings())
                .map(ApplicationSettings::getOauth)
                .map(ApplicationOAuthSettings::getClientId);
        final var event = new Event(Type.REVOKE_TOKEN, Payload.from(RevokeToken.byClientId(domain.getId(), clientId.get())));
        return eventService.create(event, domain).ignoreElement()
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to request tokens deletion by client {}", clientId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to request tokens deletion by client: %s", clientId), ex));
                })
                .doOnEvent(error -> auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                        .reference(Reference.domain(application.getDomain()))
                        .tokenActor(application.toClient())
                        .revoked("RevokeToken action requested for all tokens linked to client: " + clientId)
                        .throwable(error)));
    }

    @Override
    public Completable sendProcessRequest(Domain domain, RevokeToken revokeTokenDescription) {
        final var event = new Event(Type.REVOKE_TOKEN, Payload.from(revokeTokenDescription));
        return eventService.create(event, domain).ignoreElement();
    }
}
