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

import io.gravitee.am.authdevice.notifier.api.model.ADCallbackContext;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationRequestCallbackHandler implements Handler<RoutingContext> {
    private static Logger LOGGER = LoggerFactory.getLogger(AuthenticationRequestCallbackHandler.class);

    private final AuthenticationRequestService authRequestService;

    public AuthenticationRequestCallbackHandler(AuthenticationRequestService authRequestService) {
        this.authRequestService = authRequestService;
    }

    @Override
    public void handle(RoutingContext context) {
        final ADCallbackContext adCallbackContext = new ADCallbackContext(context.request().headers(), context.request().params());
        authRequestService.validateUserResponse(adCallbackContext)
                .doOnComplete(() -> context.response().setStatusCode(HttpStatusCode.OK_200).end())
                .doOnError(error -> {
                    LOGGER.warn("Authentication Request validation can't be processed", error);
                    if (error instanceof OAuth2Exception) {
                        context.fail(HttpStatusCode.BAD_REQUEST_400, error);
                    } else {
                        context.fail(HttpStatusCode.INTERNAL_SERVER_ERROR_500);
                    }
                }).subscribe();
    }
}
