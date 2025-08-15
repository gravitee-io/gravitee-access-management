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


import io.gravitee.am.common.event.DomainEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.gateway.handler.common.service.DomainAwareListener;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.gateway.api.RateLimitRepository;
import io.gravitee.am.repository.gateway.api.VerifyAttemptRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.service.AbstractService;
import io.gravitee.common.service.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class DomainEventListenerImpl extends AbstractService implements DomainAwareListener<DomainEvent, Domain>, Service {

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
        log.info("Register event listener for Domain events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, DomainEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Unregister event listener for Domain events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, DomainEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<DomainEvent, Domain> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getId())) {
            if (event.type() == DomainEvent.UNDEPLOY) {
                cleanUpMfaLimits(event.content().getId());
            }
        }
    }

    private void cleanUpMfaLimits(String domainId) {
        this.rateLimitRepository.deleteByDomain(domainId, ReferenceType.DOMAIN)
                .andThen(this.verifyAttemptRepository.deleteByDomain(domainId, ReferenceType.DOMAIN))
                .doOnComplete(() -> log.debug("MFA RateLimit and VerifyAttempt removed for domain '{}'", domainId))
                .doOnError(error -> log.error("Error during MFA RateLimit and VerifyAttempt removal for domain '{}'", domainId, error))
                .onErrorComplete()
                .subscribe();
    }

    @Override
    public String getDomainId() {
        return domain.getId();
    }
}
