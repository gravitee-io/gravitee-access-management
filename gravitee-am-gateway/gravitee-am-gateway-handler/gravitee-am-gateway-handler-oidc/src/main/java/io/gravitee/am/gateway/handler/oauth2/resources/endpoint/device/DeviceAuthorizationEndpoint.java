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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.service.device.UserCodeGenerator;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.CustomLog;

import java.util.Set;

import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.splitScopes;

/**
 * Device authorization endpoint of RFC 8628 section 3.1: a device asks for a device code and a
 * user code, and is told where to send its user.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class DeviceAuthorizationEndpoint implements Handler<RoutingContext> {

    private final DeviceAuthorizationRequestService requestService;
    private final DeviceVerificationUriResolver verificationUriResolver;

    public DeviceAuthorizationEndpoint(DeviceAuthorizationRequestService requestService,
                                       DeviceVerificationUriResolver verificationUriResolver) {
        this.requestService = requestService;
        this.verificationUriResolver = verificationUriResolver;
    }

    @Override
    public void handle(RoutingContext context) {
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (!client.hasGrantType(GrantType.DEVICE_CODE)) {
            context.fail(new UnauthorizedClientException("Client should have " + GrantType.DEVICE_CODE + " grant type"));
            return;
        }

        final Set<String> scopes = splitScopes(context.request().getParam(Parameters.SCOPE));

        requestService.register(client, scopes)
                .subscribe(
                        request -> {
                            final String userCode = UserCodeGenerator.format(request.getUserCode());
                            final DeviceAuthorizationResponse response = new DeviceAuthorizationResponse(
                                    request.getId(),
                                    userCode,
                                    verificationUriResolver.resolve(context, client),
                                    verificationUriResolver.resolveComplete(context, client, userCode),
                                    requestService.getDeviceCodeExpiryInSec(client),
                                    requestService.getPollingIntervalInSec(client));
                            context.response()
                                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                    .setStatusCode(HttpStatusCode.OK_200)
                                    .end(Json.encodePrettily(response));
                        },
                        error -> {
                            log.error("Unable to register the device authorization request", error);
                            context.fail(error);
                        });
    }
}
