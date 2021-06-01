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
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.FlowAuditBuilder;
import io.micrometer.core.instrument.util.IOUtils;
import io.reactivex.Observable;
import io.reactivex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.charset.Charset.defaultCharset;
import static java.util.Collections.emptyList;

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
    public Flowable<Flow> findAll(ReferenceType referenceType, String referenceId, boolean excludeApps) {
        LOGGER.debug("Find all flows for {} {}", referenceType, referenceId);
        return flowRepository.findAll(referenceType, referenceId)
                .filter(f -> (!excludeApps) ? true : f.getApplication() == null)
                .sorted(getFlowComparator())
                .switchIfEmpty(Flowable.fromIterable(defaultFlows(referenceType, referenceId)))
            .onErrorResumeNext(ex -> {
                LOGGER.error("An error has occurred while trying to find all flows for {} {}", referenceType, referenceId, ex);
                return Flowable.error(new TechnicalManagementException(String.format("An error has occurred while trying to find a all flows for %s %s", referenceType, referenceId), ex));
            });
    }

    @Override
    public Flowable<Flow> findByApplication(ReferenceType referenceType, String referenceId, String application) {
        LOGGER.debug("Find all flows for {} {} and application {}", referenceType, referenceId, application);
        return flowRepository.findByApplication(referenceType, referenceId, application)
                .sorted(getFlowComparator())
                .switchIfEmpty(Flowable.fromIterable(defaultFlows(referenceType, referenceId))
                        .map(flow -> {
                            flow.setApplication(application);
                            return flow;
                        }))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error has occurred while trying to find all flows for {} {} and application {}", referenceType, referenceId, application, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error has occurred while trying to find a all flows for %s %s and application %s", referenceType, referenceId, application), ex));
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

                if (!oldFlow.getType().equals(flow.getType())) {
                    throw new InvalidParameterException("Type of flow '" + flow.getName() +"' can't be updated");
                }

                Flow flowToUpdate = new Flow(oldFlow);
                flowToUpdate.setName(flow.getName());
                flowToUpdate.setEnabled(flow.isEnabled());
                flowToUpdate.setCondition(flow.getCondition());
                flowToUpdate.setPre(flow.getPre());
                flowToUpdate.setPost(flow.getPost());
                flowToUpdate.setUpdatedAt(new Date());
                if (flow.getOrder() != null) {
                    flowToUpdate.setOrder(flow.getOrder());
                }

                if (Type.ROOT.equals(flowToUpdate.getType())) {
                    // Pre or Post steps are not supposed to be null in the UI-Component
                    // force the ROOT post with emptyList to avoid UI issue
                    flowToUpdate.setPost(emptyList());
                }
                return flowRepository.update(flowToUpdate)
                    // create event for sync process
                    .flatMap(flow1 -> {
                        Event event = new Event(io.gravitee.am.common.event.Type.FLOW, new Payload(flow1.getId(), flow1.getReferenceType(), flow1.getReferenceId(), Action.UPDATE));
                        if (Type.ROOT.equals(flow1.getType())) {
                            // Pre or Post steps are not supposed to be null in the UI-Component
                            // force the ROOT post with emptyList to avoid UI issue
                            flow1.setPost(emptyList());
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

        computeFlowOrders(flows);

        return flowRepository.findAll(referenceType, referenceId)
                .toList()
                .flatMap(existingFlows -> {

                    final Map<String, Flow> mapOfExistingFlows = existingFlows.stream()
                            .filter(f -> (application == null && f.getApplication() == null) || (application != null && application.equals(f.getApplication())))
                            .filter(f -> f.getId() != null)
                            .distinct()
                            .collect(Collectors.toMap(Flow::getId, Function.identity()));

                    flows.forEach(flow -> {
                        if (flow.getId() != null && mapOfExistingFlows.containsKey(flow.getId()) && !mapOfExistingFlows.get(flow.getId()).getType().equals(flow.getType())) {
                            throw new InvalidParameterException("Type of flow '" + flow.getName() +"' can't be updated");
                        }
                    });

                    // preserve the list of flow id to identify flow that must be deleted
                    final List<String> flowIdsToDelete = new ArrayList<>(mapOfExistingFlows.keySet());

                    return Observable.fromIterable(flows)
                            .flatMapSingle(flowToCreateOrUpdate -> {
                                // remove new flow or updated flow from the flowIdsToDelete
                                if (flowToCreateOrUpdate.getId() != null) {
                                    flowIdsToDelete.remove(flowToCreateOrUpdate.getId());
                                }

                                // if no flow exists, just insert a new one
                                if (existingFlows == null || existingFlows.isEmpty()) {
                                    return create0(referenceType, referenceId, application, flowToCreateOrUpdate, principal);
                                }

                                // find existing flow
                                boolean updateRequired = flowToCreateOrUpdate.getId() != null && mapOfExistingFlows.containsKey(flowToCreateOrUpdate.getId());
                                return updateRequired ?
                                        update(referenceType, referenceId, flowToCreateOrUpdate.getId(), flowToCreateOrUpdate) :
                                        create0(referenceType, referenceId, application, flowToCreateOrUpdate, principal);
                            })
                            .sorted(getFlowComparator())
                            .toList()
                            .flatMap(persistedFlows -> Observable.fromIterable(flowIdsToDelete).flatMapCompletable(this::delete).toSingleDefault(persistedFlows));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error has occurred while trying to update flows", ex);
                    return Single.error(new TechnicalManagementException("An error has occurred while trying to update flows", ex));
                });
    }

    /**
     * Set value for the order attribute of each flow (regarding the flow type)
     * Assumption: incoming flows are in the right order
     * @param flows
     */
    private void computeFlowOrders(List<Flow> flows) {
        Map<Type, Integer> typedCounters = new HashMap<>();
        for (Flow flow : flows) {
            Integer order = typedCounters.get(flow.getType());
            if (order == null) {
                order = 0;
            } else {
                order++;
            }
            typedCounters.put(flow.getType(), order);
            flow.setOrder(order);
        }
    }

    private Single<Flow> create0(ReferenceType referenceType, String referenceId, String application, Flow flow, User principal) {
        if (flow.getOrder() == null) {
            flow.setOrder(Integer.MAX_VALUE); // if order is null put at the end
        }
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
        flow.setOrder(0);
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
            return f1.getOrder() - f2.getOrder();
        };
    }
}
