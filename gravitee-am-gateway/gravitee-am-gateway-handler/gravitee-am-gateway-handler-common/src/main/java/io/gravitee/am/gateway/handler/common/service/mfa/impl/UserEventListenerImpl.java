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

package io.gravitee.am.gateway.handler.common.service.mfa.impl;


import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.UserEvent;
import io.gravitee.am.gateway.handler.common.service.mfa.UserEventListener;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.gateway.api.RateLimitRepository;
import io.gravitee.am.repository.gateway.api.VerifyAttemptRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.service.AbstractService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j

public class UserEventListenerImpl extends AbstractService implements UserEventListener {

    @Autowired
    private RateLimitRepository rateLimitRepository;
    @Autowired
    private VerifyAttemptRepository verifyAttemptRepository;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private Domain domain;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Register event listener for User events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, UserEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Unregister event listener for User events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, UserEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<UserEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            if (event.type() == UserEvent.DELETE) {
                cleanUpMfaLimits(event.content().getId());
            }
        }
    }

    private void cleanUpMfaLimits(String userId) {
        this.rateLimitRepository.deleteByUser(userId)
                .andThen(this.verifyAttemptRepository.deleteByUser(userId))
                .doOnComplete(() -> log.debug("MFA RateLimit and VerifyAttempt removed for userid '{}'", userId))
                .doOnError(error -> log.error("Error during MFA RateLimit and VerifyAttempt removal for userid '{}'", userId, error))
                .onErrorComplete()
                .subscribe();
    }
}
