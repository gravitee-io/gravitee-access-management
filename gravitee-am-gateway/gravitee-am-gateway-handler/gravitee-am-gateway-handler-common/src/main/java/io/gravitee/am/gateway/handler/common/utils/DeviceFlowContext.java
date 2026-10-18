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
package io.gravitee.am.gateway.handler.common.utils;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The device-flow marker the verification page leaves in the session, and which tells the shared
 * authorization chain that this run has no redirect_uri and no response_type.
 *
 * The marker is only honoured for a request that looks like a device flow run: same client, and
 * neither of the two parameters the device flow does not have. An abandoned run therefore cannot
 * relax the validation of an ordinary authorization request made later in the same session.
 *
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeviceFlowContext {

    public static boolean isDeviceFlow(RoutingContext context) {
        return deviceCode(context) != null;
    }

    public static String deviceCode(RoutingContext context) {
        if (context.session() == null) {
            return null;
        }
        final String clientId = context.session().get(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY);
        if (clientId == null || !clientId.equals(context.request().getParam(Parameters.CLIENT_ID))) {
            return null;
        }
        if (context.request().getParam(Parameters.RESPONSE_TYPE) != null
                || context.request().getParam(Parameters.REDIRECT_URI) != null) {
            return null;
        }
        return context.session().get(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY);
    }

    public static void markDeviceFlow(RoutingContext context, String deviceCode, String clientId) {
        context.session().put(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY, deviceCode);
        context.session().put(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY, clientId);
    }

    public static void clear(RoutingContext context) {
        if (context.session() != null) {
            context.session().remove(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY);
            context.session().remove(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY);
        }
    }
}
