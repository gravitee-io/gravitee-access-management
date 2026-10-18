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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.service.device.UserCodeGenerator;
import io.gravitee.am.model.Template;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Optional;

/**
 * Renders the page where the end user types the code displayed on their device.
 *
 * A code carried by a verification_uri_complete pre-fills the field, in plain sight and still
 * editable: the user is confirming a code they can read, which is what makes the QR path safe. A
 * code this domain could not have issued is shown back all the same rather than dropped, so that a
 * tampered URI ends on the invalid outcome the user can act on.
 *
 * @author GraviteeSource Team
 */
public class DeviceVerificationEndpoint implements Handler<RoutingContext> {

    private static final int MAX_PREFILLED_CODE_LENGTH = 64;

    private final DeviceFlowPageRenderer renderer;

    public DeviceVerificationEndpoint(DeviceFlowPageRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void handle(RoutingContext context) {
        if (context.session() != null) {
            context.session().remove(ConstantKeys.RETURN_URL_KEY);
            final String error = context.session().remove(ConstantKeys.DEVICE_FLOW_ERROR_KEY);
            if (error != null) {
                context.put(ConstantKeys.ERROR_PARAM_KEY, error);
            }
        }
        prefilledCode(context.request().getParam(Parameters.USER_CODE))
                .ifPresent(userCode -> context.put(Parameters.USER_CODE, userCode));
        context.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(context.request(), context.request().uri()));
        renderer.render(context, Template.DEVICE_CODE_ENTRY);
    }

    private Optional<String> prefilledCode(String userCode) {
        if (userCode == null || userCode.isBlank()) {
            return Optional.empty();
        }
        final String normalized = UserCodeGenerator.normalize(userCode);
        if (normalized.length() == UserCodeGenerator.LENGTH) {
            return Optional.of(UserCodeGenerator.format(normalized));
        }
        return Optional.of(userCode.substring(0, Math.min(userCode.length(), MAX_PREFILLED_CODE_LENGTH)));
    }
}
