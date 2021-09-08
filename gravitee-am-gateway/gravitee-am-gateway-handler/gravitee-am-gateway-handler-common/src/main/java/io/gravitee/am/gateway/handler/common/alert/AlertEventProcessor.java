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
package io.gravitee.am.gateway.handler.common.alert;

import io.gravitee.alert.api.event.DefaultEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.gateway.handler.common.auth.AuthenticationDetails;
import io.gravitee.am.gateway.handler.common.auth.event.AuthenticationEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.gravitee.gateway.api.Request;
import io.gravitee.node.api.Node;
import io.gravitee.plugin.alert.AlertEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Map;

import static io.gravitee.am.common.event.AlertEventKeys.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AlertEventProcessor extends AbstractService {

    private static final Logger logger = LoggerFactory.getLogger(AlertEventProcessor.class);

    @Autowired
    private EventManager eventManager;

    @Autowired
    private AlertEventProducer eventProducer;

    @Autowired
    private Domain domain;

    @Autowired
    private Node node;

    @Autowired
    private Environment environment;

    @Autowired
    private EnvironmentService environmentService;

    private final EventListener<AuthenticationEvent, AuthenticationDetails> authenticationEventListener = this::onAuthenticationEvent;

    private String environmentId;

    private String organizationId;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        try {
            this.environmentId = domain.getReferenceId();
            final io.gravitee.am.model.Environment domainEnv = environmentService.findById(environmentId).blockingGet();
            this.organizationId = domainEnv.getOrganizationId();
        } catch (Exception e) {
            logger.warn("The domain [{}] seems not attached to any environment or organization. Alert events may not be accurate.", domain.getName());
        }

        logger.info("Register event listener for all events for domain {}", domain.getName());
        eventManager.subscribeForEvents(authenticationEventListener, AuthenticationEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for all events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(authenticationEventListener, AuthenticationEvent.class, domain.getId());
    }

    private void onAuthenticationEvent(Event<AuthenticationEvent, AuthenticationDetails> eventItem) {

        final AuthenticationDetails authenticationDetails = eventItem.content();

        // TODO: event date should be owned by the event itself ?
        final DefaultEvent.Builder eventBuilder = io.gravitee.alert.api.event.Event.at(System.currentTimeMillis())
                .type(TYPE_AUTHENTICATION)
                .context(CONTEXT_NODE_ID, node.id())
                .context(CONTEXT_NODE_APPLICATION, node.application())
                .context(CONTEXT_NODE_HOSTNAME, node.hostname())
                .context(CONTEXT_GATEWAY_PORT, environment.getProperty("http.port", "8092"))
                .context(PROCESSOR_GEOIP, PROPERTY_IP)
                .context(PROCESSOR_USERAGENT, PROPERTY_USER_AGENT)
                .property(PROPERTY_DOMAIN, authenticationDetails.getDomain().getId())
                .property(PROPERTY_APPLICATION, authenticationDetails.getClient().getId())
                .property(PROPERTY_USER, authenticationDetails.getPrincipal().getPrincipal())
                .property(PROPERTY_AUTHENTICATION_STATUS, eventItem.type().name())
                .property(PROPERTY_ORGANIZATION, organizationId)
                .property(PROPERTY_ENVIRONMENT, environmentId);

        if (authenticationDetails.getPrincipal().getContext() != null) {
            final Map<String, Object> attributes = authenticationDetails.getPrincipal().getContext().attributes();
            if (attributes != null) {
                eventBuilder.property(PROPERTY_IP, attributes.get("ip_address"));
                eventBuilder.property(PROPERTY_USER_AGENT, attributes.get("user_agent"));
            }

            final Request request = authenticationDetails.getPrincipal().getContext().request();
            if (request != null) {
                eventBuilder.property(PROPERTY_TRANSACTION_ID, request.transactionId());
            }
        }

        sendEvent(eventBuilder.build());
    }

    private void sendEvent(io.gravitee.alert.api.event.Event event) {
        try {
            logger.debug("Send event to alert engine");
            eventProducer.send(event);
        } catch (Exception e) {
            logger.error("An error occurs while sending event to alert engine", e);
        }
    }


}
