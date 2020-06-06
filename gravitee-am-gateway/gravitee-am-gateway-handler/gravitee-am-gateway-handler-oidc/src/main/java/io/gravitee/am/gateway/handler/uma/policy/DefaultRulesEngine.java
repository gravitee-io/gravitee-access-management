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
package io.gravitee.am.gateway.handler.uma.policy;

import io.gravitee.am.gateway.handler.common.policy.PolicyManager;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.gateway.policy.PolicyChainProcessorFactory;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultRulesEngine implements RulesEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRulesEngine.class);

    @Autowired
    private PolicyChainProcessorFactory policyChainProcessorFactory;

    @Autowired
    private PolicyManager policyManager;

    @Override
    public Completable fire(List<Rule> rules, ExecutionContext executionContext) {
        if (rules.isEmpty()) {
            LOGGER.debug("No rules registered!");
            return Completable.complete();
        }

        return Completable.create(emitter -> {
            policyChainProcessorFactory
                    .create(resolve(rules), executionContext)
                    .handler(executionContext1 -> emitter.onComplete())
                    .errorHandler(processorFailure -> emitter.onError(new PolicyChainException(processorFailure.message(), processorFailure.statusCode(), processorFailure.key(), processorFailure.parameters(), processorFailure.contentType())))
                    .handle(executionContext);
        });
    }

    protected List<Policy> resolve(List<Rule> rules) {
        if (rules != null && ! rules.isEmpty()) {
            return rules.stream()
                    .filter(rule -> rule.enabled())
                    .map(rule -> {
                        Policy policy = policyManager.create(rule.type(), rule.condition());
                        policy.setMetadata(rule.metadata());
                        return policy;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
