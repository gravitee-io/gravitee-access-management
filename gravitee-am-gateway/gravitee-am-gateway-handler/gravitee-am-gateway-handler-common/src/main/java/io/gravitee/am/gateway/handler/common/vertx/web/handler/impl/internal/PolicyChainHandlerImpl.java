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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.flow.ExecutionPredicate;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
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
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.ALTERNATIVE_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_FORCE_ENROLLMENT;
import static io.gravitee.am.common.utils.ConstantKeys.POLICY_CHAIN_ERROR_KEY_MFA_CHALLENGE_ERROR;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextUtils.getEvaluableAttributes;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyChainHandlerImpl implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(PolicyChainHandlerImpl.class);
    private final FlowManager flowManager;
    private final PolicyChainProcessorFactory policyChainProcessorFactory;
    private final ExecutionContextFactory executionContextFactory;
    private final ExtensionPoint extensionPoint;
    private final boolean skipAllFlowsOnError;

    public PolicyChainHandlerImpl(FlowManager flowManager,
                                  PolicyChainProcessorFactory policyChainProcessorFactory,
                                  ExecutionContextFactory executionContextFactory,
                                  ExtensionPoint extensionPoint, boolean skipAllFlowsOnError) {
        this.flowManager = flowManager;
        this.policyChainProcessorFactory = policyChainProcessorFactory;
        this.executionContextFactory = executionContextFactory;
        this.extensionPoint = extensionPoint;
        this.skipAllFlowsOnError = skipAllFlowsOnError;
    }

    @Override
    public void handle(RoutingContext context) {
        // do not call the policy chain if there is error, success or warning parameters
        // it means that the policy chain has been already executed
        final HttpServerRequest request = context.request();
        if (canSkipExecution(request)) {
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
                        Throwable failureCause = policyChainHandler.cause();
                        logger.debug("An error occurs while executing the policy chain", failureCause);

                        if (failureCause instanceof PolicyChainException policyChainException
                                && POLICY_CHAIN_ERROR_KEY_MFA_CHALLENGE_ERROR.equals(((PolicyChainException) failureCause).key())) {
                            // need to set into the session the alternativeFactorId
                            if (policyChainException.parameters() != null) {
                                context.session().put(ALTERNATIVE_FACTOR_ID_KEY, policyChainException.parameters().get(ALTERNATIVE_FACTOR_ID_KEY));
                                if (policyChainException.parameters().containsKey(MFA_FORCE_ENROLLMENT)) {
                                    context.session().put(MFA_FORCE_ENROLLMENT, policyChainException.parameters().get(MFA_FORCE_ENROLLMENT));
                                }
                            }
                        }

                        context.fail(failureCause);
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

    private boolean canSkipExecution(HttpServerRequest request) {
        return isAllowedToSkip(request) && isSkipConditionMet(request);
    }

    private boolean isSkipConditionMet(HttpServerRequest request) {
        return request.params() != null &&
                (request.params().contains(ConstantKeys.ERROR_PARAM_KEY) ||
                        request.params().contains(ConstantKeys.WARNING_PARAM_KEY) ||
                        request.params().contains(ConstantKeys.SUCCESS_PARAM_KEY));
    }

    private boolean isAllowedToSkip(HttpServerRequest request) {
        return skipAllFlowsOnError || request.method() == HttpMethod.GET;
    }

    private void resolve(ExecutionContext executionContext, Handler<AsyncResult<List<Policy>>> handler) {
        flowManager.findByExtensionPoint(extensionPoint, (Client)executionContext.getAttribute(ConstantKeys.CLIENT_CONTEXT_KEY), ExecutionPredicate.from(executionContext))
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
