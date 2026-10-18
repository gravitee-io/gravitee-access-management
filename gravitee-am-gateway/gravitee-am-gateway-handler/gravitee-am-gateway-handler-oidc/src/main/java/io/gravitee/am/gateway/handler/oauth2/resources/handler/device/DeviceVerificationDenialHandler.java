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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.utils.DeviceFlowContext;
import io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device.DeviceFlowOutcome;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device.DeviceFlowPageRenderer;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationSessionCleaner;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.gateway.DeviceFlowAuditBuilder;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.CustomLog;

/**
 * Terminal step of the authorization chain when the end user refuses a device flow run: there is
 * no redirect_uri to carry access_denied back on, so the device code is marked denied and the
 * completion page is rendered. The device learns of the refusal on its next poll.
 *
 * Any other failure, and any request that is not a device flow run, is left to the ordinary
 * authorization failure handling.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class DeviceVerificationDenialHandler implements Handler<RoutingContext> {

    private final DeviceAuthorizationRequestService requestService;
    private final DeviceFlowPageRenderer renderer;
    private final AuditService auditService;

    public DeviceVerificationDenialHandler(DeviceAuthorizationRequestService requestService,
                                           DeviceFlowPageRenderer renderer,
                                           AuditService auditService) {
        this.requestService = requestService;
        this.renderer = renderer;
        this.auditService = auditService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String deviceCode = DeviceFlowContext.deviceCode(context);
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (deviceCode == null || client == null || !(context.failure() instanceof AccessDeniedException)) {
            context.next();
            return;
        }

        requestService.deny(deviceCode, client.getClientId())
                .subscribe(
                        request -> {
                            reportDenied(context, client);
                            DeviceFlowContext.clear(context);
                            AuthorizationSessionCleaner.clean(context);
                            renderer.renderCompletion(context, DeviceFlowOutcome.DENIED);
                        },
                        error -> {
                            log.error("Unable to reject the device authorization request", error);
                            context.next();
                        });
    }

    private void reportDenied(RoutingContext context, Client client) {
        auditService.report(AuditBuilder.builder(DeviceFlowAuditBuilder.class)
                .type(EventType.DEVICE_FLOW_DENIED)
                .user(RoutingContextHelper.endUser(context))
                .application(client)
                .ipAddress(context)
                .userAgent(context));
    }
}
