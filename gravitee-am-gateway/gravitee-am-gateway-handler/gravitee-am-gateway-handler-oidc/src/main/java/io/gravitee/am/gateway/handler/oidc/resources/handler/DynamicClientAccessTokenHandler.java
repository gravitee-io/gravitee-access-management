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
package io.gravitee.am.gateway.handler.oidc.resources.handler;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oidc.exception.ClientRegistrationForbiddenException;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * The Client Configuration Endpoint is an OAuth 2.0 Protected Resource that MAY be provisioned by the server for a specific Client to be able to view and update its registered information.
 * The Client MUST use its Registration Access Token in all calls to this endpoint as an OAuth 2.0 Bearer Token unless the token has at least a DCR_ADMIN scope
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DynamicClientAccessTokenHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext context) {
        final JWT token = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        if (token.hasScope(Scope.DCR_ADMIN.getKey())) {
            context.next();
            return;
        }

        // if not dcr admin, access token must match client registration token
        final String rawToken = context.get(ConstantKeys.RAW_TOKEN_CONTEXT_KEY);
        if (rawToken == null || !rawToken.equals(client.getRegistrationAccessToken())) {
            context.fail(new ClientRegistrationForbiddenException("Non matching registration_access_token"));
            return;
        }

        // registration token sub must match the client_id parameter
        final String clientIdPathParameter = context.request().getParam(Parameters.CLIENT_ID);
        if (!isRequestPathClientIdMatching(token, clientIdPathParameter)) {
            context.fail(new ClientRegistrationForbiddenException("Not allowed to access to : " + clientIdPathParameter));
            return;
        }

        context.next();
    }

    private boolean isRequestPathClientIdMatching(JWT accessToken, String clientIdPathParameter) {
        return accessToken.getSub().equals(clientIdPathParameter);
    }
}
