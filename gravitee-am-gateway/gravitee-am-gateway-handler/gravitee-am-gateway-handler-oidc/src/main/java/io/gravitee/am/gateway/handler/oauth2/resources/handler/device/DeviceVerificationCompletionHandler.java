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
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.gateway.DeviceFlowAuditBuilder;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.CustomLog;

/**
 * Terminal step of the authorization chain for the device flow: there is no redirect_uri to send
 * the user back to, so the device code is marked approved and a completion page is rendered.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class DeviceVerificationCompletionHandler implements Handler<RoutingContext> {

    private final DeviceAuthorizationRequestService requestService;
    private final DeviceFlowPageRenderer renderer;
    private final AuditService auditService;

    public DeviceVerificationCompletionHandler(DeviceAuthorizationRequestService requestService,
                                               DeviceFlowPageRenderer renderer,
                                               AuditService auditService) {
        this.requestService = requestService;
        this.renderer = renderer;
        this.auditService = auditService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String deviceCode = DeviceFlowContext.deviceCode(context);
        if (deviceCode == null) {
            context.next();
            return;
        }

        final io.gravitee.am.model.User endUser = RoutingContextHelper.endUser(context);
        if (endUser == null) {
            DeviceFlowContext.clear(context);
            context.fail(new AccessDeniedException());
            return;
        }

        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final AuthorizationRequest authorizationRequest = context.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);

        requestService.approve(deviceCode, client.getClientId(), endUser.getId(), authorizationRequest.getScopes())
                .doFinally(() -> DeviceFlowContext.clear(context))
                .subscribe(
                        request -> {
                            reportApproved(context, client, endUser);
                            complete(context, DeviceFlowOutcome.APPROVED);
                        },
                        error -> DeviceFlowOutcome.of(error).ifPresentOrElse(
                                outcome -> complete(context, outcome),
                                () -> {
                                    log.error("Unable to approve the device authorization request", error);
                                    context.fail(error);
                                }));
    }

    private void reportApproved(RoutingContext context, Client client, io.gravitee.am.model.User endUser) {
        auditService.report(AuditBuilder.builder(DeviceFlowAuditBuilder.class)
                .type(EventType.DEVICE_FLOW_APPROVED)
                .user(endUser)
                .application(client)
                .ipAddress(context)
                .userAgent(context));
    }

    private void complete(RoutingContext context, DeviceFlowOutcome outcome) {
        AuthorizationSessionCleaner.clean(context);
        renderer.renderCompletion(context, outcome);
    }
}
