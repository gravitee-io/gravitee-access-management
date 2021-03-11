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
package io.gravitee.am.management.service;

import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.alert.api.trigger.TriggerProvider;
import io.gravitee.am.common.event.AlertNotifierEvent;
import io.gravitee.am.common.event.AlertTriggerEvent;
import io.gravitee.am.common.event.DomainEvent;
import io.gravitee.am.management.service.alerts.AlertTriggerFactory;
import io.gravitee.am.management.service.alerts.handlers.AlertNotificationCommandHandler;
import io.gravitee.am.management.service.alerts.handlers.ResolvePropertyCommandHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.gravitee.am.service.AlertNotifierService;
import io.gravitee.am.service.AlertTriggerService;
import io.gravitee.am.service.DomainService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AlertTriggerManager extends AbstractService<CertificateManager> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertTriggerManager.class);

    private final TriggerProvider triggerProvider;
    private final AlertTriggerService alertTriggerService;
    private final AlertNotifierService alertNotifierService;
    private final DomainService domainService;
    private final EventManager eventManager;
    private final Environment environment;
    private final ResolvePropertyCommandHandler resolvePropertyCommandHandler;
    private final AlertNotificationCommandHandler alertNotificationCommandHandler;

    public AlertTriggerManager(TriggerProvider triggerProvider, AlertTriggerService alertTriggerService, AlertNotifierService alertNotifierService, DomainService domainService, EventManager eventManager, Environment environment, ResolvePropertyCommandHandler resolvePropertyCommandHandler, AlertNotificationCommandHandler alertNotificationCommandHandler) {
        this.triggerProvider = triggerProvider;
        this.alertTriggerService = alertTriggerService;
        this.alertNotifierService = alertNotifierService;
        this.domainService = domainService;
        this.eventManager = eventManager;
        this.environment = environment;
        this.resolvePropertyCommandHandler = resolvePropertyCommandHandler;
        this.alertNotificationCommandHandler = alertNotificationCommandHandler;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        triggerProvider.addListener((TriggerProvider.OnConnectionListener) this::doOnConnect);
        triggerProvider.addListener((TriggerProvider.OnDisconnectionListener) this::doOnDisconnect);
        triggerProvider.addListener(alertNotificationCommandHandler);
        triggerProvider.addListener(resolvePropertyCommandHandler);

        // Subscribe to some internal events in order to propagate changes on triggers to the alert system.
        eventManager.subscribeForEvents(this::onDomainEvent, DomainEvent.class);
        eventManager.subscribeForEvents(this::onAlertTriggerEvent, AlertTriggerEvent.class);
        eventManager.subscribeForEvents(this::onAlertNotifierEvent, AlertNotifierEvent.class);
    }

    void doOnConnect() {
        LOGGER.info("Connected to alerting system. Sync alert triggers...");
        domainService.findAllByCriteria(new DomainCriteria())
                .doOnNext(domain -> LOGGER.info("Sending alert triggers for domain {}", domain.getName()))
                .flatMap(this::prepareAETriggers)
                .flatMapSingle(this::registerAETrigger)
                .count()
                .subscribe(count -> LOGGER.info("{} alert triggers synchronized with the alerting system.", count),
                        throwable -> LOGGER.error("An error occurred when trying to synchronize alert triggers with alerting system", throwable));
    }

    void doOnDisconnect() {
        LOGGER.warn("Connection with the alerting system has been lost.");
    }

    void onDomainEvent(Event<DomainEvent, ?> event) {

        final Payload payload = (Payload) event.content();
        domainService.findById(payload.getReferenceId())
                .flatMapPublisher(this::prepareAETriggers)
                .flatMapSingle(this::registerAETrigger)
                .count()
                .subscribe(count -> LOGGER.info("{} alert triggers synchronized with the alerting system for domain [{}].", count, payload.getReferenceId()),
                        throwable -> LOGGER.error("An error occurred when trying to synchronize alert triggers with alerting system for domain [{}]", payload.getReferenceId(), throwable));
    }

    void onAlertTriggerEvent(Event<AlertTriggerEvent, ?> event) {

        LOGGER.debug("Received alert trigger event {}", event);

        final Payload payload = (Payload) event.content();
        domainService.findById(payload.getReferenceId())
                .flatMapSingle(domain -> alertTriggerService.getById(payload.getId())
                        .flatMap(alertTrigger -> this.prepareAETrigger(domain, alertTrigger))
                        .flatMap(this::registerAETrigger))
                .subscribe(aeTrigger -> LOGGER.info("Alert trigger [{}] synchronized with the alerting system.", aeTrigger.getId()),
                        throwable -> LOGGER.error("An error occurred when trying to synchronize alert trigger [{}] with alerting system", payload.getId(), throwable));
    }

    void onAlertNotifierEvent(Event<AlertNotifierEvent, ?> event) {

        LOGGER.debug("Received alert notifier event {}", event);

        final Payload payload = (Payload) event.content();
        final AlertTriggerCriteria alertTriggerCriteria = new AlertTriggerCriteria();
        alertTriggerCriteria.setEnabled(true);
        alertTriggerCriteria.setAlertNotifierIds(Collections.singletonList(payload.getId()));

        domainService.findById(payload.getReferenceId())
                .filter(domain -> domain.isEnabled() && domain.isAlertEnabled())
                .flatMapPublisher(domain -> this.alertTriggerService.findByDomainAndCriteria(domain.getId(), alertTriggerCriteria)
                        .flatMapSingle(alertTrigger -> prepareAETrigger(domain, alertTrigger))
                        .flatMapSingle(this::registerAETrigger))
                .count()
                .subscribe(count -> LOGGER.info("{} alert triggers synchronized with the alerting system for domain [{}] after the update of alert notifier [{}].", count, payload.getReferenceId(), payload.getId()),
                        throwable -> LOGGER.error("An error occurred when trying to synchronize alert triggers with alerting system for domain [{}] after the alert notifier {} event [{}].", payload.getReferenceId(), event.type().name().toLowerCase(), payload.getId(), throwable));
    }

    private Single<Trigger> registerAETrigger(Trigger trigger) {
        return Single.defer(() -> {
            triggerProvider.register(trigger);
            LOGGER.debug("Alert trigger [{}] has been pushed to alert system.", trigger.getId());
            return Single.just(trigger);
        });
    }

    private Flowable<Trigger> prepareAETriggers(Domain domain) {
        return alertTriggerService.findByDomainAndCriteria(domain.getId(), new AlertTriggerCriteria())
                .flatMapSingle(alertTrigger -> this.prepareAETrigger(domain, alertTrigger));
    }

    private Single<Trigger> prepareAETrigger(Domain domain, AlertTrigger alertTrigger) {
        final AlertNotifierCriteria alertNotifierCriteria = new AlertNotifierCriteria();
        alertNotifierCriteria.setEnabled(true);
        alertNotifierCriteria.setIds(alertTrigger.getAlertNotifiers());

        return alertNotifierService.findByReferenceAndCriteria(alertTrigger.getReferenceType(), alertTrigger.getReferenceId(), alertNotifierCriteria)
                .toList()
                .map(alertNotifiers -> AlertTriggerFactory.create(alertTrigger, alertNotifiers, environment))
                .doOnSuccess(trigger -> trigger.setEnabled(domain.isEnabled() && domain.isAlertEnabled() && trigger.isEnabled()));
    }
}
