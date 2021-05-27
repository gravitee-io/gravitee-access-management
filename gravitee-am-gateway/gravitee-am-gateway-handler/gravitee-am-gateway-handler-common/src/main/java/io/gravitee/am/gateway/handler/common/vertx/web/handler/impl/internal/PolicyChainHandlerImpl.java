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
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.common.flow.FlowPredicate;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerResponse;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.gateway.policy.PolicyChainProcessorFactory;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.utils.RoutingContextHelper.getEvaluableAttributes;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyChainHandlerImpl implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(PolicyChainHandlerImpl.class);
    private static final List<String> BLACKLIST_CONTEXT_ATTRIBUTES = Arrays.asList("X-XSRF-TOKEN", "_csrf", "__body-handled");
    private FlowManager flowManager;
    private PolicyChainProcessorFactory policyChainProcessorFactory;
    private ExecutionContextFactory executionContextFactory;
    private ExtensionPoint extensionPoint;

    public PolicyChainHandlerImpl(FlowManager flowManager,
                                  PolicyChainProcessorFactory policyChainProcessorFactory,
                                  ExecutionContextFactory executionContextFactory,
                                  ExtensionPoint extensionPoint) {
        this.flowManager = flowManager;
        this.policyChainProcessorFactory = policyChainProcessorFactory;
        this.executionContextFactory = executionContextFactory;
        this.extensionPoint = extensionPoint;
    }

    @Override
    public void handle(RoutingContext context) {
        // do not call the policy chain if there is error, success or warning parameters
        // it means that the policy chain has been already executed
        final HttpServerRequest request = context.request();
        if (request.params() != null &&
                (request.params().contains(ConstantKeys.ERROR_PARAM_KEY) ||
                        request.params().contains(ConstantKeys.WARNING_PARAM_KEY) ||
                        request.params().contains(ConstantKeys.SUCCESS_PARAM_KEY))) {
            context.next();
            return;
        }

        // prepare execution context
        prepareContext(context, contextHandler -> {

            if (contextHandler.failed()) {
                logger.error("An error occurs while preparing execution context", contextHandler.cause());
                context.fail(contextHandler.cause());
                return;
            }

            // resolve policies
            ExecutionContext executionContext = contextHandler.result();
            resolve(executionContext, handler -> {
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

                // call the policy chain
                executePolicyChain(policies, executionContext, policyChainHandler -> {
                    if (policyChainHandler.failed()) {
                        logger.debug("An error occurs while executing the policy chain", policyChainHandler.cause());
                        context.fail(policyChainHandler.cause());
                        return;
                    }
                    // update context attributes
                    ExecutionContext processedExecutionContext = policyChainHandler.result();
                    processedExecutionContext.getAttributes().forEach((k, v) -> {
                        if (ConstantKeys.AUTH_FLOW_CONTEXT_KEY.equals(k)) {
                            final AuthenticationFlowContext authFlowContext = (AuthenticationFlowContext) v;
                            if (authFlowContext != null) {
                                // update authentication flow context version into the session
                                context.session().put(ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY, authFlowContext.getVersion());
                            }
                        }
                        context.put(k, v);
                    });
                    // continue
                    context.next();
                });
            });
        });
    }

    private void resolve(ExecutionContext executionContext, Handler<AsyncResult<List<Policy>>> handler) {
        flowManager.findByExtensionPoint(extensionPoint, (Client)executionContext.getAttribute(ConstantKeys.CLIENT_CONTEXT_KEY), FlowPredicate.from(executionContext))
                .subscribe(
                        policies -> handler.handle(Future.succeededFuture(policies)),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private void prepareContext(RoutingContext routingContext, Handler<AsyncResult<ExecutionContext>> handler) {
        try {
            io.vertx.core.http.HttpServerRequest request = routingContext.request().getDelegate();
            Request serverRequest = new VertxHttpServerRequest(request);
            Response serverResponse = new VertxHttpServerResponse(request, serverRequest.metrics());
            ExecutionContext simpleExecutionContext = new SimpleExecutionContext(serverRequest, serverResponse);
            ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);
            // add current context attributes
            executionContext.getAttributes().putAll(getEvaluableAttributes(routingContext));

            handler.handle(Future.succeededFuture(executionContext));
        } catch (Exception ex) {
            handler.handle(Future.failedFuture(ex));
        }
    }

    private void executePolicyChain(List<Policy> policies, ExecutionContext executionContext, Handler<AsyncResult<ExecutionContext>> handler) {
        policyChainProcessorFactory
                .create(policies, executionContext)
                .handler(executionContext1 -> handler.handle(Future.succeededFuture(executionContext1)))
                .errorHandler(processorFailure -> handler.handle(Future.failedFuture(new PolicyChainException(processorFailure.message(), processorFailure.statusCode(), processorFailure.key(), processorFailure.parameters(), processorFailure.contentType()))))
                .handle(executionContext);
    }
}
