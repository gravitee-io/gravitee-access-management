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
package io.gravitee.am.gateway.handler.common.policy;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.flow.ExecutionPredicate;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.common.http.NoOpResponse;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.gateway.policy.PolicyChainProcessorFactory;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.rxjava3.impl.AsyncResultSingle;
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
    private PolicyPluginManager policyPluginManager;

    @Autowired
    private FlowManager flowManager;

    @Autowired
    private ExecutionContextFactory executionContextFactory;

    @Override
    public Completable fire(List<Rule> rules, ExecutionContext executionContext) {
        if (rules.isEmpty()) {
            LOGGER.debug("No rules registered!");
            return Completable.complete();
        }

        return rxExecutePolicyChain(resolve(rules), executionContext)
                .ignoreElement();
    }

    @Override
    public Single<ExecutionContext> fire(ExtensionPoint extensionPoint,
                                         Request request,
                                         Client client,
                                         User user) {
        return prepareContext(request, client, user)
                .flatMap(executionContext -> {
                    return flowManager.findByExtensionPoint(extensionPoint, client, ExecutionPredicate.from(executionContext))
                            .flatMap(policies -> {
                                if (policies.isEmpty()) {
                                    LOGGER.debug("No policies registered for flow {}", extensionPoint.toString());
                                    return Single.just(executionContext);
                                }
                                return rxExecutePolicyChain(policies, executionContext);
                            });
                });
    }

    private Single<ExecutionContext> prepareContext(Request request,
                                                    Client client,
                                                    User user) {
        return Single.fromCallable(() -> {
            ExecutionContext simpleExecutionContext = new SimpleExecutionContext(request, new NoOpResponse());
            ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);
            // add current context attributes
            executionContext.getAttributes().put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // user can be null for flow such as client_credentials
            if (user != null) {
                executionContext.getAttributes().put(ConstantKeys.USER_CONTEXT_KEY, user);
            }
            return executionContext;
        });
    }

    private Single<ExecutionContext> rxExecutePolicyChain(List<Policy> policies, ExecutionContext executionContext) {
        return AsyncResultSingle.toSingle(resultHandler -> {
            executePolicyChain(policies, executionContext, new io.vertx.lang.rx.DelegatingHandler<>(resultHandler, ar -> ar.map(event -> {
                ExecutionContext enhancedContext = new SimpleExecutionContext(event.request(), event.response());
                enhancedContext.getAttributes().putAll(event.getAttributes());
                return enhancedContext;
            })));
        });
    }

    private void executePolicyChain(List<Policy> policies, ExecutionContext executionContext, Handler<AsyncResult<ExecutionContext>> handler) {
        policyChainProcessorFactory
                .create(policies, executionContext)
                .handler(executionContext1 -> handler.handle(Future.succeededFuture(executionContext1)))
                .errorHandler(processorFailure -> handler.handle(Future.failedFuture(new PolicyChainException(processorFailure.message(), processorFailure.statusCode(), processorFailure.key(), processorFailure.parameters(), processorFailure.contentType()))))
                .handle(executionContext);
    }

    private List<Policy> resolve(List<Rule> rules) {
        if (rules != null && ! rules.isEmpty()) {
            return rules.stream()
                    .filter(Rule::enabled)
                    .map(rule -> {
                        Policy policy = policyPluginManager.create(rule.type(), rule.condition());
                        policy.setMetadata(rule.metadata());

                        return policy;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
