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
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Builds the absolute, proxy-aware URI the end user is asked to open on a second device.
 *
 * @author GraviteeSource Team
 */
public class DeviceVerificationUriResolver {

    public static final String VERIFICATION_PATH = "/oauth/device";

    /**
     * The URI carries the client identifier because the verification page is a login page: it can
     * only render the application's forms, theme and identity providers once the client is known.
     */
    public String resolve(RoutingContext context, Client client) {
        return resolveComplete(context, client, null);
    }

    /**
     * The same URI with the user code embedded, for a device rendering it as a QR code. The code is
     * carried in its displayed form so that the user can compare what the page shows with what the
     * device shows before confirming it.
     */
    public String resolveComplete(RoutingContext context, Client client, String userCode) {
        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        queryParams.add(Parameters.CLIENT_ID, client.getClientId());
        if (userCode != null) {
            queryParams.add(Parameters.USER_CODE, userCode);
        }
        return UriBuilderRequest.resolveProxyRequest(context.request(),
                context.get(CONTEXT_PATH) + VERIFICATION_PATH, queryParams, true);
    }
}
