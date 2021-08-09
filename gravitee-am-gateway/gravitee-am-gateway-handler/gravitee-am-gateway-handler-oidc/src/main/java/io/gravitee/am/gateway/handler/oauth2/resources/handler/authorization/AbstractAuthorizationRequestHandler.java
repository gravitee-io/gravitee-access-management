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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedResponseTypeException;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractAuthorizationRequestHandler {

    protected boolean isJwtAuthRequest(RoutingContext context) {
        return !StringUtils.isEmpty(context.request().getParam(io.gravitee.am.common.oidc.Parameters.REQUEST_URI)) ||
                !StringUtils.isEmpty(context.request().getParam(io.gravitee.am.common.oidc.Parameters.REQUEST));
    }

    protected void checkResponseType(String responseType, OpenIDProviderMetadata openIDProviderMetadata) {
        if (responseType == null) {
            throw new InvalidRequestException("Missing parameter: " + Parameters.RESPONSE_TYPE);
        }

        // get supported response types
        List<String> responseTypesSupported = openIDProviderMetadata.getResponseTypesSupported();
        if (!responseTypesSupported.contains(responseType)) {
            throw new UnsupportedResponseTypeException("Unsupported response type: " + responseType);
        }
    }
}
