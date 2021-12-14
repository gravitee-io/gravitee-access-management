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
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.AlertNotifierRepository;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AlertNotifierNotFoundException;
import io.gravitee.am.service.model.NewAlertNotifier;
import io.gravitee.am.service.model.PatchAlertNotifier;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.AlertNotifierAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Primary
public class AlertNotifierServiceImpl implements io.gravitee.am.service.AlertNotifierService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertNotifierServiceImpl.class);

    private final AlertNotifierRepository alertNotifierRepository;
    private final AuditService auditService;
    private final EventService eventService;

    public AlertNotifierServiceImpl(@Lazy AlertNotifierRepository alertNotifierRepository, AuditService auditService, EventService eventService) {
        this.alertNotifierRepository = alertNotifierRepository;
        this.auditService = auditService;
        this.eventService = eventService;
    }

    /**
     * Get the alert notifier by its id and reference it belongs to.
     *
     * @param referenceType the reference type.
     * @param referenceId the reference id.
     * @param notifierId the notifier identifier.
     * @return the alert notifier found or an {@link AlertNotifierNotFoundException} exception if it has not been found.
     */
    @Override
    public Single<AlertNotifier> getById(ReferenceType referenceType, String referenceId, String notifierId) {
        LOGGER.debug("Find alert notifier by id {}", notifierId);

        return this.alertNotifierRepository.findById(notifierId)
                .filter(alertNotifier -> alertNotifier.getReferenceType() == referenceType && alertNotifier.getReferenceId().equals(referenceId))
                .switchIfEmpty(Single.error(new AlertNotifierNotFoundException(notifierId)));
    }

    /**
     * Find all the alert notifiers of a domain corresponding to the specified criteria.
     *
     * @param domainId the domain the alert notifiers are attached to.
     * @param criteria the criteria to match.
     * @return the list of alert notifiers found.
     */
    @Override
    public Flowable<AlertNotifier> findByDomainAndCriteria(String domainId, AlertNotifierCriteria criteria) {
        return findByReferenceAndCriteria(ReferenceType.DOMAIN, domainId, criteria);
    }

    /**
     * Find all the alert notifiers by reference (ex: domain) corresponding to the specified criteria.
     *
     * @param referenceType the reference type.
     * @param referenceId the reference id.
     * @param criteria the criteria to match.
     * @return the list of alert notifiers found.
     */
    @Override
    public Flowable<AlertNotifier> findByReferenceAndCriteria(ReferenceType referenceType, String referenceId, AlertNotifierCriteria criteria) {
        LOGGER.debug("Find alert notifier by {} {} and criteria {}", referenceType, referenceId, criteria);

        return alertNotifierRepository.findByCriteria(referenceType, referenceId, criteria);
    }

    /**
     * Create a new alert notifier.
     *
     * @param referenceType the reference type the newly created alert notifier will be attached to.
     * @param referenceId the reference id the newly created alert notifier will be attached to.
     * @param newAlertNotifier the information about the alert notifier to create.
     * @param byUser the user at the origin of the creation.
     * @return the newly created alert notifier.
     */
    @Override
    public Single<AlertNotifier> create(ReferenceType referenceType, String referenceId, NewAlertNotifier newAlertNotifier, User byUser) {
        LOGGER.debug("Create alert notifier for {} {}: {}", referenceType, referenceId, newAlertNotifier);

        final AlertNotifier alertNotifier = newAlertNotifier.toAlertNotifier();
        alertNotifier.setReferenceType(referenceType);
        alertNotifier.setReferenceId(referenceId);

        return this.createInternal(alertNotifier, byUser);
    }

    /**
     * Update an existing alert notifier.
     *
     * @param referenceType the reference type the newly created alert notifier will be attached to.
     * @param referenceId the reference id the newly created alert notifier will be attached to.
     * @param patchAlertNotifier the information about the alert notifier to update.
     * @param byUser the user at the origin of the update.
     * @return the updated alert notifier or a {@link AlertNotifierNotFoundException} if the notifier has not been found.
     */
    @Override
    public Single<AlertNotifier> update(ReferenceType referenceType, String referenceId, String alertNotifierId, PatchAlertNotifier patchAlertNotifier, User byUser) {
        LOGGER.debug("Update alert notifier for {}: {}", alertNotifierId, patchAlertNotifier);

        return this.getById(referenceType, referenceId, alertNotifierId)
                .flatMap(alertNotifier -> {
                    AlertNotifier toUpdate = patchAlertNotifier.patch(alertNotifier);

                    if (toUpdate.equals(alertNotifier)) {
                        // Do not update alert notifier if nothing has changed.
                        return Single.just(alertNotifier);
                    }
                    return updateInternal(toUpdate, byUser, alertNotifier);
                });
    }

    /**
     * Delete the alert notifier by its id and reference it belongs to.
     *
     * @param referenceType the reference type.
     * @param referenceId the reference id.
     * @param notifierId the notifier identifier.
     * @return nothing if the alert notifier has been successfully delete or an {@link AlertNotifierNotFoundException} exception if it has not been found.
     */
    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String notifierId, User byUser) {
        return this.getById(referenceType, referenceId, notifierId)
                .flatMapCompletable(alertNotifier -> deleteInternal(alertNotifier, byUser));
    }

    private Single<AlertNotifier> createInternal(AlertNotifier toCreate, User byUser) {

        Date now = new Date();

        toCreate.setId(RandomString.generate());
        toCreate.setCreatedAt(now);
        toCreate.setUpdatedAt(now);

        return alertNotifierRepository.create(toCreate)
                .flatMap(updated -> eventService.create(new Event(Type.ALERT_NOTIFIER, new Payload(updated.getId(), updated.getReferenceType(), updated.getReferenceId(), Action.CREATE))).ignoreElement().andThen(Single.just(updated)))
                .doOnSuccess(alertTrigger -> auditService.report(AuditBuilder.builder(AlertNotifierAuditBuilder.class).type(EventType.ALERT_NOTIFIER_CREATED).alertNotifier(alertTrigger).principal(byUser)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AlertNotifierAuditBuilder.class).type(EventType.ALERT_NOTIFIER_CREATED).alertNotifier(toCreate).principal(byUser).throwable(throwable)));
    }

    private Single<AlertNotifier> updateInternal(AlertNotifier alertNotifier, User updatedBy, AlertNotifier previous) {

        alertNotifier.setUpdatedAt(new Date());

        return alertNotifierRepository.update(alertNotifier)
                .flatMap(updated -> eventService.create(new Event(Type.ALERT_NOTIFIER, new Payload(updated.getId(), updated.getReferenceType(), updated.getReferenceId(), Action.UPDATE))).ignoreElement().andThen(Single.just(updated)))
                .doOnSuccess(updated -> auditService.report(AuditBuilder.builder(AlertNotifierAuditBuilder.class).type(EventType.ALERT_NOTIFIER_UPDATED).alertNotifier(updated).principal(updatedBy).oldValue(previous)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AlertNotifierAuditBuilder.class).type(EventType.ALERT_NOTIFIER_UPDATED).alertNotifier(previous).principal(updatedBy).throwable(throwable)));
    }

    private Completable deleteInternal(AlertNotifier alertNotifier, User deletedBy) {
        return alertNotifierRepository.delete(alertNotifier.getId())
                .andThen(eventService.create(new Event(Type.ALERT_NOTIFIER, new Payload(alertNotifier.getId(), alertNotifier.getReferenceType(), alertNotifier.getReferenceId(), Action.DELETE))).ignoreElement())
                .doOnComplete(() -> auditService.report(AuditBuilder.builder(AlertNotifierAuditBuilder.class).type(EventType.ALERT_NOTIFIER_DELETED).alertNotifier(alertNotifier).principal(deletedBy)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AlertNotifierAuditBuilder.class).type(EventType.ALERT_NOTIFIER_DELETED).alertNotifier(alertNotifier).principal(deletedBy).throwable(throwable)));
    }
}
