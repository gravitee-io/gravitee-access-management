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
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.BotDetectionRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.BotDetectionNotFoundException;
import io.gravitee.am.service.exception.BotDetectionUsedException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewBotDetection;
import io.gravitee.am.service.model.UpdateBotDetection;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.BotDetectionAuditBuilder;
import io.reactivex.*;
import io.reactivex.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class BotDetectionServiceImpl implements BotDetectionService {
    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(BotDetectionServiceImpl.class);

    @Lazy
    @Autowired
    private BotDetectionRepository botDetectionRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<BotDetection> findById(String id) {
        LOGGER.debug("Find bot detection by ID: {}", id);
        return botDetectionRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a bot detection using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a bot detection using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<BotDetection> findByDomain(String domain) {
        LOGGER.debug("Find bot detections by domain: {}", domain);
        return botDetectionRepository.findByReference(ReferenceType.DOMAIN, domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find bot detections by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find bot detections by domain", ex));
                });
    }

    @Override
    public Single<BotDetection> create(String domain, NewBotDetection newBotDetection, User principal) {
        LOGGER.debug("Create a new bot detection {} for domain {}", newBotDetection, domain);

        BotDetection botDetection = new BotDetection();
        botDetection.setId(newBotDetection.getId() == null ? RandomString.generate() : newBotDetection.getId());
        botDetection.setReferenceId(domain);
        botDetection.setReferenceType(ReferenceType.DOMAIN);
        botDetection.setName(newBotDetection.getName());
        botDetection.setType(newBotDetection.getType());
        botDetection.setDetectionType(newBotDetection.getDetectionType());
        botDetection.setConfiguration(newBotDetection.getConfiguration());
        botDetection.setCreatedAt(new Date());
        botDetection.setUpdatedAt(botDetection.getCreatedAt());

        return botDetectionRepository.create(botDetection)
                .flatMap(detection -> {
                    // create event for sync process
                    Event event = new Event(Type.BOT_DETECTION, new Payload(detection.getId(), detection.getReferenceType(), detection.getReferenceId(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(detection));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a detection", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a detection", ex));
                })
                .doOnSuccess(detection -> auditService.report(AuditBuilder.builder(BotDetectionAuditBuilder.class).principal(principal).type(EventType.BOT_DETECTION_CREATED).botDetection(detection)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(BotDetectionAuditBuilder.class).principal(principal).type(EventType.BOT_DETECTION_CREATED).throwable(throwable)));
    }

    @Override
    public Single<BotDetection> update(String domain, String id, UpdateBotDetection updateBotDetection, User principal) {
        LOGGER.debug("Update bot detection {} for domain {}", id, domain);

        return botDetectionRepository.findById(id)
                .switchIfEmpty(Maybe.error(new BotDetectionNotFoundException(id)))
                .flatMapSingle(oldBotDetection -> {
                    BotDetection botDetectionToUpdate = new BotDetection(oldBotDetection);
                    botDetectionToUpdate.setName(updateBotDetection.getName());
                    botDetectionToUpdate.setConfiguration(updateBotDetection.getConfiguration());
                    botDetectionToUpdate.setUpdatedAt(new Date());

                    return  botDetectionRepository.update(botDetectionToUpdate)
                            .flatMap(detection -> {
                                // create event for sync process
                                Event event = new Event(Type.BOT_DETECTION, new Payload(detection.getId(), detection.getReferenceType(), detection.getReferenceId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(detection));
                            })
                            .doOnSuccess(detection -> auditService.report(AuditBuilder.builder(BotDetectionAuditBuilder.class).principal(principal).type(EventType.BOT_DETECTION_UPDATED).oldValue(oldBotDetection).botDetection(detection)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(BotDetectionAuditBuilder.class).principal(principal).type(EventType.BOT_DETECTION_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update bot detection", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update bot detection", ex));
                });
    }

    @Override
    public Completable delete(String domainId, String botDetectionId, User principal) {
        LOGGER.debug("Delete bot detection {}", botDetectionId);

        return botDetectionRepository.findById(botDetectionId)
                .switchIfEmpty(Maybe.error(new BotDetectionNotFoundException(botDetectionId)))
                .flatMapSingle(checkBotDetectionReleasedByDomain(domainId, botDetectionId))
                .flatMap(checkBotDetectionReleasedByApp(domainId, botDetectionId))
                .flatMapCompletable(botDetection -> {
                    // create event for sync process
                    Event event = new Event(Type.BOT_DETECTION, new Payload(botDetectionId, ReferenceType.DOMAIN, domainId, Action.DELETE));
                    return botDetectionRepository.delete(botDetectionId)
                            .andThen(eventService.create(event))
                            .toCompletable()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(BotDetectionAuditBuilder.class).principal(principal).type(EventType.BOT_DETECTION_DELETED).botDetection(botDetection)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(BotDetectionAuditBuilder.class).principal(principal).type(EventType.BOT_DETECTION_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete bot detection: {}", botDetectionId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete bot detection: %s", botDetectionId), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domain) {
        return botDetectionRepository.deleteByReference(ReferenceType.DOMAIN, domain);
    }

    private Function<BotDetection, SingleSource<? extends BotDetection>> checkBotDetectionReleasedByApp(String domainId, String botDetectionId) {
        return botDetection -> applicationService.findByDomain(domainId)
                .flatMap(applications -> {
                    if (applications.stream().filter(app -> app.getSettings() != null &&
                            app.getSettings().getAccount() != null &&
                            botDetectionId.equals(app.getSettings().getAccount().getBotDetectionPlugin())).count() > 0) {
                        throw new BotDetectionUsedException();
                    }
                    return Single.just(botDetection);
                });
    }

    private Function<BotDetection, SingleSource<? extends BotDetection>> checkBotDetectionReleasedByDomain(String domainId, String botDetectionId) {
        return botDetection -> domainService.findById(domainId)
                .flatMapSingle(domain -> {
                    if (domain.getAccountSettings() != null &&
                            botDetectionId.equals(domain.getAccountSettings().getBotDetectionPlugin())) {
                        throw new BotDetectionUsedException();
                    }
                    return Single.just(botDetection);
                });
    }
}
