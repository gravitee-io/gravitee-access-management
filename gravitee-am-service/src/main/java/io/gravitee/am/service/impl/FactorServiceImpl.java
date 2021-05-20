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
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewFactor;
import io.gravitee.am.service.model.UpdateFactor;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.FactorAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class FactorServiceImpl implements FactorService {

    public static final String SMS_AM_FACTOR = "sms-am-factor";
    public static final String CONFIG_KEY_COUNTRY_CODES = "countryCodes";
    private static final List<String> COUNTRY_CODES = Arrays.asList(Locale.getISOCountries());
    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(FactorServiceImpl.class);

    @Lazy
    @Autowired
    private FactorRepository factorRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<Factor> findById(String id) {
        LOGGER.debug("Find factor by ID: {}", id);
        return factorRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an factor using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an factor using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<Factor> findByDomain(String domain) {
        LOGGER.debug("Find factors by domain: {}", domain);
        return factorRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find factors by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find factors by domain", ex));
                });
    }

    @Override
    public Single<Factor> create(String domain, NewFactor newFactor, User principal) {
        LOGGER.debug("Create a new factor {} for domain {}", newFactor, domain);

        Factor factor = new Factor();
        factor.setId(newFactor.getId() == null ? RandomString.generate() : newFactor.getId());
        factor.setDomain(domain);
        factor.setName(newFactor.getName());
        factor.setType(newFactor.getType());
        factor.setFactorType(newFactor.getFactorType());
        factor.setConfiguration(newFactor.getConfiguration());
        factor.setCreatedAt(new Date());
        factor.setUpdatedAt(factor.getCreatedAt());

        return checkFactorConfiguration(factor)
                .flatMap(factor1 -> factorRepository.create(factor1))
                .flatMap(factor1 -> {
                    // create event for sync process
                    Event event = new Event(Type.FACTOR, new Payload(factor1.getId(), ReferenceType.DOMAIN, factor1.getDomain(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(factor1));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a factor", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a factor", ex));
                })
                .doOnSuccess(factor1 -> auditService.report(AuditBuilder.builder(FactorAuditBuilder.class).principal(principal).type(EventType.FACTOR_CREATED).factor(factor1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(FactorAuditBuilder.class).principal(principal).type(EventType.FACTOR_CREATED).throwable(throwable)));
    }

    private Single<Factor> checkFactorConfiguration(Factor factor) {
        if (SMS_AM_FACTOR.equalsIgnoreCase(factor.getType())) {
            // for SMS Factor, check that countries code provided into the configuration are valid
            final JsonObject configuration = (JsonObject) Json.decodeValue(factor.getConfiguration());
            String countryCodes = configuration.getString(CONFIG_KEY_COUNTRY_CODES);
            for(String code : countryCodes.split(",")) {
                if (!COUNTRY_CODES.contains(code.trim().toUpperCase(Locale.ROOT))) {
                    return Single.error(new FactorConfigurationException(CONFIG_KEY_COUNTRY_CODES, code));
                }
            }
        }
        return Single.just(factor);
    }

    @Override
    public Single<Factor> update(String domain, String id, UpdateFactor updateFactor, User principal) {
        LOGGER.debug("Update an factor {} for domain {}", id, domain);

        return factorRepository.findById(id)
                .switchIfEmpty(Maybe.error(new FactorNotFoundException(id)))
                .flatMapSingle(oldFactor -> {
                    Factor factorToUpdate = new Factor(oldFactor);
                    factorToUpdate.setName(updateFactor.getName());
                    factorToUpdate.setConfiguration(updateFactor.getConfiguration());
                    factorToUpdate.setUpdatedAt(new Date());

                    return  checkFactorConfiguration(factorToUpdate)
                            .flatMap(factor1 -> factorRepository.update(factor1))
                            .flatMap(factor1 -> {
                                // create event for sync process
                                Event event = new Event(Type.FACTOR, new Payload(factor1.getId(), ReferenceType.DOMAIN, factor1.getDomain(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(factor1));
                            })
                            .doOnSuccess(factor1 -> auditService.report(AuditBuilder.builder(FactorAuditBuilder.class).principal(principal).type(EventType.FACTOR_UPDATED).oldValue(oldFactor).factor(factor1)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(FactorAuditBuilder.class).principal(principal).type(EventType.FACTOR_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a factor", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a factor", ex));
                });
    }

    @Override
    public Completable delete(String domain, String factorId, User principal) {
        LOGGER.debug("Delete factor {}", factorId);

        return factorRepository.findById(factorId)
                .switchIfEmpty(Maybe.error(new FactorNotFoundException(factorId)))
                .flatMapSingle(factor -> applicationService.findByFactor(factorId).count()
                        .flatMap(applications -> {
                            if (applications > 0) {
                                throw new FactorWithApplicationsException();
                            }
                            return Single.just(factor);
                        }))
                .flatMapCompletable(factor -> {
                    // create event for sync process
                    Event event = new Event(Type.FACTOR, new Payload(factorId, ReferenceType.DOMAIN, domain, Action.DELETE));
                    return factorRepository.delete(factorId)
                            .andThen(eventService.create(event))
                            .toCompletable()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(FactorAuditBuilder.class).principal(principal).type(EventType.FACTOR_DELETED).factor(factor)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(FactorAuditBuilder.class).principal(principal).type(EventType.FACTOR_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete factor: {}", factorId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete factor: %s", factorId), ex));
                });
    }
}
