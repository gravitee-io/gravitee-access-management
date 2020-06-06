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
package io.gravitee.am.gateway.handler.common.policy.impl;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.PolicyEvent;
import io.gravitee.am.gateway.handler.common.policy.PolicyManager;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.am.service.PolicyService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyManagerImpl extends AbstractService implements PolicyManager, InitializingBean, EventListener<PolicyEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(PolicyManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private PolicyService policyService;

    @Autowired
    private PolicyPluginManager policyPluginManager;

    @Autowired
    private EventManager eventManager;

    private ConcurrentMap<String, Policy> policies = new ConcurrentHashMap<>();
    private ConcurrentMap<String, io.gravitee.am.model.Policy> policyModels = new ConcurrentHashMap<>();

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for policy events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, PolicyEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for policy events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, PolicyEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<PolicyEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    String policyId = event.content().getId();
                    if (policyId != null) {
                        updatePolicy(policyId, event.type());
                    } else {
                        logger.info("Domain {} has received bulk_update policies event", domain.getName());
                        updatePolicies();
                    }
                    break;
                case UNDEPLOY:
                    removePolicy(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public Single<List<Policy>> findByExtensionPoint(ExtensionPoint extensionPoint) {
        return Observable.fromIterable(policyModels.values())
                .filter(policy -> policy.isEnabled() && policy.getExtensionPoint().equals(extensionPoint))
                .toSortedList(Comparator.comparing(io.gravitee.am.model.Policy::getOrder))
                .map(policies1 -> policies1.stream()
                        .map(policy -> policies.get(policy.getId()))
                        .collect(Collectors.toList()));
    }

    @Override
    public Policy create(String type, String configuration) {
        return policyPluginManager.create(type, configuration);
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing policies for domain {}", domain.getName());
        updatePolicies();
    }

    private void updatePolicy(String policyId, PolicyEvent policyEvent) {
        final String eventType = policyEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} policy event for {}", domain.getName(), eventType, policyId);
        policyService.findById(policyId)
                .subscribe(
                        policy -> {
                            updatePolicyProvider(policy);
                            logger.info("Policy {} {}d for domain {}", policyId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} policy for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No policy found with id {}", policyId));
    }

    private void removePolicy(String policyId) {
        logger.info("Domain {} has received policy event, delete policy {}", domain.getName(), policyId);
        policies.remove(policyId);
        policyModels.remove(policyId);
    }

    private void updatePolicyProvider(io.gravitee.am.model.Policy policy) {
        logger.info("\tInitializing policy: {} [{}]", policy.getName(), policy.getType());
        Policy policy1 = policyPluginManager.create(policy.getType(), policy.getConfiguration());
        if (policy1 != null) {
            policies.put(policy.getId(), policy1);
            policyModels.put(policy.getId(), policy);
        }
    }

    private void updatePolicies() {
        policyService.findByDomain(domain.getId())
                .flatMapObservable(policies -> Observable.fromIterable(policies))
                .filter(io.gravitee.am.model.Policy::isEnabled)
                .toList()
                .subscribe(
                        policies1 -> {
                            policies1.forEach(policy -> updatePolicyProvider(policy));
                            logger.info("Policies loaded for domain {}", domain.getName());
                        },
                        error -> logger.error("Unable to initialize policies for domain {}", domain.getName(), error)
                );
    }
}
