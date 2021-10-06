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
package io.gravitee.am.gateway.handler.ciba.resources.handler;

import io.gravitee.am.common.oidc.CIBADeliveryMode;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.ciba.service.response.CibaAuthenticationResponse;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.CIBA_AUTH_REQUEST_KEY;
import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationRequestAcknowledgeHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationRequestAcknowledgeHandler.class);

    private AuthenticationRequestService authRequestService;

    private Domain domain;

    public AuthenticationRequestAcknowledgeHandler(AuthenticationRequestService authRequestService, Domain domain) {
        this.authRequestService = authRequestService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        final CibaAuthenticationRequest authRequest = context.get(CIBA_AUTH_REQUEST_KEY);
        if (authRequest != null) {
            final Client client = context.get(CLIENT_CONTEXT_KEY);

            final String authReqId = RandomString.generate();
            authRequest.setId(authReqId);

            LOGGER.debug("CIBA Authentication Request linked to auth_req_id '{}'", authRequest);

            this.authRequestService.register(authRequest, client)
                    .subscribe(req -> {
                        CibaAuthenticationResponse response = new CibaAuthenticationResponse();
                        response.setAuthReqId(req.getId());
                        response.setExpiresIn(req.getExpireAt().toInstant().minusMillis(req.getCreatedAt().getTime()).getEpochSecond());
                        if (client.getBackchannelTokenDeliveryMode()!= null && !client.getBackchannelTokenDeliveryMode().equals(CIBADeliveryMode.PUSH)) {
                            response.setInterval(domain.getOidc().getCibaSettings().getTokenReqInterval());
                        }

                        context
                                .response()
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .setStatusCode(HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(response));

                    }, error -> {
                        LOGGER.error("Unable to persist CIBA AuthenticationRequest object", error);
                        context.fail(error);
                    });

            return;
        } else {
            LOGGER.error("CIBA Authentication Request object is null");
            context.fail(new IllegalArgumentException("Missing authentication request"));
        }
    }
}
