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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.impl;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.BasicAuthHandlerImpl;

import java.util.Base64;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientBasicAuthHandlerImpl extends BasicAuthHandlerImpl {

    public ClientBasicAuthHandlerImpl(AuthProvider authProvider) {
        super(authProvider, "gravitee-io");
    }

    /**
     * Override default parseCredentials method to set proper OAuth 2.0 invalid_client error response
     *
     * invalid_client
     * Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method).
     * The authorization server MAY return an HTTP 401 (Unauthorized) status code to indicate which HTTP authentication schemes are supported.  If the
     * client attempted to authenticate via the "Authorization" request header field, the authorization server MUST respond with an HTTP 401 (Unauthorized) status code and
     * include the "WWW-Authenticate" response header field matching the authentication scheme used by the client.
     */
    @Override
    public void parseCredentials(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        parseAuthorization(context, false, parseAuthorization -> {
            if (parseAuthorization.failed()) {
                handleUnauthorizedResponse(context);
                handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: missing or unsupported authentication method", parseAuthorization.cause())));
                return;
            }

            final String suser;
            final String spass;

            try {
                // decode the payload
                String decoded = new String(Base64.getDecoder().decode(parseAuthorization.result()));

                int colonIdx = decoded.indexOf(":");
                if (colonIdx != -1) {
                    suser = decoded.substring(0, colonIdx);
                    spass = decoded.substring(colonIdx + 1);
                } else {
                    suser = decoded;
                    spass = null;
                }
            } catch (RuntimeException e) {
                handleUnauthorizedResponse(context);
                context.fail(new InvalidClientException("Invalid client: unable to parse authentication method", e));
                return;
            }

            handler.handle(Future.succeededFuture(new JsonObject().put("username", suser).put("password", spass)));
        });
    }

    private void handleUnauthorizedResponse(RoutingContext context) {
        String header = authenticateHeader(context);
        if (header != null) {
            context.response().putHeader("WWW-Authenticate", header);
        }
    }
}
