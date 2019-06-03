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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.policy.PolicyManager;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.PolicyChainHandler;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.policy.PolicyChainProcessorFactory;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyChainHandlerImpl implements PolicyChainHandler {

    @Autowired
    private PolicyManager policyManager;

    @Autowired
    private PolicyChainProcessorFactory policyChainProcessorFactory;

    @Autowired
    private ExecutionContextFactory executionContextFactory;

    @Override
    public Handler<RoutingContext> create(ExtensionPoint extensionPoint) {
        Objects.requireNonNull(extensionPoint, "An extension point is required");
        return new io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.PolicyChainHandlerImpl(policyManager, policyChainProcessorFactory, executionContextFactory, extensionPoint);
    }
}
