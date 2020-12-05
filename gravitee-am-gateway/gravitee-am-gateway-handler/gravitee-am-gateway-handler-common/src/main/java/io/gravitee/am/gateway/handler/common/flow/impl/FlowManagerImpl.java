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
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.am.service.FlowService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowManagerImpl extends AbstractService implements FlowManager, InitializingBean, EventListener<FlowEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(FlowManagerImpl.class);
    private static final Map<Type, List<ExtensionPoint>> extensionPoints;
    static {
        Map<Type, List<ExtensionPoint>> aMap = new HashMap<>();
        aMap.put(Type.ROOT, Arrays.asList(ExtensionPoint.ROOT));
        aMap.put(Type.CONSENT, Arrays.asList(ExtensionPoint.PRE_CONSENT, ExtensionPoint.POST_CONSENT));
        aMap.put(Type.LOGIN, Arrays.asList(ExtensionPoint.PRE_LOGIN, ExtensionPoint.POST_LOGIN));
        aMap.put(Type.REGISTER, Arrays.asList(ExtensionPoint.PRE_REGISTER, ExtensionPoint.POST_REGISTER));
        extensionPoints = Collections.unmodifiableMap(aMap);
    }

    @Autowired
    private Domain domain;

    @Autowired
    private FlowService flowService;

    @Autowired
    private PolicyPluginManager policyPluginManager;

    @Autowired
    private EventManager eventManager;

    private ConcurrentMap<String, Flow> flows = new ConcurrentHashMap<>();
    private ConcurrentMap<ExtensionPoint, List<Policy>> policies = new ConcurrentHashMap<>();

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
                    String flowId = event.content().getId();
                    if (flowId != null) {
                        updateFlow(flowId, event.type());
                    } else {
                        logger.info("Domain {} has received bulk_update flows event", domain.getName());
                        loadFlows();
                    }
                    break;
                case UNDEPLOY:
                    removeFlow(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public Single<List<Policy>> findByExtensionPoint(ExtensionPoint extensionPoint) {
        return Single.just(policies.getOrDefault(extensionPoint, Collections.emptyList()));
    }

    private void updateFlow(String flowId, FlowEvent flowEvent) {
        final String eventType = flowEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} flow event for {}", domain.getName(), eventType, flowId);
        flowService.findById(flowId)
                .subscribe(
                        flow -> {
                            flows.put(flow.getId(), flow);
                            loadFlow(flow);
                            logger.info("Flow {} has been deployed for domain {}", flowId, domain.getName());
                        },
                        error -> logger.error("Unable to deploy flow {} for domain {}", flowId, domain.getName(), error),
                        () -> logger.error("No flow found with id {}", flowId));
    }

    private void removeFlow(String flowId) {
        logger.info("Domain {} has received flow event, delete flow {}", domain.getName(), flowId);
        Flow deletedFlow = flows.remove(flowId);
        extensionPoints.get(deletedFlow.getType()).forEach(extensionPoint -> {
            policies.remove(extensionPoint);
            flows.remove(flowId);
        });
    }

    private void loadFlows() {
        flowService.findAll(ReferenceType.DOMAIN, domain.getId())
                .subscribe(
                        flows1 -> {
                            flows1.forEach(flow -> {
                                flows.put(flow.getId(), flow);
                                loadFlow(flow);
                            });
                            logger.info("Flows loaded for domain {}", domain.getName());
                        },
                        error -> logger.error("Unable to initialize flows for domain {}", domain.getName(), error)
                );
    }

    private void loadFlow(Flow flow) {
        if (!flow.isEnabled()) {
            logger.debug("Flow {} is disabled, skip process", flow.getId());
            extensionPoints.get(flow.getType()).forEach(extensionPoint -> policies.put(extensionPoint, Collections.emptyList()));
            return;
        }

        // load policies
        List<Policy> prePolicies = flow.getPre()
                .stream()
                .filter(Step::isEnabled)
                .map(this::createPolicy)
                .filter(policy -> policy != null)
                .collect(Collectors.toList());
        List<Policy> postPolicies = flow.getPost()
                .stream()
                .filter(Step::isEnabled)
                .map(this::createPolicy)
                .filter(policy -> policy != null)
                .collect(Collectors.toList());

        switch (flow.getType()) {
            case ROOT:
                // for root type, fetch only the pre step policies
                policies.put(ExtensionPoint.ROOT, prePolicies);
                break;
            case CONSENT:
                policies.put(ExtensionPoint.PRE_CONSENT, prePolicies);
                policies.put(ExtensionPoint.POST_CONSENT, postPolicies);
                break;
            case LOGIN:
                policies.put(ExtensionPoint.PRE_LOGIN, prePolicies);
                policies.put(ExtensionPoint.POST_LOGIN, postPolicies);
                break;
            case REGISTER:
                policies.put(ExtensionPoint.PRE_REGISTER, prePolicies);
                policies.put(ExtensionPoint.POST_REGISTER, postPolicies);
                break;
            default:
                throw new IllegalArgumentException("No suitable flow type found for : " + flow.getType());
        }
    }

    private Policy createPolicy(Step step) {
        try {
            logger.info("\tInitializing policy: {} [{}]", step.getName(), step.getPolicy());
            Policy policy = policyPluginManager.create(step.getPolicy(), step.getConfiguration());
            logger.info("\tPolicy : {} [{}] has been loaded", step.getName(), step.getPolicy());
            return policy;
        } catch (Exception ex) {
            return null;
        }
    }
}

