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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.I18nDictionaryRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.DictionaryAlreadyExistsException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewDictionary;
import io.gravitee.am.service.model.UpdateI18nDictionary;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.DictionaryAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.common.audit.EventType.I18N_DICTIONARY_CREATED;
import static io.gravitee.am.common.audit.EventType.I18N_DICTIONARY_UPDATED;
import static io.gravitee.am.common.event.Action.UPDATE;
import static io.gravitee.am.common.event.Type.I18N_DICTIONARY;
import static io.reactivex.Completable.fromSingle;

@Component
public class I18nDictionaryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(I18nDictionaryService.class);
    private final I18nDictionaryRepository repository;
    private final EventService eventService;
    private final AuditService auditService;

    @Autowired
    public I18nDictionaryService(@Lazy I18nDictionaryRepository repository, EventService eventService, AuditService auditService) {
        this.repository = repository;
        this.eventService = eventService;
        this.auditService = auditService;
    }

    public Single<I18nDictionary> create(ReferenceType referenceType, String referenceId, NewDictionary newDictionary, User principal) {
        return findByName(referenceType, referenceId, newDictionary.getName())
                .isEmpty()
                .map(isEmpty -> {
                    if (!isEmpty) {
                        throw new DictionaryAlreadyExistsException(newDictionary.getName());
                    } else {
                        var dictionary = new I18nDictionary();
                        dictionary.setId(RandomString.generate());
                        dictionary.setReferenceType(referenceType);
                        dictionary.setReferenceId(referenceId);
                        dictionary.setLocale(newDictionary.getLocale());
                        dictionary.setName(newDictionary.getName());
                        dictionary.setCreatedAt(new Date());
                        dictionary.setUpdatedAt(dictionary.getCreatedAt());
                        return dictionary;
                    }
                })
                .flatMap(repository::create)
                .flatMap(dictionary -> {
                    Event event = new Event(I18N_DICTIONARY, new Payload(dictionary.getId(), dictionary.getReferenceType(), dictionary.getReferenceId(), Action.CREATE));
                    return eventService
                            .create(event)
                            .flatMap(createdEvent -> Single.just(dictionary));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    } else {
                        String msg = "An error occurred while trying to create an i18n dictionary";
                        LOGGER.error(msg, ex);
                        return Single.error(new TechnicalManagementException(msg, ex));
                    }
                })
                .doOnSuccess(dictionary -> auditService.report(AuditBuilder
                                                                       .builder(DictionaryAuditBuilder.class)
                                                                       .principal(principal)
                                                                       .type(I18N_DICTIONARY_CREATED)
                                                                       .dictionary(dictionary)))
                .doOnError(throwable -> auditService.report(AuditBuilder
                                                                    .builder(DictionaryAuditBuilder.class)
                                                                    .principal(principal)
                                                                    .type(I18N_DICTIONARY_CREATED)
                                                                    .throwable(throwable)));
    }

    public Maybe<I18nDictionary> findByName(ReferenceType referenceType, String referenceId, String name) {
        LOGGER.debug("Find dictionary by {} and name: {} {}", referenceType, referenceId, name);
        return repository.findByName(referenceType, referenceId, name)
                         .onErrorResumeNext(ex -> {
                             String msg = "An error occurred while trying to find a dictionary using its name: ? for the ? ?";
                             LOGGER.error(msg.replace("?", "{}"), name, referenceType, referenceId, ex);
                             return Maybe.error(new TechnicalManagementException(
                                     String.format(msg.replace("?", "%s"), name, referenceType, referenceId), ex));
                         });
    }

    public Single<I18nDictionary> update(ReferenceType referenceType, String referenceId, String id, UpdateI18nDictionary updateDictionary, User principal) {
        LOGGER.debug("Update a dictionary {} for {} {}", id, referenceType, referenceId);
        return findById(referenceType, referenceId, id)
                // check uniqueness
                .flatMap(existingDictionary -> repository
                        .findByName(referenceType, referenceId, updateDictionary.getName())
                        .map(Optional::ofNullable)
                        .defaultIfEmpty(Optional.empty())
                        .map(optionalDict -> {
                            if (optionalDict.isPresent() && !optionalDict.get().getId().equals(id)) {
                                throw new DictionaryAlreadyExistsException(updateDictionary.getName());
                            }
                            return existingDictionary;
                        })
                )
                .flatMapSingle(oldDictionary -> {
                    var toUpdate = new I18nDictionary(oldDictionary);
                    if (updateDictionary.getName() != null) {
                        toUpdate.setName(updateDictionary.getName());
                    }
                    if (updateDictionary.getLocale() != null) {
                        toUpdate.setLocale(updateDictionary.getLocale());
                    }
                    if (updateDictionary.getEntries() != null) {
                        toUpdate.setEntries(updateDictionary.getEntries());
                    }
                    return repository.update(toUpdate);
                })
                .flatMap(dictionary -> {
                    Event event = new Event(I18N_DICTIONARY, new Payload(dictionary.getId(), dictionary.getReferenceType(), dictionary.getReferenceId(), UPDATE));
                    return eventService
                            .create(event)
                            .flatMap(createdEvent -> Single.just(dictionary));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    String msg = "An error occurs while trying to update a dictionary";
                    LOGGER.error(msg, ex);
                    return Single.error(new TechnicalManagementException(msg, ex));
                })
                .doOnSuccess(dictionary -> auditService.report(AuditBuilder
                                                                       .builder(DictionaryAuditBuilder.class)
                                                                       .principal(principal)
                                                                       .type(I18N_DICTIONARY_UPDATED)
                                                                       .dictionary(dictionary)))
                .doOnError(throwable -> auditService.report(AuditBuilder
                                                                    .builder(DictionaryAuditBuilder.class)
                                                                    .principal(principal)
                                                                    .type(I18N_DICTIONARY_UPDATED)
                                                                    .throwable(throwable)));
    }

    public Single<I18nDictionary> updateEntries(ReferenceType referenceType, String referenceId, String id, Map<String, String> entries, User principal) {
        LOGGER.debug("Update entries for dictionary {} for {} {}", id, referenceType, referenceId);
        return findById(referenceType, referenceId, id)
                .flatMapSingle(oldDictionary -> {
                    var toUpdate = new I18nDictionary(oldDictionary);
                    toUpdate.setEntries(entries);
                    return repository.update(toUpdate);
                })
                .flatMap(dictionary -> {
                    Event event = new Event(I18N_DICTIONARY, new Payload(dictionary.getId(), dictionary.getReferenceType(), dictionary.getReferenceId(), UPDATE));
                    return eventService
                            .create(event)
                            .flatMap(createdEvent -> Single.just(dictionary));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    String msg = "An error occurs while trying to update a dictionary";
                    LOGGER.error(msg, ex);
                    return Single.error(new TechnicalManagementException(msg, ex));
                })
                .doOnSuccess(dictionary -> auditService.report(AuditBuilder
                                                                       .builder(DictionaryAuditBuilder.class)
                                                                       .principal(principal)
                                                                       .type(I18N_DICTIONARY_UPDATED)
                                                                       .dictionary(dictionary)))
                .doOnError(throwable -> auditService.report(AuditBuilder
                                                                    .builder(DictionaryAuditBuilder.class)
                                                                    .principal(principal)
                                                                    .type(I18N_DICTIONARY_UPDATED)
                                                                    .throwable(throwable)));
    }

    public Maybe<I18nDictionary> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("Find dictionary by id : {}", id);
        return repository.findById(referenceType, referenceId, id)
                         .onErrorResumeNext(ex -> {
                             String msg = "An error occurred while trying to find a dictionary using its id ?";
                             LOGGER.error(msg.replace("?", "{}"), id, ex);
                             return Maybe.error(new TechnicalManagementException(
                                     String.format(msg.replace("?", "%s"), id), ex));
                         });
    }

    public Flowable<I18nDictionary> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("Find dictionaries by {}: {}", referenceType, referenceId);
        return repository.findAll(referenceType, referenceId)
                              .onErrorResumeNext(ex -> {
                                  String msg = "An error occurred while trying to find dictionaries by ? ?";
                                  LOGGER.error(msg.replace("?", "{}"), referenceType, referenceId, ex);
                                  return Flowable.error(new TechnicalManagementException(String.format(msg.replace("?", "%s"), referenceType, referenceId), ex));
                              });
    }


    public Completable delete(ReferenceType referenceType, String referenceId, String dictId, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Delete dictionary {}", dictId);

        return findById(referenceType, referenceId, dictId)
                .flatMapCompletable(dictionary ->
                        repository.delete(dictId)
                                        .andThen(fromSingle(eventService.create(
                                                new Event(I18N_DICTIONARY,
                                                          new Payload(dictionary.getId(),
                                                                      dictionary.getReferenceType(),
                                                                      dictionary.getReferenceId(),
                                                                      Action.DELETE)))))
                                        .doOnComplete(() -> auditService.report(
                                                AuditBuilder
                                                        .builder(DictionaryAuditBuilder.class)
                                                        .principal(principal)
                                                        .type(EventType.I18N_DICTIONARY_DELETED)
                                                        .dictionary(dictionary)))
                                        .doOnError(throwable -> auditService.report(
                                                AuditBuilder
                                                        .builder(DictionaryAuditBuilder.class)
                                                        .principal(principal)
                                                        .type(EventType.I18N_DICTIONARY_DELETED)
                                                        .throwable(throwable)))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    String msg = "An error occurred while trying to delete a dictionary: ?";
                    LOGGER.error(msg.replace("?", "{}"), dictId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format(msg.replace("?", "%s"), dictId), ex));
                });
    }
}
