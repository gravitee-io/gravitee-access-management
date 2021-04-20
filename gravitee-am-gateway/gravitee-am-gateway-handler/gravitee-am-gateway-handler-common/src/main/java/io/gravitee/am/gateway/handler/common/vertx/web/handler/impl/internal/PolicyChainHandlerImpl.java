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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.policy.PolicyManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerResponse;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.gateway.policy.PolicyChainProcessorFactory;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyChainHandlerImpl implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(PolicyChainHandlerImpl.class);
    private static final List<String> BLACKLIST_CONTEXT_ATTRIBUTES = Arrays.asList("X-XSRF-TOKEN", "_csrf", "__body-handled");
    private PolicyManager policyManager;
    private PolicyChainProcessorFactory policyChainProcessorFactory;
    private ExecutionContextFactory executionContextFactory;
    private ExtensionPoint extensionPoint;

    public PolicyChainHandlerImpl(
        PolicyManager policyManager,
        PolicyChainProcessorFactory policyChainProcessorFactory,
        ExecutionContextFactory executionContextFactory,
        ExtensionPoint extensionPoint
    ) {
        this.policyManager = policyManager;
        this.policyChainProcessorFactory = policyChainProcessorFactory;
        this.executionContextFactory = executionContextFactory;
        this.extensionPoint = extensionPoint;
    }

    @Override
    public void handle(RoutingContext context) {
        // resolve policies
        resolve(
            extensionPoint,
            handler -> {
                if (handler.failed()) {
                    logger.error("An error occurs while resolving policies", handler.cause());
                    context.fail(handler.cause());
                    return;
                }

                List<Policy> policies = handler.result();
                // if no policies continue
                if (policies.isEmpty()) {
                    context.next();
                    return;
                }

                // prepare execution context
                prepareContext(
                    context,
                    contextHandler -> {
                        if (contextHandler.failed()) {
                            logger.error("An error occurs while preparing execution context", contextHandler.cause());
                            context.fail(contextHandler.cause());
                            return;
                        }

                        // call the policy chain
                        executePolicyChain(
                            policies,
                            contextHandler.result(),
                            policyChainHandler -> {
                                if (policyChainHandler.failed()) {
                                    logger.debug("An error occurs while executing the policy chain", policyChainHandler.cause());
                                    context.fail(policyChainHandler.cause());
                                    return;
                                }
                                // update context attributes
                                ExecutionContext executionContext = policyChainHandler.result();
                                executionContext.getAttributes().forEach((k, v) -> context.put(k, v));
                                // continue
                                context.next();
                            }
                        );
                    }
                );
            }
        );
    }

    private void resolve(ExtensionPoint extensionPoint, Handler<AsyncResult<List<Policy>>> handler) {
        policyManager
            .findByExtensionPoint(extensionPoint)
            .subscribe(policies -> handler.handle(Future.succeededFuture(policies)), error -> handler.handle(Future.failedFuture(error)));
    }

    private void prepareContext(RoutingContext routingContext, Handler<AsyncResult<ExecutionContext>> handler) {
        try {
            HttpServerRequest request = routingContext.request().getDelegate();
            Request serverRequest = new VertxHttpServerRequest(request);
            Response serverResponse = new VertxHttpServerResponse(request, serverRequest.metrics());
            ExecutionContext simpleExecutionContext = new SimpleExecutionContext(serverRequest, serverResponse);
            ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);

            // add current context attributes
            Map<String, Object> contextData = new HashMap<>(routingContext.data());
            // remove technical attributes
            BLACKLIST_CONTEXT_ATTRIBUTES.forEach(attribute -> contextData.remove(attribute));
            executionContext.getAttributes().putAll(contextData);

            handler.handle(Future.succeededFuture(executionContext));
        } catch (Exception ex) {
            handler.handle(Future.failedFuture(ex));
        }
    }

    private void executePolicyChain(
        List<Policy> policies,
        ExecutionContext executionContext,
        Handler<AsyncResult<ExecutionContext>> handler
    ) {
        policyChainProcessorFactory
            .create(policies, executionContext)
            .handler(executionContext1 -> handler.handle(Future.succeededFuture(executionContext1)))
            .errorHandler(
                processorFailure ->
                    handler.handle(
                        Future.failedFuture(
                            new PolicyChainException(
                                processorFailure.message(),
                                processorFailure.statusCode(),
                                processorFailure.key(),
                                processorFailure.parameters(),
                                processorFailure.contentType()
                            )
                        )
                    )
            )
            .handle(executionContext);
    }
}
