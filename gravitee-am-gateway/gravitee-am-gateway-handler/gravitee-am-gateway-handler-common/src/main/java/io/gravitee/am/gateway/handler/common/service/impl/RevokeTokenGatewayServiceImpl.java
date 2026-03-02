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


import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.RevokeTokenEvent;
import io.gravitee.am.gateway.handler.common.service.RevokeTokenGatewayService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.common.event.Event;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class RevokeTokenGatewayServiceImpl extends AbstractService implements RevokeTokenGatewayService {

    @Lazy
    @Autowired
    private BackwardCompatibleTokenRepository tokenRepository;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private AuditService auditService;

    @Autowired
    private Domain domain;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Register event listener for RevokeToken events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, RevokeTokenEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Unregister event listener for RevokeToken events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, RevokeTokenEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<RevokeTokenEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            final var revokeToken = event.content().getRevokeToken();
            if (event.type() == RevokeTokenEvent.REVOKE) {
                process(revokeToken)
                        .doOnComplete(() -> log.debug("Revoke Token {} action successful on domain '{}' for clientId '{}' and userId '{}'.",
                                revokeToken.getRevokeType(),
                                revokeToken.getDomainId(),
                                revokeToken.getClientId(),
                                revokeToken.getUserId()))
                        .doOnError(error -> log.error("Revoke Token {} action fails on domain '{}' for clientId '{}' and userId '{}'.",
                                revokeToken.getRevokeType(),
                                revokeToken.getDomainId(),
                                revokeToken.getClientId(),
                                revokeToken.getUserId(), error))
                        .onErrorComplete()
                        .subscribe();
            }
        }
    }

    @Override
    public Completable deleteByUser(User user, boolean needAudit) {
        log.debug("Delete tokens by user : {}", user.getId());
        var userId = user.getId();
        return tokenRepository.deleteByUserId(userId)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to delete tokens by user {}", userId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete tokens by user: %s", userId), ex));
                })
                .doOnEvent(error -> { if (needAudit) {
                            auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                                    .reference(new Reference(user.getReferenceType(), user.getReferenceId()))
                                    .tokenActor(user)
                                    .revoked("All tokens are revoked for user: " + userId)
                                    .throwable(error));
                            }
                        }
                );
    }

    @Override
    public Completable process(Domain domain, RevokeToken revokeTokenDescription) {
        return process(revokeTokenDescription);
    }

    private Completable process(RevokeToken revokeTokenDescription) {
        return switch (revokeTokenDescription.getRevokeType()) {
            case BY_USER -> tokenRepository.deleteByDomainIdAndUserId(revokeTokenDescription.getDomainId(), revokeTokenDescription.getUserId());
            case BY_CLIENT -> tokenRepository.deleteByDomainIdAndClientId(revokeTokenDescription.getDomainId(), revokeTokenDescription.getClientId());
            case BY_USER_AND_CLIENT -> tokenRepository.deleteByDomainIdClientIdAndUserId(revokeTokenDescription.getDomainId(), revokeTokenDescription.getClientId(), revokeTokenDescription.getUserId());
        };
    }

}
