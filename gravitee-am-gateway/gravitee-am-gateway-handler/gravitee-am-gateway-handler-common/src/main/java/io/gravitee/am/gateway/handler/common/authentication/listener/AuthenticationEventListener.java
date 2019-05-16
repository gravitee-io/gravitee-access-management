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
package io.gravitee.am.gateway.handler.common.authentication.listener;

import io.gravitee.am.gateway.handler.common.authentication.AuthenticationDetails;
import io.gravitee.am.gateway.handler.common.authentication.event.AuthenticationEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.AuthenticationAuditBuilder;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationEventListener implements InitializingBean, EventListener<AuthenticationEvent, AuthenticationDetails> {

    @Autowired
    private EventManager eventManager;

    @Autowired
    private AuditService auditService;

    @Autowired
    private Domain domain;

    @Override
    public void onEvent(Event<AuthenticationEvent, AuthenticationDetails> event) {
        if (domain.getId().equals(event.content().getDomain().getId())) {
            switch (event.type()) {
                case SUCCESS:
                    onAuthenticationSuccess(event.content());
                    break;
                case FAILURE:
                    onAuthenticationFailure(event.content());
            }
        }
    }

    private void onAuthenticationSuccess(AuthenticationDetails authenticationDetails) {
        auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class)
                .principal(authenticationDetails.getPrincipal())
                .domain(authenticationDetails.getDomain().getId())
                .client(authenticationDetails.getClient())
                .user(authenticationDetails.getUser()));
    }

    private void onAuthenticationFailure(AuthenticationDetails authenticationDetails) {
        auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class)
                .principal(authenticationDetails.getPrincipal())
                .domain(authenticationDetails.getDomain().getId())
                .client(authenticationDetails.getClient())
                .throwable(authenticationDetails.getThrowable()));
    }

    @Override
    public void afterPropertiesSet() {
        eventManager.subscribeForEvents(this, AuthenticationEvent.class);
    }
}
