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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.model.Policy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.repository.management.api.PolicyRepository;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.model.plugin.PolicyPlugin;
import io.reactivex.Completable;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PoliciesToFlowsUpgrader implements Upgrader, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoliciesToFlowsUpgrader.class);

    @Lazy
    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private FlowService flowService;

    @Autowired
    private PolicyPluginService policyPluginService;

    @Override
    public boolean upgrade() {
        policyRepository.collectionExists()
                .flatMapCompletable(collectionExists -> {
                    if (collectionExists) {
                        LOGGER.info("Policies collection exists, upgrading policies to flows");
                        return policyRepository.findAll()
                                .groupBy(Policy::getDomain)
                                .flatMapCompletable(policiesPerDomain -> {
                                    final String domain = policiesPerDomain.getKey();
                                    return policiesPerDomain.toList().flatMapCompletable(policies -> migrateToFlows(policies, domain));
                                })
                                .andThen(policyRepository.deleteCollection());
                    } else {
                        LOGGER.info("Policies collection doesn't exist, skip upgrade");
                        return Completable.complete();
                    }
                })
                .subscribe(
                        () -> LOGGER.info("Policies to flows upgrade, done."),
                        error -> LOGGER.error("An error occurs while updating policies to flows", error)
                );

        return true;
    }

    private Completable migrateToFlows(List<Policy> policies, String domain) {
        LOGGER.info("Migrate {} policies to flows for domain {}", policies.size(), domain);

        // Only ROOT, PreConsent & PostConsent are available before 3.5
        Map<ExtensionPoint, List<Policy>> policiesPerExtPoint = policies.stream().collect(Collectors.groupingBy(Policy::getExtensionPoint));
        Map<Type, Flow> flows = flowService.defaultFlows(ReferenceType.DOMAIN, domain).stream().collect(Collectors.toMap(Flow::getType, identity()));

        for (Map.Entry<ExtensionPoint, List<Policy>> epPolicies : policiesPerExtPoint.entrySet()) {
            // be sure that the policies are in the right execution order
            epPolicies.getValue().sort((p1, p2) -> p1.getOrder() - p2.getOrder());
            switch (epPolicies.getKey()) {
                case ROOT:
                    flows.get(Type.ROOT).setPre(epPolicies.getValue().stream().map(this::createStep).collect(Collectors.toList()));
                    break;
                case PRE_CONSENT:
                    flows.get(Type.CONSENT).setPre(epPolicies.getValue().stream().map(this::createStep).collect(Collectors.toList()));
                    break;
                case POST_CONSENT:
                    flows.get(Type.CONSENT).setPost(epPolicies.getValue().stream().map(this::createStep).collect(Collectors.toList()));
                    break;
                default:
                    LOGGER.info("ExtensionPoint '{}' shouldn't be present before version 3.5, ignore it", epPolicies.getKey());
            }
        }

        return Observable.fromIterable(flows.values())
                .flatMapCompletable(flow -> flowService.create(ReferenceType.DOMAIN, domain, flow).toCompletable())
                .doOnComplete(() -> LOGGER.info("Policies migrated to flows for domain {}", domain))
                .doOnError((error) -> LOGGER.info("Error during policies migration for domain {}", domain, error));
    }

    private Step createStep(Policy policy) {
        final PolicyPlugin policyPlugin = policyPluginService.findById(policy.getType()).blockingGet();
        final Step step = new Step();
        step.setName(policyPlugin != null ? policyPlugin.getName() : policy.getType());
        step.setEnabled(policy.isEnabled());
        step.setDescription(policy.getName());
        step.setPolicy(policy.getType());
        step.setConfiguration(policy.getConfiguration());
        return step;
    }

    @Override
    public int getOrder() {
        return 11;
    }
}
