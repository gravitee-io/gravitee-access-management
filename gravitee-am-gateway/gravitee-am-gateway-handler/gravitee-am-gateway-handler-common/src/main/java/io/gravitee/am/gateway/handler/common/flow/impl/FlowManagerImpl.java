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
package io.gravitee.am.gateway.handler.common.flow.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.FlowEvent;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.flow.ExecutionPredicate;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.common.flow.execution.ExecutionFlow;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.am.service.FlowService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static io.gravitee.am.common.policy.ExtensionPoint.*;
import static io.gravitee.am.gateway.handler.common.flow.ExecutionPredicate.alwaysTrue;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowManagerImpl extends AbstractService implements FlowManager, InitializingBean, EventListener<FlowEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(FlowManagerImpl.class);
    private static final Map<Type, List<ExtensionPoint>> extensionPoints;

    static {
        extensionPoints = Map.of(
                Type.ROOT, List.of(ExtensionPoint.ROOT),
                Type.LOGIN_IDENTIFIER, List.of(PRE_LOGIN_IDENTIFIER, POST_LOGIN_IDENTIFIER),
                Type.CONSENT, List.of(PRE_CONSENT, POST_CONSENT),
                Type.LOGIN, List.of(PRE_LOGIN, POST_LOGIN),
                Type.REGISTER, List.of(PRE_REGISTER, POST_REGISTER),
                Type.RESET_PASSWORD, List.of(PRE_RESET_PASSWORD, POST_RESET_PASSWORD),
                Type.REGISTRATION_CONFIRMATION, List.of(PRE_REGISTRATION_CONFIRMATION, POST_REGISTRATION_CONFIRMATION),
                Type.TOKEN, List.of(PRE_TOKEN, POST_TOKEN)
        );
    }

    @Autowired
    private Domain domain;

    @Autowired
    private FlowService flowService;

    @Autowired
    private PolicyPluginManager policyPluginManager;

    @Autowired
    private EventManager eventManager;

    private final ConcurrentMap<String, Flow> flows = new ConcurrentHashMap<>();
    private final ConcurrentMap<ExtensionPoint, Set<ExecutionFlow>> policies = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing flows for domain {}", domain.getName());
        loadFlows();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for flow events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, FlowEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for flow events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, FlowEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<FlowEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN &&
                domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateFlow(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeFlow(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public Single<List<Policy>> findByExtensionPoint(ExtensionPoint extensionPoint, Client client, ExecutionPredicate filter) {
        if (filter == null) {
            filter = alwaysTrue();
        }

        Set<ExecutionFlow> executionFlows = policies.get(extensionPoint);
        // if no flow, returns empty list
        if (executionFlows == null) {
            return Single.just(Collections.emptyList());
        }

        // get domain policies
        List<Policy> domainExecutionPolicies = getExecutionPolicies(executionFlows, client, true, filter);

        // if client is null, executes only security domain flows
        if (client == null) {
            return Single.just(domainExecutionPolicies);
        }

        // get application policies
        List<Policy> applicationExecutionPolicies = getExecutionPolicies(executionFlows, client, false, filter);

        // if client does not inherit domain flows, executes only application flows
        if (!client.isFlowsInherited()) {
            return Single.just(applicationExecutionPolicies);
        }

        return Single.just(
                Stream.concat(
                        domainExecutionPolicies.stream(),
                        applicationExecutionPolicies.stream()
                ).collect(toList()));
    }

    private void updateFlow(String flowId, FlowEvent flowEvent) {
        final String eventType = flowEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} flow event for {}", domain.getName(), eventType, flowId);
        flowService.findById(flowId)
                .subscribe(
                        flow -> {
                            loadFlow(flow);
                            flows.put(flow.getId(), flow);
                            logger.info("Flow {} has been deployed for domain {}", flowId, domain.getName());
                        },
                        error -> logger.error("Unable to deploy flow {} for domain {}", flowId, domain.getName(), error),
                        () -> logger.error("No flow found with id {}", flowId));
    }

    private void removeFlow(String flowId) {
        logger.info("Domain {} has received flow event, delete flow {}", domain.getName(), flowId);
        Flow deletedFlow = flows.remove(flowId);
        if (deletedFlow != null) {
            extensionPoints.get(deletedFlow.getType()).forEach(extensionPoint -> removeExecutionFlow(extensionPoint, deletedFlow.getId()));
        }
    }

    private void loadFlows() {
        flowService.findAll(ReferenceType.DOMAIN, domain.getId())
                .subscribe(
                        flow -> {
                            if (needDeployment(flow)) {
                                loadFlow(flow);
                                flows.put(flow.getId(), flow);
                                logger.info("Flow {} loaded for domain {}", flow.getType(), domain.getName());
                            }
                        },
                        error -> logger.error("Unable to initialize flows for domain {}", domain.getName(), error)
                );
    }

    private void loadFlow(Flow flow) {
        if (!flow.isEnabled()) {
            logger.debug("Flow {} is disabled, skip process", flow.getId());
            extensionPoints.get(flow.getType()).forEach(extensionPoint -> removeExecutionFlow(extensionPoint, flow.getId()));
            return;
        }

        // load policies
        var prePolicies = loadPolicies(flow.getPre());
        var postPolicies = loadPolicies(flow.getPost());

        switch (flow.getType()) {
            case ROOT:
                // for root type, fetch only the pre step policies
                addExecutionFlow(ROOT, flow, prePolicies);
                break;
            case CONSENT:
                addExecutionFlow(PRE_CONSENT, flow, prePolicies);
                addExecutionFlow(POST_CONSENT, flow, postPolicies);
                break;
            case LOGIN:
                addExecutionFlow(PRE_LOGIN, flow, prePolicies);
                addExecutionFlow(POST_LOGIN, flow, postPolicies);
                break;
            case LOGIN_IDENTIFIER:
                addExecutionFlow(PRE_LOGIN_IDENTIFIER, flow, prePolicies);
                addExecutionFlow(POST_LOGIN_IDENTIFIER, flow, postPolicies);
                break;
            case REGISTER:
                addExecutionFlow(PRE_REGISTER, flow, prePolicies);
                addExecutionFlow(POST_REGISTER, flow, postPolicies);
                break;
            case RESET_PASSWORD:
                addExecutionFlow(PRE_RESET_PASSWORD, flow, prePolicies);
                addExecutionFlow(POST_RESET_PASSWORD, flow, postPolicies);
                break;
            case REGISTRATION_CONFIRMATION:
                addExecutionFlow(PRE_REGISTRATION_CONFIRMATION, flow, prePolicies);
                addExecutionFlow(POST_REGISTRATION_CONFIRMATION, flow, postPolicies);
                break;
            case TOKEN:
                addExecutionFlow(PRE_TOKEN, flow, prePolicies);
                addExecutionFlow(POST_TOKEN, flow, postPolicies);
                break;
            case CONNECT:
                addExecutionFlow(PRE_CONNECT, flow, prePolicies);
                addExecutionFlow(POST_CONNECT, flow, postPolicies);
                break;
            default:
                throw new IllegalArgumentException("No suitable flow type found for : " + flow.getType());
        }
    }

    private List<Policy> loadPolicies(List<Step> steps) {
        return steps.stream()
                .filter(Step::isEnabled)
                .map(this::createPolicy)
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private Policy createPolicy(Step step) {
        try {
            logger.info("\tInitializing policy: {} [{}]", step.getName(), step.getPolicy());
            Policy policy = policyPluginManager.create(step.getPolicy(), step.getCondition(), step.getConfiguration());
            logger.info("\tPolicy : {} [{}] has been loaded", step.getName(), step.getPolicy());
            policy.activate();
            return policy;
        } catch (Exception ex) {
            return null;
        }
    }

    private void addExecutionFlow(ExtensionPoint extensionPoint, Flow flow, List<Policy> executionPolicies) {
        Set<ExecutionFlow> existingFlows = policies.get(extensionPoint);
        if (existingFlows == null) {
            existingFlows = new HashSet<>();
        }
        ExecutionFlow executionFlow = new ExecutionFlow(flow, executionPolicies);
        existingFlows.remove(executionFlow);
        existingFlows.add(executionFlow);
        policies.put(extensionPoint, existingFlows);
    }

    private void removeExecutionFlow(ExtensionPoint extensionPoint, String flowId) {
        Set<ExecutionFlow> existingFlows = policies.get(extensionPoint);
        if (existingFlows == null || existingFlows.isEmpty()) {
            return;
        }
        existingFlows.removeIf(executionFlow -> flowId.equals(executionFlow.getFlowId()));
    }

    private List<Policy> getExecutionPolicies(Set<ExecutionFlow> executionFlows,
                                              Client client,
                                              boolean excludeApps,
                                              ExecutionPredicate predicate) {
        return executionFlows.stream()
                .filter(executionFlow -> excludeApps(client, excludeApps, executionFlow))
                .filter(executionFlow -> predicate.evaluate(executionFlow.getCondition()))
                .map(ExecutionFlow::getPolicies)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private boolean excludeApps(Client client, boolean excludeApps, ExecutionFlow executionFlow) {
        return excludeApps ? isNull(executionFlow.getApplication()) : client.getId().equals(executionFlow.getApplication());
    }

    /**
     * @param flow
     * @return true if the Flow has never been deployed or if the deployed version is not up to date
     */
    private boolean needDeployment(Flow flow) {
        if (flow != null && flow.getId() != null) {
            final Flow deployedFlow = this.flows.get(flow.getId());
            return (deployedFlow == null || deployedFlow.getUpdatedAt().before(flow.getUpdatedAt()));
        } else {
            return false;
        }
    }
}

