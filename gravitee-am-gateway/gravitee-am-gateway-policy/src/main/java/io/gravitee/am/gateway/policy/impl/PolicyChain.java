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
package io.gravitee.am.gateway.policy.impl;

import com.google.common.base.Throwables;
import io.gravitee.am.gateway.core.processor.AbstractProcessor;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.gateway.policy.PolicyException;
import io.gravitee.am.gateway.policy.impl.processor.PolicyChainProcessorFailure;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyChain extends AbstractProcessor<ExecutionContext> implements io.gravitee.policy.api.PolicyChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyChain.class);
    private static final String GATEWAY_POLICY_INTERNAL_ERROR_KEY = "GATEWAY_POLICY_INTERNAL_ERROR";
    private final List<Policy> policies;
    private final Iterator<Policy> policyIterator;
    private final ExecutionContext executionContext;

    public PolicyChain(List<Policy> policies, ExecutionContext executionContext) {
        Objects.requireNonNull(policies, "Policies must not be null");
        Objects.requireNonNull(executionContext, "ExecutionContext must not be null");

        this.policies = policies;
        this.executionContext = executionContext;
        this.policyIterator = this.policies.iterator();
    }

    @Override
    public void doNext(Request request, Response response) {
        if (policyIterator.hasNext()) {
            Policy policy = policyIterator.next();
            try {
                if (policy.isRunnable()) {
                    // enhance execution context with policy metadata
                    if (policy.metadata() != null) {
                        policy.metadata().forEach(executionContext::setAttribute);
                    }
                    execute(
                            policy,
                            this,
                            executionContext.request(),
                            executionContext.response(),
                            executionContext);
                } else {
                    doNext(executionContext.request(), executionContext.response());
                }
            } catch (Exception ex) {
                final String message = "An error occurs in policy[" + policy.id()+"] error["+ Throwables.getStackTraceAsString(ex)+"]";
                LOGGER.error(message);
                request.metrics().setMessage(message);
                if (errorHandler != null) {
                    errorHandler.handle(new PolicyChainProcessorFailure(PolicyResult.failure(
                            GATEWAY_POLICY_INTERNAL_ERROR_KEY, ex.getMessage())));
                }
            }
        } else {
            next.handle(executionContext);
        }

    }

    @Override
    public void failWith(PolicyResult policyResult) {
        errorHandler.handle(new PolicyChainProcessorFailure(policyResult));
    }

    @Override
    public void streamFailWith(PolicyResult policyResult) {
        throw new IllegalStateException("Stream handler is not implemented by Gravitee.io AM");
    }

    @Override
    public void handle(ExecutionContext context) {
        doNext(context.request(), context.response());
    }

    private void execute(Policy policy, Object ... args) throws PolicyChainException {
        try {
            policy.execute(args);
        } catch (PolicyException pe) {
            throw new PolicyChainException(pe);
        }
    }
}
