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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.device;

import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device.DeviceFlowPageRenderer;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationChainExtension;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.model.Domain;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

public class DeviceVerificationChainExtension implements AuthorizationChainExtension {

    private final Domain domain;
    private final DeviceVerificationCompletionHandler completionHandler;
    private final DeviceVerificationDenialHandler denialHandler;

    public DeviceVerificationChainExtension(Domain domain,
                                            DeviceAuthorizationRequestService requestService,
                                            DeviceFlowPageRenderer renderer) {
        this.domain = domain;
        this.completionHandler = new DeviceVerificationCompletionHandler(requestService, renderer);
        this.denialHandler = new DeviceVerificationDenialHandler(requestService, renderer);
    }

    @Override
    public Handler<RoutingContext> completionHandler() {
        return domain.useDeviceFlow() ? completionHandler : RoutingContext::next;
    }

    @Override
    public Handler<RoutingContext> failureHandler() {
        return domain.useDeviceFlow() ? denialHandler : RoutingContext::next;
    }
}
