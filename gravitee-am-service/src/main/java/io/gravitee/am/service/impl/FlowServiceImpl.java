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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.repository.management.api.FlowRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.FlowNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.FlowAuditBuilder;
import io.micrometer.core.instrument.util.IOUtils;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.Charset.defaultCharset;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class FlowServiceImpl implements FlowService {

    private final Logger LOGGER = LoggerFactory.getLogger(FlowServiceImpl.class);
    private static final String DEFINITION_PATH = "/flow/am-schema.json";

    @Lazy
    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Single<List<Flow>> findAll(ReferenceType referenceType, String referenceId, boolean excludeApps) {
        LOGGER.debug("Find all flows for {} {}", referenceType, referenceId);
        return flowRepository.findAll(referenceType, referenceId)
            .map(flows -> {
                List<Flow> filteredFlows = flows
                        .stream()
                        .filter(f -> (!excludeApps) ? true : f.getApplication() == null)
                        .sorted(getFlowComparator())
                        .collect(Collectors.toList());

                if (filteredFlows.isEmpty()) {
                    return defaultFlows(referenceType, referenceId);
                }
                return filteredFlows;
            })
            .onErrorResumeNext(ex -> {
                LOGGER.error("An error has occurred while trying to find all flows for {} {}", referenceType, referenceId, ex);
                return Single.error(new TechnicalManagementException(String.format("An error has occurred while trying to find a all flows for %s %s", referenceType, referenceId), ex));
            });
    }

    @Override
    public Single<List<Flow>> findByApplication(ReferenceType referenceType, String referenceId, String application) {
        LOGGER.debug("Find all flows for {} {} and application {}", referenceType, referenceId, application);
        return flowRepository.findByApplication(referenceType, referenceId, application)
                .map(flows -> {
                    if (flows == null || flows.isEmpty()) {
                        return defaultFlows(referenceType, referenceId)
                                .stream()
                                .map(flow -> {
                                    flow.setApplication(application);
                                    return flow;
                                }).collect(Collectors.toList());
                    }
                    flows.sort(getFlowComparator());
                    return flows;
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error has occurred while trying to find all flows for {} {} and application {}", referenceType, referenceId, application, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error has occurred while trying to find a all flows for %s %s and application %s", referenceType, referenceId, application), ex));
                });
    }

    @Override
    public List<Flow> defaultFlows(ReferenceType referenceType, String referenceId) {
        return Arrays.asList(
            buildFlow(Type.ROOT, referenceType, referenceId),
            buildFlow(Type.LOGIN, referenceType, referenceId),
            buildFlow(Type.CONSENT, referenceType, referenceId),
            buildFlow(Type.REGISTER, referenceType, referenceId)
        );
    }

    @Override
    public Maybe<Flow> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("Find flow by referenceType {}, referenceId {} and id {}", referenceType, referenceId, id);
        if (id == null) {
            // flow id may be null for default flows
            return Maybe.empty();
        }
        return flowRepository.findById(referenceType, referenceId, id)
            .onErrorResumeNext(ex -> {
                LOGGER.error("An error has occurred while trying to find a flow using its referenceType {}, referenceId {} and id {}", referenceType, referenceId, id, ex);
                return Maybe.error(new TechnicalManagementException(
                    String.format("An error has occurred while trying to find a flow using its referenceType %s, referenceId %s and id %s", referenceType, referenceId, id), ex));
            });
    }

    @Override
    public Maybe<Flow> findById(String id) {
        LOGGER.debug("Find flow by id {}", id);
        if (id == null) {
            // flow id may be null for default flows
            return Maybe.empty();
        }
        return flowRepository.findById(id)
            .onErrorResumeNext(ex -> {
                LOGGER.error("An error has occurred while trying to find a flow using its id {}", id, ex);
                return Maybe.error(new TechnicalManagementException(
                    String.format("An error has occurred while trying to find a flow using its id %s", id), ex));
            });
    }

    @Override
    public Single<Flow> create(ReferenceType referenceType, String referenceId, Flow flow, User principal) {
        LOGGER.debug("Create a new flow {} for referenceType {} and referenceId", flow, referenceType, referenceId);
        return create0(referenceType, referenceId, null, flow, principal);
    }

    @Override
    public Single<Flow> create(ReferenceType referenceType, String referenceId, String application, Flow flow, User principal) {
        LOGGER.debug("Create a new flow {} for referenceType {}, referenceId {} and application {}", flow, referenceType, referenceId, application);
        return create0(referenceType, referenceId, application, flow, principal);
    }

    @Override
    public Single<Flow> update(ReferenceType referenceType, String referenceId, String id, Flow flow, User principal) {
        LOGGER.debug("Update a flow {} ", flow);

        return flowRepository.findById(referenceType, referenceId, id)
            .switchIfEmpty(Maybe.error(new FlowNotFoundException(id)))
            .flatMapSingle(oldFlow -> {
                Flow flowToUpdate = new Flow(oldFlow);
                flowToUpdate.setName(flow.getName());
                flowToUpdate.setEnabled(flow.isEnabled());
                flowToUpdate.setCondition(flow.getCondition());
                flowToUpdate.setPre(flow.getPre());
                flowToUpdate.setPost(flow.getPost());
                flowToUpdate.setUpdatedAt(new Date());
                if (Type.ROOT.equals(flowToUpdate.getType())) {
                    flowToUpdate.setPost(null);
                }
                return flowRepository.update(flowToUpdate)
                    // create event for sync process
                    .flatMap(flow1 -> {
                        Event event = new Event(io.gravitee.am.common.event.Type.FLOW, new Payload(flow1.getId(), flow1.getReferenceType(), flow1.getReferenceId(), Action.UPDATE));
                        if (Type.ROOT.equals(flow1.getType())) {
                            flow1.setPost(null);
                        }
                        return eventService.create(event).flatMap(__ -> Single.just(flow1));
                    })
                    .doOnSuccess(flow1 -> auditService.report(AuditBuilder.builder(FlowAuditBuilder.class).principal(principal).type(EventType.FLOW_UPDATED).oldValue(oldFlow).flow(flow1)))
                    .doOnError(throwable -> auditService.report(AuditBuilder.builder(FlowAuditBuilder.class).principal(principal).type(EventType.FLOW_UPDATED).throwable(throwable)));

            })
            .onErrorResumeNext(ex -> {
                if (ex instanceof AbstractManagementException) {
                    return Single.error(ex);
                }
                LOGGER.error("An error has occurred while trying to update a flow", ex);
                return Single.error(new TechnicalManagementException("An error has occurred while trying to update a flow", ex));
            });
    }

    @Override
    public Single<List<Flow>> createOrUpdate(ReferenceType referenceType, String referenceId, List<Flow> flows, User principal) {
        LOGGER.debug("Create or update flows {} for domain {}", flows, referenceId);
        return createOrUpdate0(referenceType, referenceId, null, flows, principal);
    }

    @Override
    public Single<List<Flow>> createOrUpdate(ReferenceType referenceType, String referenceId, String application, List<Flow> flows, User principal) {
        LOGGER.debug("Create or update flows {} for domain {} and application {}", flows, referenceId, application);
        return createOrUpdate0(referenceType, referenceId, application, flows, principal);
    }

    @Override
    public Completable delete(String id, User principal) {
        LOGGER.debug("Delete flow {}", id);
        if (id == null) {
            // flow id may be null for default flows
            return Completable.complete();
        }
        return flowRepository.findById(id)
                .switchIfEmpty(Maybe.error(new FlowNotFoundException(id)))
                .flatMapCompletable(flow -> {
                    // create event for sync process
                    Event event = new Event(io.gravitee.am.common.event.Type.FLOW, new Payload(flow.getId(), flow.getReferenceType(), flow.getReferenceId(), Action.DELETE));
                    return flowRepository.delete(id)
                            .andThen(eventService.create(event))
                            .ignoreElement()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(FlowAuditBuilder.class).principal(principal).type(EventType.FLOW_DELETED).flow(flow)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(FlowAuditBuilder.class).principal(principal).type(EventType.FLOW_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error has occurred while trying to delete flow: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error has occurred while trying to delete flow: %s", id), ex));
                });
    }

    @Override
    public Single<String> getSchema() {
        return Single.create(emitter -> {
            try {
                InputStream resourceAsStream = this.getClass().getResourceAsStream(DEFINITION_PATH);
                String schema = IOUtils.toString(resourceAsStream, defaultCharset());
                emitter.onSuccess(schema);
            } catch (Exception e) {
                emitter.onError(new TechnicalManagementException("An error has occurred while trying load flow schema", e));
            }
        });
    }

    private Single<List<Flow>> createOrUpdate0(ReferenceType referenceType, String referenceId, String application, List<Flow> flows, User principal) {
        return Observable.fromIterable(flows)
                .flatMapSingle(flow -> flow.getId() == null ? create0(referenceType, referenceId, application, flow, principal) : update(referenceType, referenceId, flow.getId(), flow))
                .sorted(getFlowComparator())
                .toList()
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error has occurred while trying to update flows", ex);
                    return Single.error(new TechnicalManagementException("An error has occurred while trying to update flows", ex));
                });
    }

    private Single<Flow> create0(ReferenceType referenceType, String referenceId, String application, Flow flow, User principal) {
        flow.setId(flow.getId() == null ? RandomString.generate() : flow.getId());
        flow.setReferenceType(referenceType);
        flow.setReferenceId(referenceId);
        flow.setApplication(application);
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(flow.getCreatedAt());

        return flowRepository.create(flow)
                .flatMap(flow1 -> {
                    // create event for sync process
                    Event event = new Event(io.gravitee.am.common.event.Type.FLOW, new Payload(flow1.getId(), referenceType, referenceId, Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(flow1));
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error has occurred while trying to create a flow", ex);
                    return Single.error(new TechnicalManagementException("An error has occurred while trying to create a flow", ex));
                })
                .doOnSuccess(flow1 -> auditService.report(AuditBuilder.builder(FlowAuditBuilder.class).principal(principal).type(EventType.FLOW_CREATED).flow(flow1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(FlowAuditBuilder.class).principal(principal).type(EventType.FLOW_CREATED).throwable(throwable)));
    }

    private Flow buildFlow(Type type, ReferenceType referenceType, String referenceId) {
        Flow flow = new Flow();
        if (Type.ROOT.equals(type)) {
            flow.setName("ALL");
        } else {
            flow.setName(type.name());
        }
        flow.setType(type);
        flow.setReferenceType(referenceType);
        flow.setReferenceId(referenceId);
        flow.setEnabled(true);
        return flow;
    }

    private Comparator<Flow> getFlowComparator() {
        List<Type> types = Arrays.asList(Type.values());
        return (f1, f2) -> {
            if (types.indexOf(f1.getType()) < types.indexOf(f2.getType())) {
                return -1;
            } else if (types.indexOf(f1.getType()) > types.indexOf(f2.getType())) {
                return 1;
            }
            return 0;
        };
    }
}
