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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.AlertTriggerRepository;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AlertTriggerNotFoundException;
import io.gravitee.am.service.model.PatchAlertTrigger;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.AlertTriggerAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AlertTriggerServiceImpl implements io.gravitee.am.service.AlertTriggerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertTriggerServiceImpl.class);

    private final AlertTriggerRepository alertTriggerRepository;
    private final AuditService auditService;
    private final EventService eventService;

    public AlertTriggerServiceImpl(@Lazy AlertTriggerRepository alertTriggerRepository, AuditService auditService, EventService eventService) {
        this.alertTriggerRepository = alertTriggerRepository;
        this.auditService = auditService;
        this.eventService = eventService;
    }

    /**
     * Find the alert trigger corresponding to the specified id.
     *
     * @param id the alert trigger identifier.
     * @return the alert trigger found or an {@link AlertTriggerNotFoundException} if no alert trigger has been found.
     */
    @Override
    public Single<AlertTrigger> getById(String id) {
        LOGGER.debug("Find alert trigger by id: {}", id);

        return alertTriggerRepository.findById(id)
                .switchIfEmpty(Single.error(new AlertTriggerNotFoundException(id)));
    }


    /**
     * Find the alert trigger corresponding to the specified id and its reference.
     *
     * @param referenceType the reference type.
     * @param referenceId the reference id.
     * @param id the alert trigger identifier.
     * @return the alert trigger found or an {@link AlertTriggerNotFoundException} if no alert trigger has been found.
     */
    @Override
    public Single<AlertTrigger> getById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("Find alert trigger by id: {}", id);

        return alertTriggerRepository.findById(id)
                .filter(alertTrigger -> alertTrigger.getReferenceType().equals(referenceType) && alertTrigger.getReferenceId().equals(referenceId))
                .switchIfEmpty(Single.error(new AlertTriggerNotFoundException(id)));
    }

    /**
     * Find all alert triggers of a domain and matching the specified criteria.
     *
     * @param domainId the id of the domain the alert trigger is attached to.
     * @param criteria the criteria to match.
     * @return the alert triggers found or empty if none has been found.
     */
    @Override
    public Flowable<AlertTrigger> findByDomainAndCriteria(String domainId, AlertTriggerCriteria criteria) {
        LOGGER.debug("Find alert trigger by domain {} and criteria: {}", domainId, criteria);

        return alertTriggerRepository.findByCriteria(ReferenceType.DOMAIN, domainId, criteria);
    }

    /**
     * Create or update an alert trigger.
     * Note: alert trigger are predefined. There is at most one trigger of each type for a given reference.
     *
     * @param referenceType the reference type.
     * @param referenceId the reference id.
     * @param patchAlertTrigger the information on the alert trigger to create or update.
     * @param byUser the user at the origin of the update.
     * @return the created or updated alert trigger.
     */
    @Override
    public Single<AlertTrigger> createOrUpdate(ReferenceType referenceType, String referenceId, PatchAlertTrigger patchAlertTrigger, User byUser) {
        LOGGER.debug("Create or update alert trigger for {} {}: {}", referenceType, referenceId, patchAlertTrigger);

        final AlertTriggerCriteria criteria = new AlertTriggerCriteria();
        criteria.setType(patchAlertTrigger.getType());

        return alertTriggerRepository.findByCriteria(referenceType, referenceId, criteria)
                .firstElement()
                .flatMap(alertTrigger -> {
                    AlertTrigger toUpdate = patchAlertTrigger.patch(alertTrigger);

                    if (toUpdate.equals(alertTrigger)) {
                        // Do not update alert trigger if nothing has changed.
                        return Maybe.just(alertTrigger);
                    }
                    return updateInternal(toUpdate, byUser, alertTrigger).toMaybe();
                })
                .switchIfEmpty(Single.defer(() -> {
                    AlertTrigger alertTrigger = new AlertTrigger();
                    alertTrigger.setId(RandomString.generate());
                    alertTrigger.setReferenceType(referenceType);
                    alertTrigger.setReferenceId(referenceId);
                    alertTrigger.setType(patchAlertTrigger.getType());
                    alertTrigger = patchAlertTrigger.patch(alertTrigger);

                    return createInternal(alertTrigger, byUser);
                }));
    }

    /**
     * Delete the alert trigger by its id and reference it belongs to.
     *
     * @param referenceType the reference type.
     * @param referenceId the reference id.
     * @param alertTriggerId the alert trigger identifier.
     * @return nothing if the alert trigger has been successfully delete or an {@link AlertTriggerNotFoundException} exception if it has not been found.
     */
    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String alertTriggerId, User byUser) {
        return this.getById(referenceType, referenceId, alertTriggerId)
                .flatMapCompletable(alertTrigger -> deleteInternal(alertTrigger, byUser));
    }

    private Single<AlertTrigger> createInternal(AlertTrigger toCreate, User byUser) {

        Date now = new Date();

        toCreate.setCreatedAt(now);
        toCreate.setUpdatedAt(now);

        return alertTriggerRepository.create(toCreate)
                .flatMap(created -> eventService.create(new Event(Type.ALERT_TRIGGER, new Payload(created.getId(), created.getReferenceType(), created.getReferenceId(), Action.CREATE))).ignoreElement().andThen(Single.just(created)))
                .doOnSuccess(alertTrigger -> auditService.report(AuditBuilder.builder(AlertTriggerAuditBuilder.class).type(EventType.ALERT_TRIGGER_CREATED).alertTrigger(alertTrigger).principal(byUser)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AlertTriggerAuditBuilder.class).type(EventType.ALERT_TRIGGER_CREATED).alertTrigger(toCreate).principal(byUser).throwable(throwable)));
    }

    private Single<AlertTrigger> updateInternal(AlertTrigger alertTrigger, User updatedBy, AlertTrigger previous) {

        alertTrigger.setUpdatedAt(new Date());

        return alertTriggerRepository.update(alertTrigger)
                .flatMap(updated -> eventService.create(new Event(Type.ALERT_TRIGGER, new Payload(updated.getId(), updated.getReferenceType(), updated.getReferenceId(), Action.UPDATE))).ignoreElement().andThen(Single.just(updated)))
                .doOnSuccess(updated -> auditService.report(AuditBuilder.builder(AlertTriggerAuditBuilder.class).type(EventType.ALERT_TRIGGER_UPDATED).alertTrigger(updated).principal(updatedBy).oldValue(previous)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AlertTriggerAuditBuilder.class).type(EventType.ALERT_TRIGGER_UPDATED).alertTrigger(previous).principal(updatedBy).throwable(throwable)));
    }


    private Completable deleteInternal(AlertTrigger alertTrigger, User deletedBy) {
        return alertTriggerRepository.delete(alertTrigger.getId())
                .andThen(eventService.create(new Event(Type.ALERT_TRIGGER, new Payload(alertTrigger.getId(), alertTrigger.getReferenceType(), alertTrigger.getReferenceId(), Action.DELETE))).ignoreElement())
                .doOnComplete(() -> auditService.report(AuditBuilder.builder(AlertTriggerAuditBuilder.class).type(EventType.ALERT_TRIGGER_DELETED).alertTrigger(alertTrigger).principal(deletedBy)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AlertTriggerAuditBuilder.class).type(EventType.ALERT_TRIGGER_DELETED).alertTrigger(alertTrigger).principal(deletedBy).throwable(throwable)));
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        return this.alertTriggerRepository.deleteByReference(referenceType, referenceId);
    }
}